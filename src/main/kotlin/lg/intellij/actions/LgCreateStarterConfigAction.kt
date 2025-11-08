package lg.intellij.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import lg.intellij.LgBundle
import lg.intellij.ui.dialogs.LgInitWizardDialog

/**
 * Action to create starter lg-cfg configuration via wizard.
 * 
 * Opens [LgInitWizardDialog] which handles:
 * - Preset selection
 * - Conflict resolution
 * - Opening sections.yaml on success
 * 
 * Available in:
 * - Control Panel toolbar
 * - Tools → Listing Generator menu
 */
class LgCreateStarterConfigAction : AnAction(
    LgBundle.message("control.btn.create.config"),
    LgBundle.message("action.create.config.description"),
    AllIcons.Actions.AddDirectory
) {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        val dialog = LgInitWizardDialog(project)
        dialog.show()
    }
    
    override fun update(e: AnActionEvent) {
        // Enabled только если есть проект
        e.presentation.isEnabledAndVisible = e.project != null
    }
    
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

