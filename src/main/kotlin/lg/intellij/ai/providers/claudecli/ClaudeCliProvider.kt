package lg.intellij.ai.providers.claudecli

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.terminal.ui.TerminalWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lg.intellij.cli.ExecutableDetector
import lg.intellij.ai.ProviderModeInfo
import lg.intellij.ai.base.BaseCliProvider
import lg.intellij.ai.base.CliExecutionContext
import lg.intellij.statepce.PCEStateStore
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

    override val id = "com.anthropic.claude.cli"
    override val name = "Claude CLI"
    override val priority = 50

    companion object {
        private const val SESSION_LOCK_FILE = ".claude-session.lock"

        private const val SESSION_ACTIVE_MESSAGE =
            "Claude CLI session is still active. Please run \"/exit\" in the terminal to complete the current session before starting a new one."
    }

    override suspend fun isAvailable(): Boolean {
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
        val workingDirectory = ClaudeCommon.getWorkingDirectory(project, ctx.scope)
        val method = resolveIntegrationMethod(project)

        val lockFile = when (method) {
            ClaudeIntegrationMethod.MEMORY_FILE -> ClaudeMethodMemoryFile.CLAUDE_LOCAL_FILE
            ClaudeIntegrationMethod.SESSION -> SESSION_LOCK_FILE
        }

        return checkLockFile(workingDirectory, lockFile, SESSION_ACTIVE_MESSAGE)
    }

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
        val method = resolveIntegrationMethod(project)

        // ctx.runs is passed as-is to CLI (opaque string from mode configuration)
        when (method) {
            ClaudeIntegrationMethod.MEMORY_FILE -> {
                ClaudeMethodMemoryFile.execute(workingDirectory, content, widget, ctx)
            }
            ClaudeIntegrationMethod.SESSION -> {
                executeSessionMethod(workingDirectory, content, widget, ctx)
            }
        }
    }

    private suspend fun executeSessionMethod(
        workingDirectory: java.nio.file.Path,
        content: String,
        widget: TerminalWidget,
        ctx: CliExecutionContext
    ) {
        log.debug("Using session method")

        val sessionId = try {
            log.debug("Trying primary: manual session creation")
            ClaudeMethodManual.createSession(workingDirectory, content)
        } catch (e: Exception) {
            log.warn("Primary method failed: ${e.message}")
            log.debug("Trying fallback: headless session creation")
            ClaudeMethodHeadless.createSession(workingDirectory, content)
        }

        ClaudeCommon.addToHistoryIndex(
            sessionId = sessionId,
            cwd = workingDirectory,
            displayText = ClaudeCommon.truncateForDisplay(content, 100)
        )
        log.debug("History index updated")

        val lockFilePath = ClaudeCommon.resolveFile(workingDirectory, SESSION_LOCK_FILE)

        try {
            lockFilePath.toFile().writeText("")
            log.debug("Lock file created: $SESSION_LOCK_FILE")
        } catch (e: Exception) {
            log.warn("Failed to create lock file: ${e.message}")
        }

        // ctx.runs is passed as-is (opaque string from mode configuration)
        val claudeCommand = ClaudeCommon.buildClaudeCommand(
            runs = ctx.runs,
            shell = ctx.shell,
            lockFile = SESSION_LOCK_FILE,
            model = ctx.claudeModel?.name?.lowercase(),
            sessionId = sessionId,
            activationPrompt = "Let's continue"
        )

        log.debug("Sending command: $claudeCommand")

        withContext(Dispatchers.EDT) {
            widget.sendCommandToExecute(claudeCommand)
        }
    }

    /**
     * Resolves integration method from PCEStateStore provider settings.
     */
    private fun resolveIntegrationMethod(project: Project): ClaudeIntegrationMethod {
        val store = PCEStateStore.getInstance(project)
        val methodStr = store.getProviderSetting(id, "method")
        return methodStr?.let { runCatching { ClaudeIntegrationMethod.valueOf(it) }.getOrNull() }
            ?: ClaudeIntegrationMethod.SESSION
    }

    override fun getSupportedModes(): List<ProviderModeInfo> {
        return listOf(
            ProviderModeInfo("ask", "--permission-mode default"),
            ProviderModeInfo("agent", "--permission-mode acceptEdits"),
            ProviderModeInfo("plan", "--permission-mode plan")
        )
    }
}
