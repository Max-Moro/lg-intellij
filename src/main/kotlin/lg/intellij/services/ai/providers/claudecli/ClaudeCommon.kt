package lg.intellij.services.ai.providers.claudecli

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import lg.intellij.models.ShellType
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

object ClaudeCommon {

    /**
     * Create GeneralCommandLine with Windows PATH resolution support.
     *
     * On Windows, wraps command in `cmd /c` to properly resolve executables from PATH.
     * On Unix, creates command directly.
     *
     * @param command Command name (e.g., "claude", "git")
     * @param args Command arguments
     * @return Configured GeneralCommandLine (can be further customized with .withWorkDirectory(), etc.)
     */
    fun createCommandLine(command: String, vararg args: String): GeneralCommandLine {
        return if (System.getProperty("os.name").lowercase().contains("win")) {
            GeneralCommandLine("cmd", "/c", command, *args)
        } else {
            GeneralCommandLine(command, *args)
        }
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
     * Get Claude project directory (~/.claude/projects/<encoded-path>)
     */
    fun getClaudeProjectDir(workingDirectory: Path): Path {
        val encodedPath = encodeProjectPath(workingDirectory)
        val homeDir = System.getProperty("user.home")
        return Path(homeDir, ".claude", "projects", encodedPath)
    }

    /**
     * Encode project path for Claude Code format.
     *
     * Claude Code replaces path separators, colons, dots, and underscores with dashes.
     * The leading dash from Unix absolute paths is preserved.
     *
     * Examples:
     * - F:\workspace\project → F--workspace-project
     * - /home/user/project → -home-user-project
     * - F:\workspace\2026.01.02__Local_Project → F--workspace-2026-01-02--Local-Project
     */
    fun encodeProjectPath(projectPath: Path): String {
        val normalized = projectPath.normalize().toString()

        // Replace path separators, colons, dots, and underscores with dashes
        // Note: leading dash from Unix paths is intentionally preserved
        return normalized.replace(Regex("[/\\\\:._]"), "-")
    }

    /**
     * Get session file path
     */
    fun getClaudeSessionPath(workingDirectory: Path, sessionId: String): Path {
        val projectDir = getClaudeProjectDir(workingDirectory)
        return projectDir.resolve("$sessionId.jsonl")
    }

    /**
     * Add entry to history.jsonl for `claude -r` display
     */
    fun addToHistoryIndex(
        sessionId: String,
        cwd: Path,
        displayText: String
    ) {
        val historyEntry = buildJsonObject {
            put("display", displayText)
            putJsonObject("pastedContents") { }
            put("timestamp", System.currentTimeMillis())
            put("project", cwd.toString())
            put("sessionId", sessionId)
        }

        val homeDir = System.getProperty("user.home")
        val historyPath = Path(homeDir, ".claude", "history.jsonl")

        val json = historyEntry.toString()
        val line = "$json\n"

        try {
            historyPath.appendText(line)
        } catch (_: java.nio.file.NoSuchFileException) {
            historyPath.parent?.createDirectories()
            historyPath.writeText(line)
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
     * Build Claude command with cleanup lock-file.
     *
     * @param runs CLI arguments passed as-is (opaque string from mode configuration)
     * @param shell Shell type for cleanup command syntax
     * @param lockFile Lock file to remove on exit
     * @param model Optional model override
     * @param sessionId Session ID for resume (-r flag)
     * @param activationPrompt Prompt to send after launch
     */
    fun buildClaudeCommand(
        runs: String,
        shell: ShellType,
        lockFile: String,
        model: String?,
        sessionId: String?,
        activationPrompt: String?
    ): String {
        val modelArg = if (model != null) " --model $model" else ""

        // runs is passed as-is (opaque string from mode configuration)
        val runsArg = if (runs.isNotBlank()) " $runs" else ""
        var claudeCmd = "claude$runsArg$modelArg"

        if (sessionId != null) {
            claudeCmd += " -r \"$sessionId\""
        }
        if (activationPrompt != null) {
            claudeCmd += " \"$activationPrompt\""
        }

        require(sessionId != null || activationPrompt != null) {
            "At least one of sessionId or activationPrompt must be provided"
        }

        return when (shell) {
            ShellType.POWERSHELL -> {
                "try { $claudeCmd } finally { Remove-Item \"$lockFile\" -EA SilentlyContinue }"
            }
            ShellType.CMD -> {
                "$claudeCmd & if exist \"$lockFile\" del /q \"$lockFile\""
            }
            else -> { // BASH, ZSH, SH
                "(trap \"rm -f \\\"$lockFile\\\"\" EXIT INT TERM HUP; $claudeCmd)"
            }
        }
    }
}
