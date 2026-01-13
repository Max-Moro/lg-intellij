package lg.intellij.cli

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import lg.intellij.models.CliResult
import lg.intellij.services.LgErrorReportingService
import lg.intellij.services.LgInitService

/**
 * Utility for generalized processing of CLI command execution results.
 *
 * Implements DRY pattern for uniform error handling:
 * - CliResult.Success -> process successful result
 * - CliResult.Failure -> log and report error
 * - CliResult.Timeout -> log and report timeout
 * - CliResult.NotFound -> log and report CLI not found
 *
 * Usage example:
 * ```kotlin
 * val result = cliExecutor.execute(args = listOf("render", "sec:all"))
 * val content = result.handleWith(
 *     project = project,
 *     operationName = "Listing Generation",
 *     logger = LOG
 * ) { data ->
 *     // Process successful result
 *     data
 * }
 * ```
 */
object CliResultHandler {
    
    /**
     * Processes CLI command execution result with uniform error handling.
     *
     * @param T data type in CliResult.Success
     * @param R return result type
     * @param project project for error reports
     * @param operationName operation name for logs and notifications (e.g., "Listing Generation")
     * @param logger logger for message recording
     * @param errorReporting service for error reports (default - singleton)
     * @param onSuccess successful result handler, receives CliResult.Success and returns value of type R or null
     * @return processing result or null on error (user already notified)
     */
    fun <T, R> handle(
        result: CliResult<T>,
        project: Project,
        operationName: String,
        logger: Logger,
        errorReporting: LgErrorReportingService = LgErrorReportingService.getInstance(),
        onSuccess: (CliResult.Success<T>) -> R?
    ): R? {
        return when (result) {
            is CliResult.Success -> {
                try {
                    onSuccess(result)
                } catch (e: Exception) {
                    logger.error("$operationName: failed to process success result", e)
                    null
                }
            }
            
            is CliResult.Failure -> {
                // Suppress errors for uninitialized projects (lg-cfg/ doesn't exist yet)
                val initService = project.service<LgInitService>()
                if (!initService.isInitialized()) {
                    logger.debug("$operationName failed (project not initialized): exit code ${result.exitCode}")
                    null
                } else {
                    logger.warn("$operationName failed: exit code ${result.exitCode}")
                    errorReporting.reportCliFailure(project, operationName, result)
                    null
                }
            }
            
            is CliResult.Timeout -> {
                logger.warn("$operationName timeout")
                errorReporting.reportTimeout(project, operationName, result.timeoutMs)
                null
            }
            
            is CliResult.NotFound -> {
                logger.warn("$operationName: CLI not found - ${result.message}")
                errorReporting.reportCliNotFound(project, operationName, result.message)
                null
            }

            is CliResult.Unavailable -> {
                // Silent failure - no user notification
                // This happens when first CLI resolution failed and subsequent parallel calls
                // encounter the cached fatal error
                logger.debug("$operationName: CLI unavailable (silent failure after previous fatal error)")
                null
            }
        }
    }
    
    /**
     * Processes result with additional fallback value on error.
     *
     * @param T data type in CliResult.Success
     * @param R return result type
     * @param project project for error reports
     * @param operationName operation name for logs and notifications
     * @param logger logger for message recording
     * @param fallback value returned on any error
     * @param errorReporting service for error reports (default - singleton)
     * @param onSuccess successful result handler
     * @return processing result or fallback on error
     */
    fun <T, R> handleWithFallback(
        result: CliResult<T>,
        project: Project,
        operationName: String,
        logger: Logger,
        fallback: R,
        errorReporting: LgErrorReportingService = LgErrorReportingService.getInstance(),
        onSuccess: (CliResult.Success<T>) -> R
    ): R {
        return handle(result, project, operationName, logger, errorReporting, onSuccess) ?: fallback
    }
}

/**
 * Extension function for more convenient usage of result handler.
 *
 * Example:
 * ```kotlin
 * val content = result.handleWith(
 *     project = project,
 *     operationName = "Listing Generation",
 *     logger = LOG
 * ) { success -> success.data }
 * ```
 */
fun <T, R> CliResult<T>.handleWith(
    project: Project,
    operationName: String,
    logger: Logger,
    errorReporting: LgErrorReportingService = LgErrorReportingService.getInstance(),
    onSuccess: (CliResult.Success<T>) -> R?
): R? = CliResultHandler.handle(result = this, project, operationName, logger, errorReporting, onSuccess)

/**
 * Extension function with fallback value.
 *
 * Example:
 * ```kotlin
 * val sections = result.handleWithFallback(
 *     project = project,
 *     operationName = "Loading sections",
 *     logger = LOG,
 *     fallback = emptyList()
 * ) { success -> json.decodeFromString<SectionsListSchema>(success.data).sections }
 * ```
 */
fun <T, R> CliResult<T>.handleWithFallback(
    project: Project,
    operationName: String,
    logger: Logger,
    fallback: R,
    errorReporting: LgErrorReportingService = LgErrorReportingService.getInstance(),
    onSuccess: (CliResult.Success<T>) -> R
): R = CliResultHandler.handleWithFallback(result = this, project, operationName, logger, fallback, errorReporting, onSuccess)

