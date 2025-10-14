package lg.intellij.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.runBlocking
import lg.intellij.cli.CliExecutor
import lg.intellij.cli.models.CliResult

/**
 * Test action for CLI integration.
 * 
 * Executes `listing-generator --version` to verify CLI is accessible.
 * Displays result in a dialog.
 * 
 * This is a TEMPORARY action for Phase 1 testing.
 * Will be removed in later phases when real functionality is implemented.
 * 
 * Usage:
 * 1. Enable mock mode if CLI is not available: CliExecutor.useMockMode = true
 * 2. Trigger action via Tools > Listing Generator > Test CLI Connection
 * 3. View result in modal dialog
 */
class LgTestCliAction : AnAction("Test CLI Connection"), DumbAware {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        object : Task.Backgroundable(
            project,
            "Testing CLI Connection...",
            true
        ) {
            private var result: CliResult<String>? = null
            
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Executing listing-generator --version..."
                indicator.isIndeterminate = true
                
                val executor = project.service<CliExecutor>()
                
                // Enable mock mode for development without real CLI
                // TODO: Remove this or make configurable in Settings (Phase 2)
                executor.useMockMode = true
                
                result = runBlocking {
                    executor.execute(listOf("--version"))
                }
            }
            
            override fun onSuccess() {
                when (val res = result) {
                    is CliResult.Success -> {
                        Messages.showInfoMessage(
                            project,
                            "CLI Version:\n\n${res.data}",
                            "CLI Connection Success"
                        )
                    }
                    
                    is CliResult.Failure -> {
                        Messages.showErrorDialog(
                            project,
                            "CLI execution failed (exit code ${res.exitCode}):\n\n${res.stderr}",
                            "CLI Connection Failed"
                        )
                    }
                    
                    is CliResult.Timeout -> {
                        Messages.showErrorDialog(
                            project,
                            "CLI execution timeout after ${res.timeoutMs}ms",
                            "CLI Connection Failed"
                        )
                    }
                    
                    is CliResult.NotFound -> {
                        Messages.showErrorDialog(
                            project,
                            "CLI not found:\n\n${res.message}",
                            "CLI Connection Failed"
                        )
                    }
                    
                    null -> {
                        Messages.showErrorDialog(
                            project,
                            "Unknown error occurred",
                            "CLI Connection Failed"
                        )
                    }
                }
            }
            
            override fun onThrowable(error: Throwable) {
                Messages.showErrorDialog(
                    project,
                    "Unexpected error:\n\n${error.message}",
                    "CLI Connection Failed"
                )
            }
        }.queue()
    }
    
    override fun update(e: AnActionEvent) {
        // Always enabled for testing
        e.presentation.isEnabled = e.project != null
    }
    
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
