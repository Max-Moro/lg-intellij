/**
 * State Engine — Universal State Coordination Framework
 *
 * A generic, framework-agnostic state management system based on:
 * - Command-driven architecture (all changes via commands)
 * - Business rules (declarative state transitions)
 * - Async operations (side effects as first-class citizens)
 * - Stability tracking (emit only when stable)
 *
 * ## Architecture Overview
 *
 * ```
 * ┌──────────────────────────────────────────────────────────┐
 * │                    StateCoordinator                       │
 * │  ┌─────────────┐   ┌─────────────┐   ┌─────────────────┐ │
 * │  │  dispatch() │ → │   Rules     │ → │  applyMutations │ │
 * │  └─────────────┘   └─────────────┘   └─────────────────┘ │
 * │         ↑                                      ↓         │
 * │  ┌─────────────┐                    ┌─────────────────┐ │
 * │  │ AsyncOps    │ ←──────────────────│    emit()       │ │
 * │  └─────────────┘                    └─────────────────┘ │
 * └──────────────────────────────────────────────────────────┘
 *                              ↓
 *                    ┌─────────────────┐
 *                    │   StateStore    │
 *                    │  (Persistence)  │
 *                    └─────────────────┘
 *                              ↓
 *                    ┌─────────────────┐
 *                    │   Subscribers   │
 *                    │      (UI)       │
 *                    └─────────────────┘
 * ```
 *
 * ## Key Components
 *
 * ### Types (types.kt)
 * - [BaseCommand] - Base interface for all commands
 * - [BusinessRule] - Rule definition with trigger, condition, apply
 * - [RuleResult] - Result of rule application (asyncOps, followUp)
 * - [AsyncOperation] - Deferred side effect producing a command
 * - [StateStore] - Storage interface (getState, applyMutations, emit, subscribe)
 * - [UIMeta] - UI meta state (isLoading)
 *
 * ### Command Factory (command.kt)
 * - [command] - Create command definitions
 * - [CommandDef] - Typed command definition with payload
 * - [CommandDefNoPayload] - Command definition without payload
 * - [RuleRegistry] - Isolated rule registry per application
 * - [createRuleFactory] - Create rule registration function
 *
 * ### Coordinator (coordinator.kt)
 * - [StateCoordinator] - Main orchestrator for command processing
 * - [CoordinatorLogger] - Logger interface for debugging
 * - [nullLogger] - No-op logger for production
 *
 * ## Usage Example
 *
 * ```kotlin
 * // 1. Define state
 * data class AppState(
 *     val counter: Int = 0,
 *     val loading: Boolean = false
 * )
 *
 * // 2. Define commands
 * data class IncrementPayload(val amount: Int)
 * val Increment = command("counter/INCREMENT").payload<IncrementPayload>()
 * val Reset = command("counter/RESET").noPayload()
 *
 * // 3. Define rule result with mutations
 * data class AppRuleResult(
 *     override val asyncOps: List<AsyncOperation>? = null,
 *     override val followUp: List<BaseCommand>? = null,
 *     val mutations: Map<String, Any?>? = null
 * ) : RuleResult
 *
 * // 4. Create store
 * class AppStore : StateStore<AppState, AppRuleResult> {
 *     private var state = AppState()
 *     private val listeners = mutableSetOf<StateListener<AppState>>()
 *
 *     override fun getState() = state
 *
 *     override suspend fun applyMutations(result: AppRuleResult) {
 *         result.mutations?.let { mutations ->
 *             state = state.copy(
 *                 counter = mutations["counter"] as? Int ?: state.counter
 *             )
 *         }
 *     }
 *
 *     override fun emit() {
 *         listeners.forEach { it(state) }
 *     }
 *
 *     override fun subscribe(listener: StateListener<AppState>): () -> Unit {
 *         listeners.add(listener)
 *         return { listeners.remove(listener) }
 *     }
 * }
 *
 * // 5. Create coordinator and register rules
 * val store = AppStore()
 * val coordinator = StateCoordinator(store)
 * val registry = RuleRegistry<AppState, AppRuleResult>()
 * val rule = createRuleFactory(registry)
 *
 * rule(Increment, RuleConfig(
 *     condition = { _, _ -> true },
 *     apply = { state, payload ->
 *         AppRuleResult(mutations = mapOf("counter" to state.counter + payload.amount))
 *     }
 * ))
 *
 * coordinator.setRules(registry.getAll())
 *
 * // 6. Dispatch commands
 * scope.launch {
 *     coordinator.dispatch(Increment.create(IncrementPayload(5)))
 * }
 * ```
 *
 * ## Thread Safety
 *
 * - StateCoordinator uses Kotlin Coroutines for async operations
 * - dispatch() is a suspend function and should be called from a coroutine
 * - UI updates should be done on EDT (Dispatchers.EDT)
 * - State mutations are applied synchronously within dispatch()
 *
 * @see StateCoordinator
 * @see command
 * @see RuleRegistry
 */
package lg.intellij.stateengine

// Public API is exported via explicit imports in consuming code.
// This file serves as documentation and package-level info.
