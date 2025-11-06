package lg.intellij.services.ai.providers.claudecli

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.terminal.ui.TerminalWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lg.intellij.models.CliExecutionContext
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * Memory File integration method for Claude CLI.
 *
 * Advantages:
 * - Stable, battle-tested
 * - Simple implementation
 * - Fast (no additional requests)
 *
 * Disadvantages:
 * - CLAUDE.local.md visible to all subagents (pollutes context)
 */
object ClaudeMethodMemoryFile {

    private val log = logger<ClaudeMethodMemoryFile>()

    /** Memory file name */
    const val CLAUDE_LOCAL_FILE = "CLAUDE.local.md"

    /** Activation prompt for Claude Code */
    private const val ACTIVATION_PROMPT =
        "Process the context from CLAUDE.local.md and complete the task specified there. " +
        "Communicate with the user in the language that is predominantly used in the Memory files section."

    /**
     * Execute Memory File method: write CLAUDE.local.md and launch Claude Code.
     *
     * This is the main entry point for Memory File integration method.
     * Performs:
     * 1. Write content to CLAUDE.local.md
     * 2. Build Claude command with cleanup
     * 3. Execute command in terminal
     *
     * @param workingDirectory Working directory path
     * @param content Content to send
     * @param widget Terminal widget
     * @param ctx CLI execution context
     * @param permissionMode Claude permission mode (plan/acceptEdits)
     */
    suspend fun execute(
        workingDirectory: Path,
        content: String,
        widget: TerminalWidget,
        ctx: CliExecutionContext,
        permissionMode: String
    ) {
        log.debug("Using memory-file method")

        // Write content to CLAUDE.local.md
        val memoryFilePath = writeMemoryFile(workingDirectory, content)
        log.debug("Memory file written: $memoryFilePath")

        // Build command with cleanup
        val claudeCommand = ClaudeCommon.buildClaudeCommand(
            permissionMode = permissionMode,
            shell = ctx.shell,
            lockFile = CLAUDE_LOCAL_FILE,
            model = ctx.claudeModel?.name?.lowercase(),
            sessionId = null,
            activationPrompt = ACTIVATION_PROMPT
        )

        log.debug("Sending command: $claudeCommand")

        // Execute in terminal (must be on EDT)
        withContext(Dispatchers.EDT) {
            widget.sendCommandToExecute(claudeCommand)
        }
    }

    /**
     * Write content to CLAUDE.local.md
     *
     * @param workingDirectory Working directory path
     * @param content Content to write
     * @return Path to created file
     */
    private fun writeMemoryFile(
        workingDirectory: Path,
        content: String
    ): String {
        val claudeLocalPath = ClaudeCommon.resolveFile(
            workingDirectory,
            CLAUDE_LOCAL_FILE
        )

        try {
            claudeLocalPath.parent?.createDirectories()
            claudeLocalPath.writeText(content)
            log.debug("Memory file created: $claudeLocalPath")
            return claudeLocalPath.toString()
        } catch (e: Exception) {
            log.error("Failed to write memory file: ${e.message}", e)
            try {
                claudeLocalPath.toFile().delete()
            } catch (_: Exception) {}
            throw Exception("Failed to write memory file: ${e.message}", e)
        }
    }
}
