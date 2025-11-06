package lg.intellij.services.ai.providers.claudecli

import com.intellij.execution.util.ExecUtil
import java.nio.file.Path
import kotlinx.coroutines.delay
import kotlin.io.path.*

/**
 * Headless session creation method (fallback).
 *
 * Advantages:
 * - Guaranteed compatible format (Claude creates structure)
 * - Automatically adapts to format changes
 *
 * Disadvantages:
 * - Additional headless request (time cost)
 */
object ClaudeMethodHeadless {

    /**
     * Create session via headless request and content replacement
     */
    suspend fun createSession(
        workingDirectory: Path,
        content: String
    ): String {
        val cwd = workingDirectory
        val projectDir = ClaudeCommon.getClaudeProjectDir(workingDirectory)

        // Get files BEFORE headless call
        val filesBefore = try {
            projectDir.listDirectoryEntries().map { it.fileName.toString() }
        } catch (_: Exception) {
            emptyList()
        }

        // Execute headless request with marker
        val marker = "TEMP_PLACEHOLDER_FOR_REPLACEMENT"
        val commandLine = ClaudeCommon.createCommandLine("claude", "-p", marker)
            .withWorkDirectory(cwd.toString())

        val result = ExecUtil.execAndGetOutput(commandLine, 240000)
        if (result.exitCode != 0) {
            throw Exception("Headless call failed with code ${result.exitCode}")
        }

        // Wait for file creation
        delay(500)

        // Find new USER session file (exclude agent-*)
        val filesAfter = try {
            projectDir.listDirectoryEntries().map { it.fileName.toString() }
        } catch (_: Exception) {
            throw Exception("Failed to read project directory after headless call")
        }

        val newFiles = filesAfter.filter { fileName ->
            fileName !in filesBefore &&
            fileName.endsWith(".jsonl") &&
            !fileName.startsWith("agent-")
        }

        if (newFiles.isEmpty()) {
            throw Exception("No new user session file created after headless call")
        }

        val sessionFile = newFiles.first()
        val sessionId = sessionFile.removeSuffix(".jsonl")

        // Replace marker with real content
        val sessionFilePath = projectDir.resolve(sessionFile)
        val fileContent = sessionFilePath.readText()

        val escapedMarker = marker.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
        val escapedContent = content.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

        val pattern = Regex(
            """"content":"${Regex.escape(escapedMarker)}""""
        )

        val modifiedContent = fileContent.replace(pattern, """"content":"$escapedContent"""")
        sessionFilePath.writeText(modifiedContent)

        return sessionId
    }
}
