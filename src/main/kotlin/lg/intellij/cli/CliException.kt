package lg.intellij.cli

/**
 * Base exception for CLI-related errors.
 */
open class CliException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * CLI process exited with non-zero exit code.
 *
 * The message is automatically formatted from command, exit code, and stderr
 * for informative log output. Individual properties remain accessible
 * for structured error handling (e.g., user-facing notifications).
 *
 * @property exitCode Process exit code
 * @property stderr Error output from stderr
 * @property stdout Standard output (may contain partial data, e.g. JSON conflict report from init)
 * @property command Full CLI command string that was executed
 */
class CliExecutionException(
    val exitCode: Int,
    val stderr: String,
    val stdout: String = "",
    val command: String = ""
) : CliException(formatMessage(exitCode, stderr, command)) {

    companion object {
        /**
         * Formats error message for logging.
         * Preserves full Python stacktrace from stderr.
         */
        private fun formatMessage(exitCode: Int, stderr: String, command: String): String {
            val parts = mutableListOf<String>()
            if (command.isNotEmpty()) {
                parts.add("Command: $command")
            }
            parts.add("Exit code: $exitCode")
            val trimmed = stderr.trim()
            if (trimmed.isNotEmpty()) {
                parts.add(trimmed)
            }
            return parts.joinToString("\n")
        }
    }
}

/**
 * CLI process execution exceeded timeout.
 *
 * @property timeoutMs Timeout duration in milliseconds
 */
class CliTimeoutException(
    message: String,
    val timeoutMs: Long = 0
) : CliException(message)

/**
 * CLI executable not found in configured locations.
 *
 * @property silent If true, error should not trigger user notifications (subsequent failures after fatal error)
 */
class CliNotFoundException(
    message: String,
    val silent: Boolean = false
) : CliException(message)
