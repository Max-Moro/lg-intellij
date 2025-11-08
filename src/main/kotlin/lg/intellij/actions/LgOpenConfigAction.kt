package lg.intellij.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import lg.intellij.LgBundle
import lg.intellij.ui.dialogs.LgInitWizardDialog
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists

/**
 * Action to open lg-cfg/sections.yaml in editor.
 * 
 * If lg-cfg/sections.yaml doesn't exist:
 * - Shows dialog asking to create starter config
 * - Opens wizard on confirmation
 * 
 * Available in:
 * - Control Panel toolbar
 * - Tools → Listing Generator menu
 */
class LgOpenConfigAction : AnAction(
    LgBundle.message("action.open.config.text"),
    LgBundle.message("action.open.config.description"),
    AllIcons.Ide.ConfigFile
) {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val basePath = project.basePath ?: return
        
        val sectionsPath = Path.of(basePath) / "lg-cfg" / "sections.yaml"
        
        if (sectionsPath.exists()) {
            // File exists → open in editor
            val lfs = LocalFileSystem.getInstance()
            val virtualFile = lfs.refreshAndFindFileByNioFile(sectionsPath)
            
            if (virtualFile != null) {
                FileEditorManager.getInstance(project).openFile(virtualFile, true)
            } else {
                Messages.showErrorDialog(
                    project,
                    LgBundle.message("action.open.config.error.not.found"),
                    LgBundle.message("action.open.config.error.title")
                )
            }
        } else {
            // File doesn't exist → offer to create
            val choice = Messages.showYesNoDialog(
                project,
                LgBundle.message("action.open.config.create.message"),
                LgBundle.message("action.open.config.create.title"),
                LgBundle.message("action.open.config.create.yes"),
                LgBundle.message("action.open.config.create.no"),
                Messages.getQuestionIcon()
            )
            
            if (choice == Messages.YES) {
                // Open wizard
                val dialog = LgInitWizardDialog(project)
                dialog.show()
            }
        }
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
    
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

