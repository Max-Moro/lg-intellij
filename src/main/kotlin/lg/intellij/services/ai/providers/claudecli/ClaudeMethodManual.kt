package lg.intellij.services.ai.providers.claudecli

import com.intellij.execution.util.ExecUtil
import java.nio.file.Path
import java.util.*
import kotlin.io.path.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * Manual session creation method.
 *
 * Advantages:
 * - Fast (no headless request)
 * - Independent of Claude Code state
 * - Full control
 *
 * Disadvantages:
 * - Potentially fragile if format changes
 */
object ClaudeMethodManual {

    /**
     * Create session manually by constructing JSONL file
     */
    fun createSession(
        workingDirectory: Path,
        content: String
    ): String {
        // Generate session ID
        val sessionId = UUID.randomUUID().toString()

        // Get Claude Code version
        val version = getClaudeVersion() ?: "2.0.30"

        // Get git branch
        val gitBranch = getGitBranch(workingDirectory.toString())

        // Create JSONL content
        val snapshotUuid = UUID.randomUUID().toString()
        val userMessageUuid = UUID.randomUUID().toString()
        val now = java.time.Instant.now().toString()

        val snapshot = buildJsonObject {
            put("type", "file-history-snapshot")
            put("messageId", snapshotUuid)
            putJsonObject("snapshot") {
                put("messageId", snapshotUuid)
                putJsonObject("trackedFileBackups") { }
                put("timestamp", now)
            }
            put("isSnapshotUpdate", false)
        }

        val userMessage = buildJsonObject {
            put("parentUuid", JsonPrimitive(null))
            put("isSidechain", false)
            put("userType", "external")
            put("cwd", workingDirectory.toString())
            put("sessionId", sessionId)
            put("version", version)
            put("gitBranch", gitBranch)
            put("type", "user")
            putJsonObject("message") {
                put("role", "user")
                put("content", content)
            }
            put("uuid", userMessageUuid)
            put("timestamp", now)
            putJsonObject("thinkingMetadata") {
                put("level", "high")
                put("disabled", false)
                putJsonArray("triggers") { }
            }
        }

        val jsonlContent = listOf(
            snapshot.toString(),
            userMessage.toString()
        ).joinToString("\n") + "\n"

        // Write session file
        val sessionFilePath = ClaudeCommon.getClaudeSessionPath(workingDirectory, sessionId)
        sessionFilePath.parent?.createDirectories()
        sessionFilePath.writeText(jsonlContent)

        return sessionId
    }

    private fun getClaudeVersion(): String? {
        return try {
            val commandLine = ClaudeCommon.createCommandLine("claude", "--version")
            val output = ExecUtil.execAndGetOutput(commandLine, 3000)
            if (output.exitCode == 0) {
                val match = Regex("""v?([\d.]+)""").find(output.stdout)
                match?.groupValues?.get(1)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun getGitBranch(cwd: String): String {
        return try {
            val commandLine = ClaudeCommon.createCommandLine("git", "branch", "--show-current")
                .withWorkDirectory(cwd)
            val output = ExecUtil.execAndGetOutput(commandLine, 3000)
            if (output.exitCode == 0) output.stdout.trim() else ""
        } catch (_: Exception) {
            ""
        }
    }
}
