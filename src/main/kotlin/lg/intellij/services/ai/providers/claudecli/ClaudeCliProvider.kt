package lg.intellij.services.ai.providers.claudecli

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.terminal.ui.TerminalWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lg.intellij.cli.ExecutableDetector
import lg.intellij.models.AiInteractionMode
import lg.intellij.models.ClaudeIntegrationMethod
import lg.intellij.models.CliExecutionContext
import lg.intellij.services.ai.base.BaseCliProvider
import lg.intellij.services.state.LgPanelStateService
import kotlin.io.path.exists

/**
 * Claude CLI Provider with dual integration methods.
 *
 * Methods:
 * 1. Memory File (CLAUDE.local.md) - stable but visible to subagents
 * 2. Session (saved sessions) - better isolation
 *
 * Method selected via claudeIntegrationMethod state
 */
class ClaudeCliProvider : BaseCliProvider() {

    override val id = "claude.cli"
    override val name = "Claude CLI"
    override val priority = 50

    companion object {
        /** Lock file name for session-based integration */
        private const val SESSION_LOCK_FILE = ".claude-session.lock"

        /** Message shown when CLI session is active */
        private const val SESSION_ACTIVE_MESSAGE =
            "Claude CLI session is still active. Please run \"/exit\" in the terminal to complete the current session before starting a new one."
    }

    override suspend fun isAvailable(): Boolean {
        // Use ExecutableDetector with fallback to common user paths
        // This correctly handles shell environment on all platforms (including Linux GUI apps)
        return try {
            val claudeExecutable = ExecutableDetector.findExecutable("claude")
            claudeExecutable != null
        } catch (e: Exception) {
            log.debug("Claude CLI detection failed", e)
            false
        }
    }

    override suspend fun checkTerminalBusy(
        project: Project,
        widget: TerminalWidget,
        ctx: CliExecutionContext
    ): BusyState {
        // Compute working directory early
        val workingDirectory = ClaudeCommon.getWorkingDirectory(project, ctx.scope)

        // Determine lock file based on integration method
        val stateService = project.service<LgPanelStateService>()
        val method = stateService.state.claudeIntegrationMethod

        val lockFile = when (method) {
            ClaudeIntegrationMethod.MEMORY_FILE -> ClaudeMethodMemoryFile.CLAUDE_LOCAL_FILE
            ClaudeIntegrationMethod.SESSION -> SESSION_LOCK_FILE
        }

        return checkLockFile(workingDirectory, lockFile, SESSION_ACTIVE_MESSAGE)
    }

    @Suppress("SameParameterValue")
    private fun checkLockFile(
        workingDirectory: java.nio.file.Path,
        lockFile: String,
        message: String
    ): BusyState {
        val lockFilePath = ClaudeCommon.resolveFile(workingDirectory, lockFile)

        return if (lockFilePath.exists()) {
            BusyState(busy = true, message = message)
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
        val workingDirectory = ClaudeCommon.getWorkingDirectory(project, ctx.scope)

        val permissionMode = mapModeToPermission(ctx.mode)

        val stateService = project.service<LgPanelStateService>()
        val method = stateService.state.claudeIntegrationMethod

        when (method) {
            ClaudeIntegrationMethod.MEMORY_FILE -> {
                ClaudeMethodMemoryFile.execute(workingDirectory, content, widget, ctx, permissionMode)
            }
            ClaudeIntegrationMethod.SESSION -> {
                executeSessionMethod(workingDirectory, content, widget, ctx, permissionMode)
            }
        }
    }

    private suspend fun executeSessionMethod(
        workingDirectory: java.nio.file.Path,
        content: String,
        widget: TerminalWidget,
        ctx: CliExecutionContext,
        permissionMode: String
    ) {
        log.debug("Using session method")

        // Try primary: manual session creation
        val sessionId = try {
            log.debug("Trying primary: manual session creation")
            ClaudeMethodManual.createSession(workingDirectory, content)
        } catch (e: Exception) {
            // Fallback: headless session creation
            log.warn("Primary method failed: ${e.message}")
            log.debug("Trying fallback: headless session creation")
            ClaudeMethodHeadless.createSession(workingDirectory, content)
        }

        // Update history index
        ClaudeCommon.addToHistoryIndex(
            sessionId = sessionId,
            cwd = workingDirectory,
            displayText = ClaudeCommon.truncateForDisplay(content, 100)
        )
        log.debug("History index updated")

        // Create lock file
        val lockFilePath = ClaudeCommon.resolveFile(
            workingDirectory,
            SESSION_LOCK_FILE
        )

        try {
            lockFilePath.toFile().writeText("")
            log.debug("Lock file created: $SESSION_LOCK_FILE")
        } catch (e: Exception) {
            log.warn("Failed to create lock file: ${e.message}")
        }

        // Build and execute command
        val claudeCommand = ClaudeCommon.buildClaudeCommand(
            permissionMode = permissionMode,
            shell = ctx.shell,
            lockFile = SESSION_LOCK_FILE,
            model = ctx.claudeModel?.name?.lowercase(),
            sessionId = sessionId,
            activationPrompt = "Let's continue"
        )

        log.debug("Sending command: $claudeCommand")

        // Execute in terminal (must be on EDT)
        withContext(Dispatchers.EDT) {
            widget.sendCommandToExecute(claudeCommand)
        }
    }

    /**
     * Map AI interaction mode to Claude permission mode
     */
    private fun mapModeToPermission(mode: AiInteractionMode): String {
        return when (mode) {
            AiInteractionMode.ASK -> "plan"
            AiInteractionMode.AGENT -> "acceptEdits"
        }
    }
}
