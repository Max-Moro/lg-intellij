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
 * @property exitCode Process exit code
 * @property stderr Error output from stderr
 * @property stdout Standard output (may contain partial data, e.g. JSON conflict report from init)
 */
class CliExecutionException(
    message: String,
    val exitCode: Int,
    val stderr: String,
    val stdout: String = ""
) : CliException(message)

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
