package lg.intellij.cli.models

import lg.intellij.cli.CliExecutionException
import lg.intellij.cli.CliNotFoundException
import lg.intellij.cli.CliTimeoutException

/**
 * Typed result of CLI command execution.
 * 
 * Provides structured error handling and avoids throwing exceptions
 * for expected failure scenarios (non-zero exit codes, timeouts).
 */
sealed class CliResult<out T> {
    /**
     * Successful execution with output data.
     */
    data class Success<T>(val data: T) : CliResult<T>()
    
    /**
     * CLI process exited with non-zero code.
     * 
     * @property exitCode Process exit code
     * @property stderr Error output from the process
     * @property stdout Standard output (may contain partial data)
     */
    data class Failure(
        val exitCode: Int,
        val stderr: String,
        val stdout: String = ""
    ) : CliResult<Nothing>()
    
    /**
     * Process execution exceeded timeout.
     * 
     * @property timeoutMs Timeout duration in milliseconds
     */
    data class Timeout(val timeoutMs: Long) : CliResult<Nothing>()
    
    /**
     * CLI executable not found.
     * 
     * @property message Error description
     */
    data class NotFound(val message: String) : CliResult<Nothing>()
    
    /**
     * Returns true if this result represents a successful execution.
     */
    fun isSuccess(): Boolean = this is Success
    
    /**
     * Returns the data if successful, or null otherwise.
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }
    
    /**
     * Returns the data if successful, or throws an exception.
     */
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Failure -> throw CliExecutionException(
            "CLI failed with exit code $exitCode",
            exitCode,
            stderr
        )
        is Timeout -> throw CliTimeoutException(
            "CLI execution timeout after ${timeoutMs}ms"
        )
        is NotFound -> throw CliNotFoundException(message)
    }
}
