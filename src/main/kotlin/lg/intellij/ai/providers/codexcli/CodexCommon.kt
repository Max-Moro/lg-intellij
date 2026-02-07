package lg.intellij.ai.providers.codexcli

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import lg.intellij.models.ShellType
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*

object CodexCommon {

    /** Lock file for Codex CLI session detection */
    const val CODEX_SESSION_LOCK_FILE = ".codex-session.lock"

    /**
     * Get Codex home directory (~/.codex)
     */
    fun getCodexHome(): Path {
        val homeDir = System.getProperty("user.home")
        return Paths.get(homeDir, ".codex")
    }

    /**
     * Get working directory with scope
     */
    fun getWorkingDirectory(project: Project, scope: String): Path {
        val root = Paths.get(File(project.basePath ?: ".").absolutePath)
        return if (scope.isBlank()) root else root.resolve(scope)
    }

    /**
     * Resolve file path relative to working directory
     */
    fun resolveFile(workingDirectory: Path, fileName: String): Path {
        return workingDirectory.resolve(fileName)
    }

    /**
     * Create GeneralCommandLine with Windows PATH resolution support.
     */
    fun createCommandLine(command: String, vararg args: String): GeneralCommandLine {
        return if (System.getProperty("os.name").lowercase().contains("win")) {
            GeneralCommandLine("cmd", "/c", command, *args)
        } else {
            GeneralCommandLine(command, *args)
        }
    }

    /**
     * Truncate text for display
     */
    fun truncateForDisplay(text: String, maxLength: Int): String {
        return if (text.length <= maxLength) text
        else text.take(maxLength) + "..."
    }

    /**
     * Format timestamp for session filename (2026-01-22T08-15-11)
     */
    fun formatFileTimestamp(date: java.time.Instant): String {
        val iso = date.toString()
        // 2026-01-22T08:15:11.357Z -> 2026-01-22T08-15-11
        return iso.substring(0, 19).replace(":", "-")
    }

    /**
     * Generate UUID v7 (time-ordered)
     */
    fun generateUuidV7(): String {
        val now = System.currentTimeMillis()
        val bytes = ByteArray(16)

        // Timestamp (48 bits) - big endian
        bytes[0] = ((now shr 40) and 0xff).toByte()
        bytes[1] = ((now shr 32) and 0xff).toByte()
        bytes[2] = ((now shr 24) and 0xff).toByte()
        bytes[3] = ((now shr 16) and 0xff).toByte()
        bytes[4] = ((now shr 8) and 0xff).toByte()
        bytes[5] = (now and 0xff).toByte()

        // Random bits for remaining bytes
        val random = java.security.SecureRandom()
        val randomBytes = ByteArray(10)
        random.nextBytes(randomBytes)
        System.arraycopy(randomBytes, 0, bytes, 6, 10)

        // Version 7
        bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x70).toByte()

        // Variant (RFC 4122)
        bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()

        // Format as UUID string
        val hex = bytes.joinToString("") { String.format("%02x", it) }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
    }

    /**
     * Add entry to history.jsonl
     */
    fun addToHistoryIndex(
        sessionId: String,
        timestamp: Long,
        text: String
    ) {
        val historyEntry = buildJsonObject {
            put("session_id", sessionId)
            put("ts", timestamp / 1000)
            put("text", truncateForDisplay(text, 500))
        }

        val historyPath = getCodexHome().resolve("history.jsonl")
        val line = historyEntry.toString() + "\n"

        try {
            historyPath.appendText(line)
        } catch (_: java.nio.file.NoSuchFileException) {
            historyPath.parent?.createDirectories()
            historyPath.writeText(line)
        }
    }

    /**
     * Activation prompt to start agent immediately after session resume
     */
    private const val ACTIVATION_PROMPT = "Let's continue"

    /**
     * Build Codex CLI launch command with lock file cleanup.
     *
     * @param runs CLI arguments passed as-is (opaque string from mode configuration)
     * @param sessionId Session ID for resume
     * @param shell Shell type for cleanup command syntax
     * @param lockFile Lock file to remove on exit
     */
    fun buildCodexCommand(
        runs: String,
        sessionId: String,
        shell: ShellType,
        lockFile: String
    ): String {
        // runs is passed as-is (opaque string from mode configuration)
        val runsArg = if (runs.isNotBlank()) " $runs" else ""

        // Add activation prompt to start agent immediately
        val codexCmd = "codex$runsArg resume \"$sessionId\" \"$ACTIVATION_PROMPT\""

        // Add cleanup depending on shell
        return when (shell) {
            ShellType.POWERSHELL -> {
                "try { $codexCmd } finally { Remove-Item \"$lockFile\" -EA SilentlyContinue }"
            }
            ShellType.CMD -> {
                "$codexCmd & if exist \"$lockFile\" del /q \"$lockFile\""
            }
            else -> { // BASH, ZSH, SH
                "(trap \"rm -f \\\"$lockFile\\\"\" EXIT INT TERM HUP; $codexCmd)"
            }
        }
    }
}
