package lg.intellij.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import kotlinx.coroutines.runBlocking
import lg.intellij.LgBundle
import lg.intellij.models.DiagReportSchema
import lg.intellij.services.diagnostics.LgDiagnosticsService
import lg.intellij.ui.dialogs.LgDoctorDialog

/**
 * Action to run diagnostics and show Doctor dialog.
 * 
 * Phase 14: Doctor Diagnostics implementation.
 */
class LgRunDoctorAction : AnAction(
    LgBundle.message("action.doctor.text"),
    LgBundle.message("action.doctor.description"),
    AllIcons.Actions.Checked
) {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val diagnosticsService = project.service<LgDiagnosticsService>()
        
        object : Task.Backgroundable(
            project,
            LgBundle.message("action.doctor.progress"),
            true
        ) {
            private var report: DiagReportSchema? = null
            
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = LgBundle.message("action.doctor.progress.text")
                
                report = runBlocking {
                    diagnosticsService.runDiagnostics()
                }
            }
            
            override fun onSuccess() {
                val result = report
                if (result != null) {
                    val dialog = LgDoctorDialog(project, result)
                    dialog.show()
                }
            }
        }.queue()
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
    
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

