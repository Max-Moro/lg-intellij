package lg.intellij.services.ai.providers.codexcli

import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.diagnostic.logger
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import lg.intellij.models.CodexReasoningEffort
import lg.intellij.models.ShellType
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * Codex session creation parameters.
 * Note: sandboxMode and approvalPolicy use defaults - actual values
 * are controlled via CLI args (runs) which are passed as opaque string.
 */
data class CodexSessionParams(
    val content: String,
    val cwd: Path,
    val shell: ShellType,
    val reasoningEffort: CodexReasoningEffort
)

// Default values for session file (actual behavior controlled by CLI args)
private const val DEFAULT_APPROVAL_POLICY = "on-request"
private const val DEFAULT_SANDBOX_MODE = "workspace-write"

/**
 * Creates Codex CLI sessions.
 */
object CodexSession {

    private val log = logger<CodexSession>()

    /**
     * Create a new Codex session with the given content
     */
    fun createSession(params: CodexSessionParams): String {
        log.debug("Creating Codex session with reasoning effort: ${params.reasoningEffort}")

        // 1. Generate session ID (UUID v7)
        val sessionId = CodexCommon.generateUuidV7()
        log.debug("Generated session ID: $sessionId")

        // 2. Get timestamps
        val now = Instant.now()
        val isoTimestamp = now.toString()
        val fileTimestamp = CodexCommon.formatFileTimestamp(now)

        // 3. Build session directory path
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = now.toEpochMilli()
        }
        val year = calendar.get(java.util.Calendar.YEAR)
        val month = String.format("%02d", calendar.get(java.util.Calendar.MONTH) + 1)
        val day = String.format("%02d", calendar.get(java.util.Calendar.DAY_OF_MONTH))

        val sessionDir = CodexCommon.getCodexHome()
            .resolve("sessions")
            .resolve(year.toString())
            .resolve(month)
            .resolve(day)

        // 4. Create directory if not exists
        sessionDir.createDirectories()

        // 5. Build session file path
        val fileName = "rollout-$fileTimestamp-$sessionId.jsonl"
        val sessionFilePath = sessionDir.resolve(fileName)
        log.debug("Session file: $sessionFilePath")

        // 6. Get CLI version
        val cliVersion = getCodexVersion()

        // 7. Build JSONL records (using defaults - actual behavior controlled by CLI args)
        val records = listOf(
            buildSessionMeta(sessionId, isoTimestamp, params.cwd.toString(), cliVersion),
            buildDeveloperMessage(isoTimestamp, DEFAULT_APPROVAL_POLICY, DEFAULT_SANDBOX_MODE),
            buildEnvironmentContext(isoTimestamp, params.cwd.toString(), params.shell),
            buildUserMessage(isoTimestamp, params.content),
            buildUserMessageEvent(isoTimestamp, params.content),
            buildTurnContext(isoTimestamp, params)
        )

        // 8. Write session file
        val jsonlContent = records.joinToString("\n") { it.toString() } + "\n"
        sessionFilePath.writeText(jsonlContent)
        log.debug("Session file written")

        // 9. Update history.jsonl
        CodexCommon.addToHistoryIndex(
            sessionId = sessionId,
            timestamp = now.toEpochMilli(),
            text = params.content
        )
        log.debug("History index updated")

        return sessionId
    }

    private fun getCodexVersion(): String {
        return try {
            val commandLine = CodexCommon.createCommandLine("codex", "--version")
            val output = ExecUtil.execAndGetOutput(commandLine, 3000)
            if (output.exitCode == 0) {
                val match = Regex("""v?([\d.]+)""").find(output.stdout)
                match?.groupValues?.get(1) ?: "0.88.0"
            } else "0.88.0"
        } catch (_: Exception) {
            "0.88.0"
        }
    }

    private fun buildSessionMeta(
        sessionId: String,
        timestamp: String,
        cwd: String,
        cliVersion: String
    ) = buildJsonObject {
        put("timestamp", timestamp)
        put("type", "session_meta")
        putJsonObject("payload") {
            put("id", sessionId)
            put("timestamp", timestamp)
            put("cwd", cwd)
            put("originator", "lg_intellij_plugin")
            put("cli_version", cliVersion)
            put("source", "cli")
        }
    }

    private fun buildDeveloperMessage(
        timestamp: String,
        approvalPolicy: String,
        sandboxMode: String
    ): kotlinx.serialization.json.JsonObject {
        val sandboxText = if (sandboxMode == "read-only") {
            "The sandbox only permits reading files. Network access is restricted."
        } else {
            "The sandbox permits writing to the workspace."
        }

        return buildJsonObject {
            put("timestamp", timestamp)
            put("type", "response_item")
            putJsonObject("payload") {
                put("type", "message")
                put("role", "developer")
                putJsonArray("content") {
                    add(buildJsonObject {
                        put("type", "input_text")
                        put("text", "<permissions instructions>Filesystem sandboxing defines which files can be read or written. `sandbox_mode` is `$sandboxMode`: $sandboxText `approval_policy` is `$approvalPolicy`: Commands will be run in the sandbox by default.</permissions instructions>")
                    })
                }
            }
        }
    }

    private fun buildEnvironmentContext(
        timestamp: String,
        cwd: String,
        shell: ShellType
    ) = buildJsonObject {
        put("timestamp", timestamp)
        put("type", "response_item")
        putJsonObject("payload") {
            put("type", "message")
            put("role", "user")
            putJsonArray("content") {
                add(buildJsonObject {
                    put("type", "input_text")
                    put("text", "<environment_context>\n  <cwd>$cwd</cwd>\n  <shell>${shell.name.lowercase()}</shell>\n</environment_context>")
                })
            }
        }
    }

    private fun buildUserMessage(timestamp: String, content: String) = buildJsonObject {
        put("timestamp", timestamp)
        put("type", "response_item")
        putJsonObject("payload") {
            put("type", "message")
            put("role", "user")
            putJsonArray("content") {
                add(buildJsonObject {
                    put("type", "input_text")
                    put("text", content)
                })
            }
        }
    }

    private fun buildUserMessageEvent(timestamp: String, content: String) = buildJsonObject {
        put("timestamp", timestamp)
        put("type", "event_msg")
        putJsonObject("payload") {
            put("type", "user_message")
            put("message", content)
            putJsonArray("images") {}
            putJsonArray("local_images") {}
            putJsonArray("text_elements") {}
        }
    }

    private fun buildTurnContext(timestamp: String, params: CodexSessionParams) = buildJsonObject {
        put("timestamp", timestamp)
        put("type", "turn_context")
        putJsonObject("payload") {
            put("cwd", params.cwd.toString())
            put("approval_policy", DEFAULT_APPROVAL_POLICY)
            putJsonObject("sandbox_policy") {
                put("type", DEFAULT_SANDBOX_MODE)
            }
            put("effort", params.reasoningEffort.name.lowercase())
            // Note: actual sandbox/approval behavior controlled by CLI args (runs)
        }
    }
}
