package lg.intellij.ui.dialogs

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import lg.intellij.LgBundle
import lg.intellij.models.InitResult
import lg.intellij.services.LgInitService
import lg.intellij.services.catalog.LgCatalogService
import java.nio.file.Path
import javax.swing.JComponent
import kotlin.io.path.div

/**
 * Dialog for initializing lg-cfg via `lg init` command.
 * 
 * Features:
 * - Async loading of available presets
 * - Preset selection via ComboBox
 * - Conflict resolution (force overwrite confirmation)
 * - Opens sections.yaml in editor on success
 * 
 * Flow:
 * 1. Dialog opens → load presets asynchronously
 * 2. User selects preset and clicks OK
 * 3. Execute `lg init --preset X`
 * 4. If conflicts → show confirmation dialog
 * 5. If confirmed → execute with --force
 * 6. On success → open sections.yaml in editor
 */
class LgInitWizardDialog(
    private val project: Project
) : DialogWrapper(project) {
    
    private val initService = project.service<LgInitService>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private val presetCombo = ComboBox<String>()
    
    private var presetsLoaded = false
    
    init {
        title = LgBundle.message("dialog.init.title")
        init()
        
        // Disable OK until presets loaded
        isOKActionEnabled = false
        
        // Load presets asynchronously
        loadPresetsAsync()
    }
    
    override fun createCenterPanel(): JComponent {
        return panel {
            row {
                label(LgBundle.message("dialog.init.description"))
            }
            
            row(LgBundle.message("dialog.init.preset.label")) {
                cell(presetCombo)
                    .align(AlignX.FILL)
            }
        }.apply {
            preferredSize = JBUI.size(400, 80)
        }
    }
    
    /**
     * Loads available presets from CLI asynchronously.
     */
    private fun loadPresetsAsync() {
        scope.launch {
            try {
                val presets = initService.listPresets()
                ApplicationManager.getApplication().invokeLater({
                    updatePresetsUI(presets)
                }, ModalityState.stateForComponent(contentPane))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LOG.error("Failed to load presets", e)
                
                ApplicationManager.getApplication().invokeLater({
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("LG Important")
                        .createNotification(
                            LgBundle.message("dialog.init.error.title"),
                            LgBundle.message("dialog.init.error.load.presets"),
                            NotificationType.ERROR
                        )
                        .notify(project)
                    
                    updatePresetsUI(listOf("basic")) // Fallback
                }, ModalityState.stateForComponent(contentPane))
            }
        }
    }
    
    /**
     * Updates UI with loaded presets.
     */
    private fun updatePresetsUI(presets: List<String>) {
        presetCombo.removeAllItems()
        presets.forEach { presetCombo.addItem(it) }
        
        if (presets.isNotEmpty()) {
            presetCombo.selectedIndex = 0
            isOKActionEnabled = true
            presetsLoaded = true
        } else {
            isOKActionEnabled = false
        }
    }
    
    /**
     * Executes initialization when OK clicked.
     */
    override fun doOKAction() {
        if (!presetsLoaded) {
            return
        }
        
        val preset = presetCombo.selectedItem as? String ?: return
        
        // Close dialog first
        super.doOKAction()
        
        // Execute with blocking progress
        val result = ProgressManager.getInstance().runProcessWithProgressSynchronously<InitResult, Exception>(
            {
                runBlocking {
                    initService.initWithPreset(preset, force = false)
                }
            },
            LgBundle.message("dialog.init.progress.title"),
            true,
            project
        )
        
        handleInitResult(result)
    }
    
    /**
     * Handles initialization result.
     */
    private fun handleInitResult(result: InitResult) {
        when {
            result.ok -> {
                // Success → refresh catalogs and open sections.yaml
                refreshCatalogs()
                openSectionsYaml()
                
                Messages.showInfoMessage(
                    project,
                    LgBundle.message("dialog.init.success.message", result.created.size),
                    LgBundle.message("dialog.init.success.title")
                )
            }
            
            result.conflicts.isNotEmpty() -> {
                // Conflicts → ask user to overwrite
                val choice = Messages.showYesNoDialog(
                    project,
                    LgBundle.message("dialog.init.conflicts.message", result.conflicts.size),
                    LgBundle.message("dialog.init.conflicts.title"),
                    LgBundle.message("dialog.init.conflicts.overwrite"),
                    LgBundle.message("dialog.init.conflicts.cancel"),
                    Messages.getWarningIcon()
                )
                
                if (choice == Messages.YES) {
                    // Retry with --force
                    retryWithForce(result.preset)
                }
            }
            
            else -> {
                // Generic error
                Messages.showErrorDialog(
                    project,
                    result.error ?: result.message ?: "Unknown error",
                    LgBundle.message("dialog.init.error.title")
                )
            }
        }
    }
    
    /**
     * Retries initialization with --force flag.
     */
    private fun retryWithForce(preset: String) {
        val result = ProgressManager.getInstance().runProcessWithProgressSynchronously<InitResult, Exception>(
            {
                runBlocking {
                    initService.initWithPreset(preset, force = true)
                }
            },
            LgBundle.message("dialog.init.progress.title"),
            true,
            project
        )
        
        if (result.ok) {
            refreshCatalogs()
            openSectionsYaml()
            
            Messages.showInfoMessage(
                project,
                LgBundle.message("dialog.init.success.overwritten", result.created.size),
                LgBundle.message("dialog.init.success.title")
            )
        } else {
            Messages.showErrorDialog(
                project,
                result.error ?: "Failed to overwrite files",
                LgBundle.message("dialog.init.error.title")
            )
        }
    }
    
    /**
     * Refreshes catalog data after successful initialization.
     */
    private fun refreshCatalogs() {
        val catalogService = project.service<LgCatalogService>()
        
        // Launch in service's own scope (not dialog's scope which may be cancelled)
        ApplicationManager.getApplication().invokeLater({
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                catalogService.loadAll()
            }
        }, ModalityState.nonModal())
    }
    
    /**
     * Opens lg-cfg/sections.yaml in editor.
     */
    private fun openSectionsYaml() {
        val basePath = project.basePath ?: return
        val sectionsPath = Path.of(basePath) / "lg-cfg" / "sections.yaml"
        
        // Refresh VFS to pick up new file
        val lfs = LocalFileSystem.getInstance()
        val virtualFile = lfs.refreshAndFindFileByNioFile(sectionsPath)
        
        if (virtualFile != null) {
            FileEditorManager.getInstance(project).openFile(virtualFile, true)
        } else {
            LOG.warn("Failed to find sections.yaml after init: $sectionsPath")
        }
    }
    
    override fun dispose() {
        scope.cancel()
        super.dispose()
    }
    
    companion object {
        private val LOG = logger<LgInitWizardDialog>()
    }
}

