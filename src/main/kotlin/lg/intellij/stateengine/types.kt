package lg.intellij.stateengine

/**
 * State Engine - Universal Types
 *
 * Generic types for command-driven state management with async operations.
 * Framework-agnostic, can be reused in other projects.
 *
 * Ported from VS Code Extension's state-engine/types.ts
 */

// ============================================
// Base Command Interface
// ============================================

/**
 * Base interface for all commands.
 * Commands are the only way to modify state in the system.
 *
 * Convention: command types use namespace format "domain/ACTION"
 * Examples: "context/SELECT", "provider/SELECT", "adaptive/TOGGLE_TAG"
 */
interface BaseCommand {
    val type: String
}

// ============================================
// Rule System Types
// ============================================

/**
 * Async operation that produces a command when complete.
 *
 * Used for deferred side effects like:
 * - CLI calls
 * - Network requests
 * - File system operations
 */
interface AsyncOperation {
    /**
     * Execute the async operation and return a result command.
     */
    suspend fun execute(): BaseCommand
}

/**
 * Base result of applying a business rule.
 * Contains only coordination fields - mutations are application-specific.
 *
 * Applications extend this interface to add their own mutation types.
 *
 * Note: Does NOT contain mutations field - that's implementation detail of Store.
 */
interface RuleResult {
    /**
     * Async operations to execute after rule application.
     * Results will be dispatched as new commands.
     */
    val asyncOps: List<AsyncOperation>?

    /**
     * Follow-up commands to dispatch after mutations are applied.
     * Synchronous, processed before async ops.
     */
    val followUp: List<BaseCommand>?
}

/**
 * Business rule definition.
 * Generic over state type and result type for flexibility.
 *
 * Rules are pure functions that:
 * 1. Check if they should apply (condition)
 * 2. Compute mutations and side effects (apply)
 *
 * @param TState Application state type
 * @param TResult Rule result type (must extend RuleResult)
 */
interface BusinessRule<TState, TResult : RuleResult> {
    /**
     * Command type that triggers this rule.
     * Supports wildcards: "*" matches all commands.
     */
    val trigger: String

    /**
     * Condition to check before applying rule.
     * Return true if rule should be applied.
     *
     * @param state Current application state
     * @param cmd Command that triggered the rule
     */
    fun condition(state: TState, cmd: BaseCommand): Boolean

    /**
     * Apply rule and return mutations/effects.
     *
     * This function must be pure - no side effects.
     * Side effects are represented as AsyncOperation instances.
     *
     * @param state Current application state
     * @param cmd Command that triggered the rule
     * @return Rule result with mutations and/or side effects
     */
    fun apply(state: TState, cmd: BaseCommand): TResult
}

// ============================================
// Store Types
// ============================================

/**
 * Listener for state changes.
 */
typealias StateListener<TState> = (state: TState) -> Unit

/**
 * Store interface for state management.
 *
 * Only responsible for storing and updating business state.
 * Coordination (stability, pending ops) is handled by StateCoordinator.
 *
 * Note: Method is named getBusinessState() to avoid conflict with
 * IntelliJ Platform's SimplePersistentStateComponent.getState().
 *
 * @param TState Application state type
 * @param TResult Rule result type (for applyMutations)
 */
interface StateStore<TState, TResult : RuleResult> {
    /**
     * Get current business state snapshot.
     *
     * Named getBusinessState() to avoid conflict with platform persistence APIs.
     */
    fun getBusinessState(): TState

    /**
     * Apply mutations from rule result.
     * Implementation handles specific mutation types.
     *
     * @param result Rule result containing mutations
     */
    suspend fun applyMutations(result: TResult)

    /**
     * Emit state change to subscribers.
     * Call after mutations are applied.
     */
    fun emit()

    /**
     * Subscribe to state changes.
     *
     * @param listener Callback invoked on state changes
     * @return Unsubscribe function
     */
    fun subscribe(listener: StateListener<TState>): () -> Unit
}

// ============================================
// Coordinator Types
// ============================================

/**
 * UI meta state for loading indicators.
 *
 * Separate from business state to avoid coupling
 * loading UI with business logic.
 */
data class UIMeta(
    /**
     * True when async operations are in progress.
     */
    val isLoading: Boolean
)

/**
 * Listener for UI meta changes.
 */
typealias MetaListener = (meta: UIMeta) -> Unit

// ============================================
// Command Definition Types
// ============================================

/**
 * Command definition with typed payload.
 *
 * Provides type-safe command creation:
 * - [type] - unique command identifier
 * - [create] - factory method for creating commands with payload
 *
 * @param TPayload Payload type
 */
interface CommandDef<TPayload> {
    val type: String
    fun create(payload: TPayload): BaseCommand
}

/**
 * Command definition without payload.
 */
interface CommandDefNoPayload {
    val type: String
    fun create(): BaseCommand
}
