package lg.intellij.statepce

import com.intellij.openapi.diagnostic.logger
import lg.intellij.stateengine.CoordinatorLogger
import lg.intellij.stateengine.StateCoordinator

/**
 * LG Extension State Coordinator Setup
 *
 * Provides factory and types for using StateCoordinator with LG state.
 * The factory automatically registers all domain rules.
 *
 * Ported from VS Code Extension's state-lg/coordinator.ts
 */

/**
 * Type alias for LG coordinator instance.
 */
typealias PCEStateCoordinator = StateCoordinator<PCEState, LGRuleResult>

/**
 * Logger adapter for StateCoordinator.
 *
 * Routes coordinator debug/error messages to IntelliJ Platform logging.
 */
private val lgLogger = object : CoordinatorLogger {
    private val LOG = logger<PCEStateStore>()

    override fun debug(msg: String) {
        LOG.debug("[Coordinator] $msg")
    }

    override fun error(msg: String, error: Throwable?) {
        LOG.error("[Coordinator] $msg", error)
    }
}

/**
 * Create a StateCoordinator configured for LG Extension.
 * Automatically registers all domain rules.
 *
 * @param store PCEStateStore instance
 * @return Configured coordinator ready for dispatching commands
 */
fun createPCECoordinator(store: PCEStateStore): PCEStateCoordinator {
    // Domain rule modules register via side-effect at class loading time.
    // By the time getAllRules() is called, all rule() invocations
    // in domain modules have already executed.
    val coordinator = StateCoordinator(store, lgLogger)
    coordinator.setRules(getAllRules())
    return coordinator
}
