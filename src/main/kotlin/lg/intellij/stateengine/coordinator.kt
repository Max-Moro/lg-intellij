package lg.intellij.stateengine

import kotlinx.coroutines.*

/**
 * State Engine - State Coordinator
 *
 * Orchestrates command processing through business rules.
 * Generic over state type for reusability.
 */

// ============================================
// Logger Interface
// ============================================

/**
 * Logger interface for debugging coordinator operations.
 */
interface CoordinatorLogger {
    fun debug(msg: String)
    fun error(msg: String, error: Throwable? = null)
}

/**
 * No-op logger for production use.
 * All logging calls are ignored.
 */
val nullLogger: CoordinatorLogger = object : CoordinatorLogger {
    override fun debug(msg: String) {}
    override fun error(msg: String, error: Throwable?) {}
}

// ============================================
// State Coordinator
// ============================================

/**
 * State Coordinator - orchestrates command processing.
 *
 * Responsibilities:
 * - Process commands through business rules
 * - Manage async operations
 * - Track state stability
 * - Emit state changes when stable
 *
 * The coordinator does NOT store business state - that's the store's job.
 * It only tracks coordination state (pendingOps, pending jobs).
 *
 * @param TState Application state type
 * @param TResult Rule result type (must extend RuleResult)
 * @param store State store for mutations and emissions
 * @param logger Optional logger for debugging (defaults to nullLogger)
 */
class StateCoordinator<TState, TResult : RuleResult>(
    private val store: StateStore<TState, TResult>,
    private val logger: CoordinatorLogger = nullLogger
) {
    private val rules = mutableListOf<BusinessRule<TState, TResult>>()
    private val metaListeners = mutableSetOf<MetaListener>()

    // Coordination state (NOT business state!)
    private var pendingOps = 0
    private val pendingJobs = mutableListOf<Job>()

    // CoroutineScope for async operations
    // SupervisorJob ensures one failing child doesn't cancel siblings
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Set business rules for this coordinator.
     *
     * Replaces any existing rules.
     *
     * @param rules List of business rules to register
     */
    fun setRules(rules: List<BusinessRule<TState, TResult>>) {
        this.rules.clear()
        this.rules.addAll(rules)
        logger.debug("Registered ${rules.size} business rules")
    }

    /**
     * Check if state is stable (no pending async operations).
     *
     * UI components can use this to show/hide loading indicators.
     *
     * @return true if no async operations are in progress
     */
    fun isStable(): Boolean = pendingOps == 0

    /**
     * Process a command through the rules engine.
     *
     * Command processing flow:
     * 1. Find applicable rules (matching trigger and condition)
     * 2. Apply rules and collect results
     * 3. Apply mutations via store
     * 4. Start async operations
     * 5. Process follow-up commands
     * 6. Emit state when stable
     *
     * @param command Command to process
     */
    suspend fun dispatch(command: BaseCommand) {
        logger.debug("Dispatching command: ${command.type}")

        val state = store.getBusinessState()

        // Find applicable rules
        val applicableRules = rules.filter { rule ->
            rule.trigger == command.type &&
                    rule.condition(state, command)
        }

        if (applicableRules.isEmpty()) {
            logger.debug("No rules matched for ${command.type}")
            return
        }

        logger.debug("${applicableRules.size} rules matched for ${command.type}")

        // Apply rules and collect results
        val allAsyncOps = mutableListOf<AsyncOperation>()
        val allFollowUps = mutableListOf<BaseCommand>()

        for (rule in applicableRules) {
            try {
                val result = rule.apply(state, command)

                // Apply mutations via store (store handles empty results gracefully)
                store.applyMutations(result)

                // Collect async ops
                result.asyncOps?.let { allAsyncOps.addAll(it) }

                // Collect follow-ups
                result.followUp?.let { allFollowUps.addAll(it) }

            } catch (e: Exception) {
                logger.error("Rule for ${rule.trigger} failed", e)
            }
        }

        // Start async operations
        for (op in allAsyncOps) {
            startAsyncOp(op)
        }

        // Update stability and emit if stable
        checkAndEmit()

        // Process follow-up commands sequentially
        for (followUp in allFollowUps) {
            dispatch(followUp)
        }
    }

    /**
     * Start an async operation.
     *
     * Increments pendingOps counter, executes the operation in background,
     * and dispatches the result command when complete.
     *
     * @param op Async operation to execute
     */
    private fun startAsyncOp(op: AsyncOperation) {
        pendingOps++
        emitMeta()

        val job = scope.launch {
            try {
                val resultCommand = op.execute()
                pendingOps--
                dispatch(resultCommand)
            } catch (e: CancellationException) {
                // Must re-throw CancellationException for proper coroutine cancellation
                pendingOps--
                throw e
            } catch (e: Exception) {
                pendingOps--
                logger.error("Async op failed", e)
                checkAndEmit()
            }
        }

        pendingJobs.add(job)
    }

    /**
     * Check stability and emit state if stable.
     */
    private fun checkAndEmit() {
        if (isStable()) {
            logger.debug("State is stable, emitting")
            store.emit()
        }
        emitMeta()
    }

    /**
     * Emit UI meta state to listeners.
     *
     * Called whenever stability changes.
     */
    private fun emitMeta() {
        val meta = UIMeta(isLoading = !isStable())

        for (listener in metaListeners) {
            try {
                listener(meta)
            } catch (e: Exception) {
                logger.debug("Meta listener error: $e")
            }
        }
    }

    /**
     * Subscribe to UI meta changes (loading state).
     *
     * Meta state contains isLoading flag which UI can use
     * to show/hide loading indicators.
     *
     * @param listener Callback for meta changes
     * @return Unsubscribe function
     */
    fun subscribeToMeta(listener: MetaListener): () -> Unit {
        metaListeners.add(listener)
        return { metaListeners.remove(listener) }
    }

    /**
     * Wait for all pending operations to complete.
     *
     * Useful for testing or when you need to ensure
     * all async operations have finished.
     */
    suspend fun waitForStability() {
        while (pendingJobs.isNotEmpty()) {
            val jobs = pendingJobs.toList()
            pendingJobs.clear()
            jobs.joinAll()
        }
    }

    /**
     * Cancel all pending operations and cleanup.
     *
     * Should be called when the coordinator is no longer needed.
     */
    fun dispose() {
        scope.cancel()
        pendingJobs.clear()
        metaListeners.clear()
    }
}
