package lg.intellij.listeners

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import kotlinx.coroutines.*
import lg.intellij.services.catalog.LgCatalogService

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
    
    // Scope для debounce механизма
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Текущая отложенная задача reload (для отмены)
    private var pendingReload: Job? = null
    
    override fun after(events: List<VFileEvent>) {
        // Фильтруем: интересуют только изменения в lg-cfg/
        val hasConfigChanges = events.any { event ->
            val file = event.file ?: return@any false
            
            // Проверяем что файл в lg-cfg/ директории текущего проекта
            isInLgConfigDir(file.path)
        }
        
        if (!hasConfigChanges) {
            return
        }
        
        LOG.debug("Detected changes in lg-cfg/, scheduling reload")
        scheduleReload()
    }
    
    /**
     * Планирует reload с debounce (500ms).
     * Отменяет предыдущий pending reload если есть.
     */
    private fun scheduleReload() {
        // Отменить предыдущую задачу
        pendingReload?.cancel()
        
        // Запланировать новую с задержкой
        pendingReload = scope.launch {
            delay(DEBOUNCE_DELAY_MS)
            
            try {
                val catalogService = project.service<LgCatalogService>()
                catalogService.reload()
                LOG.info("Catalog reloaded after lg-cfg/ changes")
            } catch (e: CancellationException) {
                // Отменено новым событием - норма
                throw e
            } catch (e: Exception) {
                LOG.error("Failed to reload catalog after file changes", e)
            }
        }
    }
    
    /**
     * Проверяет что файл находится в lg-cfg/ директории проекта.
     */
    private fun isInLgConfigDir(filePath: String): Boolean {
        val basePath = project.basePath ?: return false
        
        // Нормализуем пути (Windows vs Unix)
        val normalizedBase = basePath.replace('\\', '/')
        val normalizedFile = filePath.replace('\\', '/')
        
        // Проверяем что файл в {basePath}/lg-cfg/
        val lgCfgPath = "$normalizedBase/lg-cfg/"
        return normalizedFile.startsWith(lgCfgPath)
    }
    
    /**
     * Cleanup при dispose проекта.
     */
    fun dispose() {
        scope.cancel()
        LOG.debug("Config file listener disposed for project: ${project.name}")
    }
    
    companion object {
        private val LOG = logger<LgConfigFileListener>()
        private const val DEBOUNCE_DELAY_MS = 500L
    }
}

