package lg.intellij.cli

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import lg.intellij.models.CliResult
import lg.intellij.services.LgErrorReportingService

/**
 * Утилита для обобщенной обработки результатов выполнения CLI команд.
 * 
 * Реализует паттерн DRY для единообразной обработки ошибок:
 * - CliResult.Success -> обработка успешного результата
 * - CliResult.Failure -> логирование и отчет об ошибке
 * - CliResult.Timeout -> логирование и отчет о таймауте
 * - CliResult.NotFound -> логирование и отчет об отсутствии CLI
 * 
 * Пример использования:
 * ```kotlin
 * val result = cliExecutor.execute(args = listOf("render", "sec:all"))
 * val content = result.handleWith(
 *     project = project,
 *     operationName = "Listing Generation",
 *     logger = LOG
 * ) { data ->
 *     // Обработка успешного результата
 *     data
 * }
 * ```
 */
object CliResultHandler {
    
    /**
     * Обрабатывает результат выполнения CLI команды с единообразной обработкой ошибок.
     * 
     * @param T тип данных в CliResult.Success
     * @param R тип возвращаемого результата
     * @param project проект для отчетов об ошибках
     * @param operationName название операции для логов и уведомлений (например, "Listing Generation")
     * @param logger логгер для записи сообщений
     * @param errorReporting сервис для отчетов об ошибках (по умолчанию - singleton)
     * @param onSuccess обработчик успешного результата, получает CliResult.Success и возвращает значение типа R или null
     * @return результат обработки или null при ошибке (пользователь уже уведомлен)
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
                logger.warn("$operationName failed: exit code ${result.exitCode}")
                errorReporting.reportCliFailure(project, operationName, result)
                null
            }
            
            is CliResult.Timeout -> {
                logger.warn("$operationName timeout")
                errorReporting.reportTimeout(project, operationName, result.timeoutMs)
                null
            }
            
            is CliResult.NotFound -> {
                logger.warn("$operationName: CLI not found")
                errorReporting.reportCliNotFound(project, operationName)
                null
            }
        }
    }
    
    /**
     * Обрабатывает результат с дополнительным fallback-значением при ошибке.
     * 
     * @param T тип данных в CliResult.Success
     * @param R тип возвращаемого результата
     * @param project проект для отчетов об ошибках
     * @param operationName название операции для логов и уведомлений
     * @param logger логгер для записи сообщений
     * @param fallback значение, возвращаемое при любой ошибке
     * @param errorReporting сервис для отчетов об ошибках (по умолчанию - singleton)
     * @param onSuccess обработчик успешного результата
     * @return результат обработки или fallback при ошибке
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
 * Extension-функция для более удобного использования обработчика результатов.
 * 
 * Пример:
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
 * Extension-функция с fallback-значением.
 * 
 * Пример:
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

