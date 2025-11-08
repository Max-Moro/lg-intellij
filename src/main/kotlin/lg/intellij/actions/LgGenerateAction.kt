package lg.intellij.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import kotlinx.coroutines.runBlocking
import lg.intellij.LgBundle
import lg.intellij.services.generation.GenerationTarget
import lg.intellij.services.generation.LgGenerationService
import lg.intellij.services.state.LgPanelStateService
import lg.intellij.services.vfs.LgVirtualFileService
import javax.swing.Icon

/**
 * Universal action for generating content (listings or contexts).
 *
 * Displays results in editor via LgVirtualFileService (read-only or editable).
 */
open class LgGenerateAction(
    text: String,
    description: String,
    icon: Icon?,
    private val targetType: GenerationTarget,
    private val targetNameProvider: (LgPanelStateService) -> String?
) : AnAction(text, description, icon) {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val panelState = project.service<LgPanelStateService>()
        val generationService = project.service<LgGenerationService>()
        
        val targetName = targetNameProvider(panelState)
        if (targetName.isNullOrBlank()) {
            LOG.warn("Generate action triggered but no target selected for ${targetType.displayName}")
            return
        }
        
        object : Task.Backgroundable(
            project,
            LgBundle.message("action.generate.${targetType.displayName}.progress", targetName),
            true
        ) {
            private var output: String? = null
            
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = LgBundle.message("action.generate.${targetType.displayName}.progress.text", targetName)
                
                output = runBlocking {
                    generationService.generate(targetType, targetName)
                }
            }
            
            override fun onSuccess() {
                val result = output
                if (result != null) {
                    openInEditor(project, result, targetName)
                }
            }
        }.queue()
    }
    
    /**
     * Opens generated content in editor via LgVirtualFileService.
     */
    private fun openInEditor(project: Project, content: String, targetName: String) {
        val virtualFileService = project.service<LgVirtualFileService>()
        
        val success = when (targetType) {
            GenerationTarget.SECTION -> virtualFileService.openListing(content, targetName)
            GenerationTarget.CONTEXT -> virtualFileService.openContext(content, targetName)
        }
        
        if (!success) {
            LOG.warn("Failed to open ${targetType.displayName} in editor")
        }
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        val panelState = project?.service<LgPanelStateService>()
        val targetName = panelState?.let { targetNameProvider(it) }
        
        e.presentation.isEnabled = project != null && !targetName.isNullOrBlank()
    }
    
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    
    companion object {
        private val LOG = logger<LgGenerateAction>()
    }
}

