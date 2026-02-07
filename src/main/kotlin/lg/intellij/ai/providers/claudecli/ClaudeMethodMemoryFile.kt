package lg.intellij.ai.providers.claudecli

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.terminal.ui.TerminalWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lg.intellij.ai.base.CliExecutionContext
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
     * ctx.runs is passed as-is to CLI (opaque string from mode configuration)
     *
     * @param workingDirectory Working directory path
     * @param content Content to send
     * @param widget Terminal widget
     * @param ctx CLI execution context (runs passed as-is)
     */
    suspend fun execute(
        workingDirectory: Path,
        content: String,
        widget: TerminalWidget,
        ctx: CliExecutionContext
    ) {
        log.debug("Using memory-file method")

        // Write content to CLAUDE.local.md
        val memoryFilePath = writeMemoryFile(workingDirectory, content)
        log.debug("Memory file written: $memoryFilePath")

        // Build command with cleanup - runs is passed as-is (opaque string)
        val claudeCommand = ClaudeCommon.buildClaudeCommand(
            runs = ctx.runs,
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
