package lg.intellij.services.ai.providers.codexcli

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.terminal.ui.TerminalWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lg.intellij.cli.ExecutableDetector
import lg.intellij.models.AiInteractionMode
import lg.intellij.models.CliExecutionContext
import lg.intellij.models.CodexReasoningEffort
import lg.intellij.services.ai.base.BaseCliProvider
import kotlin.io.path.exists

/**
 * OpenAI Codex CLI Provider.
 *
 * Integrates with Codex CLI via session-based method:
 * 1. Creates a session file in ~/.codex/sessions/
 * 2. Launches `codex resume SESSION_ID` in terminal
 */
class CodexCliProvider : BaseCliProvider() {

    override val id = "codex.cli"
    override val name = "Codex CLI"
    override val priority = 45

    override suspend fun isAvailable(): Boolean {
        return try {
            val codexExecutable = ExecutableDetector.findExecutable("codex")
            codexExecutable != null
        } catch (e: Exception) {
            log.debug("Codex CLI detection failed", e)
            false
        }
    }

    override suspend fun checkTerminalBusy(
        project: Project,
        widget: TerminalWidget,
        ctx: CliExecutionContext
    ): BusyState {
        val workingDirectory = CodexCommon.getWorkingDirectory(project, ctx.scope)
        val lockFilePath = CodexCommon.resolveFile(
            workingDirectory,
            CodexCommon.CODEX_SESSION_LOCK_FILE
        )

        return if (lockFilePath.exists()) {
            BusyState(
                busy = true,
                message = "Codex CLI session is still active. Please run \"/exit\" in the terminal to complete the current session before starting a new one."
            )
        } else {
            BusyState(busy = false)
        }
    }

    override suspend fun executeInTerminal(
        project: Project,
        content: String,
        widget: TerminalWidget,
        ctx: CliExecutionContext
    ) {
        val workingDirectory = CodexCommon.getWorkingDirectory(project, ctx.scope)

        // Get reasoning effort from context (or default)
        val reasoningEffort = ctx.codexReasoningEffort ?: CodexReasoningEffort.MEDIUM

        // Determine sandbox mode from AI interaction mode
        val sandboxMode = when (ctx.mode) {
            AiInteractionMode.ASK -> "read-only"
            AiInteractionMode.AGENT -> "workspace-write"
        }

        log.debug("Creating Codex session with reasoning effort: $reasoningEffort")

        // Create session
        val sessionId = CodexSession.createSession(
            CodexSessionParams(
                content = content,
                cwd = workingDirectory,
                shell = ctx.shell,
                reasoningEffort = reasoningEffort,
                sandboxMode = sandboxMode
            )
        )

        log.debug("Session created: $sessionId")

        // Create lock file
        val lockFilePath = CodexCommon.resolveFile(
            workingDirectory,
            CodexCommon.CODEX_SESSION_LOCK_FILE
        )

        try {
            lockFilePath.toFile().writeText("")
            log.debug("Lock file created: ${CodexCommon.CODEX_SESSION_LOCK_FILE}")
        } catch (e: Exception) {
            log.warn("Failed to create lock file: ${e.message}")
        }

        // Build and execute command
        val codexCommand = CodexCommon.buildCodexCommand(
            sessionId = sessionId,
            shell = ctx.shell,
            lockFile = CodexCommon.CODEX_SESSION_LOCK_FILE,
            reasoningEffort = reasoningEffort
        )

        log.debug("Sending command: $codexCommand")

        // Execute in terminal (must be on EDT)
        withContext(Dispatchers.EDT) {
            widget.sendCommandToExecute(codexCommand)
        }
    }
}
