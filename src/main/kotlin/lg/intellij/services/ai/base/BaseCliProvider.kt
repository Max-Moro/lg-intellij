package lg.intellij.services.ai.base

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import lg.intellij.models.AiInteractionMode
import lg.intellij.models.CliExecutionContext
import lg.intellij.services.ai.AiProvider
import lg.intellij.services.ai.AiProviderException
import lg.intellij.services.state.LgPanelStateService
import com.intellij.terminal.ui.TerminalWidget
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

/**
 * Base class for CLI-based AI providers.
 *
 * Provides common infrastructure for providers that work through CLI utilities in terminal
 * (e.g., Claude CLI, Codex CLI).
 *
 * Main capabilities:
 * - Terminal lifecycle management (create, reuse, check busy state)
 * - Shell initialization with delay
 * - Scope support (workspace subdirectory execution)
 * - Terminal busy state detection
 *
 * Subclasses must implement:
 * - checkTerminalBusy() - provider-specific busy detection
 * - executeInTerminal() - provider-specific command execution
 */
abstract class BaseCliProvider : AiProvider {

    protected val log = logger<BaseCliProvider>()

    /**
     * Get CLI execution context from panel state.
     *
     * @param project Project context
     * @param mode AI interaction mode
     * @return CLI execution context with scope/shell/mode
     */
    protected fun getCliBaseContext(project: Project, mode: AiInteractionMode): CliExecutionContext {
        val stateService = project.service<LgPanelStateService>()
        val state = stateService.state

        return CliExecutionContext(
            scope = state.cliScope ?: "",
            shell = state.cliShell,
            mode = mode,
            claudeModel = state.claudeModel,
            codexReasoningEffort = state.codexReasoningEffort
        )
    }

    /**
     * Check if terminal is busy with an unfinished process.
     *
     * @param project Project context
     * @param widget Terminal widget to check
     * @param ctx CLI execution context
     * @return Busy state with optional user message
     */
    protected abstract suspend fun checkTerminalBusy(
        project: Project,
        widget: TerminalWidget,
        ctx: CliExecutionContext
    ): BusyState

    /**
     * Create and prepare terminal.
     *
     * Reuses existing terminal with same name if not busy, or creates new one.
     * Shows terminal to user and waits for shell initialization.
     * Terminal is started in workspace root directory.
     *
     * @param project Project context
     * @param ctx CLI execution context for busy check
     * @return Terminal widget and isNew flag, or null if terminal is busy
     */
    protected suspend fun ensureTerminal(
        project: Project,
        ctx: CliExecutionContext
    ): TerminalResult? = withContext(Dispatchers.EDT) {
        val manager = TerminalToolWindowManager.getInstance(project)

        // Check existing terminal
        val existing = manager.terminalWidgets
            .find { it.terminalTitle.buildTitle() == name }

        if (existing != null) {
            // Check if terminal is still active (has TTY connector)
            val ttyConnector = existing.ttyConnector
            if (ttyConnector == null || !ttyConnector.isConnected) {
                // Terminal closed - can't reuse
                // Let it be disposed naturally, create new one
            } else {
                // Terminal active - check if busy
                val busyState = checkTerminalBusy(project, existing, ctx)

                if (busyState.busy) {
                    // Show warning to user
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("LG Important")
                        .createNotification(
                            "CLI session active",
                            busyState.message ?: "Previous CLI session is still running. Please complete it before starting a new one.",
                            NotificationType.WARNING
                        )
                        .addAction(
                            NotificationAction.createSimple("Show terminal") {
                                val toolWindow = manager.toolWindow
                                toolWindow?.activate(null)
                            }
                        )
                        .notify(project)

                    return@withContext null
                }

                // Terminal free - reuse
                return@withContext TerminalResult(existing, isNew = false)
            }
        }

        // Create new terminal
        val workspaceRoot = ProjectManager.getInstance().openProjects.firstOrNull()?.basePath ?: ""
        val widget = manager.createShellWidget(workspaceRoot, name, true, true)

        // Wait for shell initialization
        delay(500)

        return@withContext TerminalResult(widget, isNew = true)
    }

    /**
     * Send content through CLI.
     *
     * Gets CLI execution context, prepares terminal,
     * changes directory if scope provided, and
     * calls executeInTerminal for command execution.
     */
    override suspend fun send(content: String) {
        val project = getCurrentProject()
            ?: throw AiProviderException("No project context available")

        val stateService = project.service<LgPanelStateService>()
        val mode = stateService.getAiInteractionMode()

        // Get CLI execution context
        val ctx = getCliBaseContext(project, mode)

        // Create terminal and get isNew flag
        val result = ensureTerminal(project, ctx) ?: return

        // Change directory for new terminal (if scope provided)
        if (result.isNew && ctx.scope.isNotBlank()) {
            // Validate scope - must be relative path
            val path = java.nio.file.Paths.get(ctx.scope)
            if (path.isAbsolute) {
                throw AiProviderException("Scope must be a relative path")
            }

            withContext(Dispatchers.EDT) {
                result.widget.sendCommandToExecute("cd \"${ctx.scope}\"")
            }

            // Small delay for cd to complete
            delay(100)
        }

        // Execute provider-specific command
        executeInTerminal(project, content, result.widget, ctx)
    }

    /**
     * Execute CLI command in terminal.
     *
     * Implemented by subclasses for provider-specific command execution.
     * Terminal is already in correct directory (if scope was provided).
     *
     * @param project Project context
     * @param content Content to send
     * @param widget Prepared terminal widget
     * @param ctx CLI execution context
     */
    protected abstract suspend fun executeInTerminal(
        project: Project,
        content: String,
        widget: TerminalWidget,
        ctx: CliExecutionContext
    )

    /**
     * Get current project (first open project).
     */
    protected fun getCurrentProject(): Project? {
        return ProjectManager.getInstance().openProjects.firstOrNull()
    }

    /**
     * Result of busy state check.
     */
    data class BusyState(
        val busy: Boolean,
        val message: String? = null
    )

    /**
     * Result of terminal creation/retrieval.
     */
    data class TerminalResult(
        val widget: TerminalWidget,
        val isNew: Boolean
    )
}

