package lg.intellij.bootstrap

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import lg.intellij.statepce.PCEStateStore
import lg.intellij.statepce.PCEStateCoordinator
import lg.intellij.statepce.createPCECoordinator

/**
 * Bootstrap Layer — centralized coordinator initialization per project.
 *
 * Manages PCEStateCoordinator lifecycle. All state is read directly
 * from PCEStateStore by consumers (actions, services, UI).
 */

private val LOG = logger<Bootstrap>()

// Placeholder class for logger initialization
private class Bootstrap

// Per-project coordinator instances
private val coordinators = mutableMapOf<Project, PCEStateCoordinator>()

/**
 * Initialize Coordinator for a project.
 * Called once when Tool Window is first created.
 *
 * @return Created or existing coordinator
 */
fun bootstrapCoordinator(project: Project): PCEStateCoordinator {
    val existing = coordinators[project]
    if (existing != null) {
        LOG.warn("Coordinator already bootstrapped for project ${project.name}")
        return existing
    }

    val store = PCEStateStore.getInstance(project)
    val coordinator = createPCECoordinator(store, project)

    coordinators[project] = coordinator

    LOG.info("Coordinator bootstrapped for project ${project.name}")

    return coordinator
}

/**
 * Returns Coordinator for a project.
 *
 * @throws IllegalStateException if not bootstrapped
 */
fun getCoordinator(project: Project): PCEStateCoordinator {
    return coordinators[project]
        ?: throw IllegalStateException("Coordinator not bootstrapped for project ${project.name}")
}

/**
 * Returns Store for a project.
 */
fun getStore(project: Project): PCEStateStore {
    return PCEStateStore.getInstance(project)
}

/**
 * Cleans up Coordinator when project is closed.
 */
fun shutdownCoordinator(project: Project) {
    // Dispose coordinator
    coordinators.remove(project)?.dispose()

    LOG.info("Coordinator shutdown for project ${project.name}")
}
