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
import lg.intellij.services.generation.GenerationException
import lg.intellij.services.generation.GenerationTarget
import lg.intellij.services.generation.LgGenerationService
import lg.intellij.services.state.LgPanelStateService
import lg.intellij.ui.dialogs.OutputPreviewDialog
import javax.swing.Icon

/**
 * Universal action for generating content (listings or contexts).
 * 
 * Phase 7: Single action class with target type parameter (DRY principle).
 * Phase 8: TODO - Replace dialog with VirtualFile display in editor.
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
                
                try {
                    output = runBlocking {
                        generationService.generate(targetType, targetName)
                    }
                } catch (e: GenerationException) {
                    LOG.warn("${targetType.displayName.replaceFirstChar { it.uppercase() }} generation failed: ${e.message}")
                } catch (e: Exception) {
                    LOG.error("Unexpected error during ${targetType.displayName} generation", e)
                }
            }
            
            override fun onSuccess() {
                val result = output
                if (result != null) {
                    showOutputDialog(project, result, targetName)
                }
            }
        }.queue()
    }
    
    private fun showOutputDialog(project: Project, content: String, targetName: String) {
        val title = LgBundle.message("dialog.output.${targetType.displayName}.title", targetName)
        val dialog = OutputPreviewDialog(project, content, title)
        dialog.show()
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

