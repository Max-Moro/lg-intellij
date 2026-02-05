package lg.intellij.statepce

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import lg.intellij.stateengine.CoordinatorLogger
import lg.intellij.stateengine.StateCoordinator
import lg.intellij.statepce.domains.registerAllDomainRules

/**
 * LG Extension State Coordinator Setup
 *
 * Provides factory and types for using StateCoordinator with LG state.
 * The factory automatically registers all domain rules.
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
 * Registers all domain rules and configures the coordinator.
 *
 * @param store PCEStateStore instance
 * @param project Project for service access in domain rules
 * @return Configured coordinator ready for dispatching commands
 */
fun createPCECoordinator(store: PCEStateStore, project: Project): PCEStateCoordinator {
    // Register domain rules (each domain captures project for async operations)
    registerAllDomainRules(project)

    val coordinator = StateCoordinator(store, lgLogger)
    coordinator.setRules(getAllRules())
    return coordinator
}
