package lg.intellij.bootstrap

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lg.intellij.services.state.LgPanelStateService
import lg.intellij.statepce.PCEState
import lg.intellij.statepce.PCEStateStore
import lg.intellij.statepce.PCEStateCoordinator
import lg.intellij.statepce.createPCECoordinator

/**
 * Bootstrap Layer — centralized coordinator initialization per project.
 *
 * Manages PCEStateCoordinator lifecycle and provides a sync bridge
 * from PCEStateStore to LgPanelStateService for backward compatibility
 * with Actions and Services (migrated in next step).
 */

private val LOG = logger<Bootstrap>()

// Placeholder class for logger initialization
private class Bootstrap

// Per-project coordinator instances
private val coordinators = mutableMapOf<Project, PCEStateCoordinator>()

// Per-project unsubscribe functions for sync bridge
private val syncBridgeUnsubscribers = mutableMapOf<Project, () -> Unit>()

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

    // Set up sync bridge (PCEStateStore → LgPanelStateService)
    setupSyncBridge(project, store)

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
    // Remove sync bridge
    syncBridgeUnsubscribers.remove(project)?.invoke()

    // Dispose coordinator
    coordinators.remove(project)?.dispose()

    LOG.info("Coordinator shutdown for project ${project.name}")
}

/**
 * Sets up sync bridge: PCEStateStore → LgPanelStateService.
 *
 * This temporary bridge ensures backward compatibility with Actions
 * and Services that still read from LgPanelStateService.
 * Will be removed when all consumers are migrated to PCEStateStore.
 */
private fun setupSyncBridge(project: Project, store: PCEStateStore) {
    val unsubscribe = store.subscribe { state ->
        syncToPanelState(project, state)
    }

    syncBridgeUnsubscribers[project] = unsubscribe
    LOG.debug("Sync bridge established for project ${project.name}")
}

/**
 * Syncs PCEState fields to LgPanelStateService.
 */
private fun syncToPanelState(project: Project, state: PCEState) {
    try {
        val panelState = LgPanelStateService.getInstance(project)
        val ps = panelState.state

        ps.selectedTemplate = state.persistent.template
        ps.selectedSection = state.persistent.section
        ps.providerId = state.persistent.providerId
        ps.tokenizerLib = state.persistent.tokenizerLib
        ps.encoder = state.persistent.encoder
        ps.ctxLimit = state.persistent.ctxLimit
        ps.cliScope = state.persistent.cliScope
        ps.cliShell = state.persistent.cliShell
        ps.targetBranch = state.persistent.targetBranch

        // Sync nested structures
        @Suppress("UNCHECKED_CAST")
        ps.modesByContextProvider = state.persistent.modesByContextProvider
            .toMutableMap() as MutableMap<String, MutableMap<String, MutableMap<String, String>>>

        ps.tagsByContext = state.persistent.tagsByContext
            .mapValues { (_, tagSets) ->
                tagSets.mapValues { (_, tags) -> tags.toMutableSet() }.toMutableMap()
            }.toMutableMap()

        // Update taskTextFlow (reactive)
        panelState.updateTaskText(state.persistent.taskText)
    } catch (e: Exception) {
        LOG.debug("Sync bridge error: ${e.message}")
    }
}
