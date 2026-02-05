package lg.intellij.listeners

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import kotlinx.coroutines.*
import lg.intellij.bootstrap.getCoordinator
import lg.intellij.services.LgInitService
import lg.intellij.statepce.domains.Refresh

/**
 * Listens for file changes in lg-cfg/ directory and triggers catalog reload.
 * 
 * Features:
 * - Debouncing (500ms) to avoid multiple reloads on batch changes
 * - Project-level filtering (only events for this project)
 * - Automatic cancellation on project disposal
 * 
 * Lifecycle: Project-level listener
 */
class LgConfigFileListener(private val project: Project) : BulkFileListener {
    
    // Scope for debounce mechanism
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Current pending reload task (for cancellation)
    private var pendingReload: Job? = null
    
    override fun after(events: List<VFileEvent>) {
        // Early pruning: while no lg-cfg/, nothing to listen to
        val initService = project.service<LgInitService>()
        if (!initService.isInitialized()) {
            return
        }

        // Filter:
        // only interested in changes in lg-cfg/
        val hasConfigChanges = events.any { event ->
            val file = event.file ?: return@any false

            // Check that file is in lg-cfg/ directory of current project
            isInLgConfigDir(file.path)
        }

        if (!hasConfigChanges) {
            return
        }

        LOG.debug("Detected changes in lg-cfg/, scheduling reload")
        scheduleReload()
    }
    
    /**
     * Schedules reload with debounce (500ms).
     * Cancels previous pending reload if exists.
     */
    private fun scheduleReload() {
        // Cancel previous task
        pendingReload?.cancel()

        // Schedule new with delay
        pendingReload = scope.launch {
            delay(DEBOUNCE_DELAY_MS)

            try {
                val coordinator = getCoordinator(project)
                coordinator.dispatch(Refresh.create())
                LOG.info("Dispatched Refresh after lg-cfg/ changes")
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                // Coordinator not yet bootstrapped - ignore
                LOG.debug("Coordinator not ready, skipping refresh")
            } catch (e: Exception) {
                LOG.error("Failed to dispatch refresh after file changes", e)
            }
        }
    }
    
    /**
     * Checks that file is in project's lg-cfg/ directory.
     */
    private fun isInLgConfigDir(filePath: String): Boolean {
        val basePath = project.basePath ?: return false

        // Normalize paths (Windows vs Unix)
        val normalizedBase = basePath.replace('\\', '/')
        val normalizedFile = filePath.replace('\\', '/')

        // Check that file is in {basePath}/lg-cfg/
        val lgCfgPath = "$normalizedBase/lg-cfg/"
        return normalizedFile.startsWith(lgCfgPath)
    }

    companion object {
        private val LOG = logger<LgConfigFileListener>()
        private const val DEBOUNCE_DELAY_MS = 500L
    }
}

