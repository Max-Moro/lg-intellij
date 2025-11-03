package lg.intellij.services.ai.base

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.diagnostic.logger
import lg.intellij.services.ai.AiProvider
import java.io.File
import java.nio.file.Files

/**
 * Базовый класс для CLI-based AI провайдеров.
 *
 * Используется для провайдеров, которые работают через CLI утилиты
 * (например, Claude CLI).
 *
 * Основные возможности:
 * - Проверка наличия CLI команды в PATH
 * - Создание временных файлов с контентом
 * - Выполнение команд с контентом
 */
@Suppress("unused") // Base class for future CLI-based AI providers
abstract class BaseCliProvider : AiProvider {
    
    private val log = logger<BaseCliProvider>()
    
    /**
     * Имя CLI команды (например, "claude", "aichat").
     */
    protected abstract val cliCommand: String
    
    /**
     * Проверяет доступность CLI команды через which/where.
     */
    override suspend fun isAvailable(): Boolean {
        return try {
            val checkCommand = if (System.getProperty("os.name").startsWith("Windows")) {
                "where"
            } else {
                "which"
            }
            
            val commandLine = GeneralCommandLine(checkCommand, cliCommand)
            val output = ExecUtil.execAndGetOutput(commandLine, 5000)
            
            output.exitCode == 0
        } catch (e: Exception) {
            log.debug("CLI command '$cliCommand' not found", e)
            false
        }
    }
    
    /**
     * Отправляет контент через CLI.
     * 
     * Создает временный файл с контентом и вызывает executeCommand.
     */
    override suspend fun send(content: String) {
        val tempFile = createTempFile(content)

        try {
            executeCommand(tempFile.absolutePath)
        } finally {
            // Удалить временный файл
            tempFile.delete()
        }
    }
    
    /**
     * Создает временный файл с контентом.
     * 
     * @param content Контент для записи
     * @return Созданный временный файл
     */
    protected fun createTempFile(content: String): File {
        val tempFile = Files.createTempFile("lg-ai-", ".md").toFile()
        tempFile.writeText(content)
        return tempFile
    }
    
    /**
     * Выполняет CLI команду с путем к файлу контента.
     * 
     * Реализуется наследниками для специфичной логики запуска.
     * 
     * @param contentFilePath Абсолютный путь к временному файлу с контентом
     */
    protected abstract fun executeCommand(contentFilePath: String)
}

