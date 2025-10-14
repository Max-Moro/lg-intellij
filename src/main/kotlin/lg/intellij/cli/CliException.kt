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
 */
class CliExecutionException(
    message: String,
    val exitCode: Int,
    val stderr: String
) : CliException(message)

/**
 * CLI process execution exceeded timeout.
 */
class CliTimeoutException(
    message: String
) : CliException(message)

/**
 * CLI executable not found in configured locations.
 */
class CliNotFoundException(
    message: String
) : CliException(message)

/**
 * Failed to parse CLI output (malformed JSON, unexpected format).
 */
class CliParseException(
    message: String,
    cause: Throwable? = null
) : CliException(message, cause)
