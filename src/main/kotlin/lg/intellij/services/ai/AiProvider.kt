package lg.intellij.services.ai

/**
 * Интерфейс для AI провайдеров.
 * 
 * Каждый провайдер представляет способ отправки сгенерированного контента
 * в AI-систему (clipboard, JetBrains AI, GitHub Copilot, Claude CLI и т.д.)
 */
interface AiProvider {
    /**
     * Уникальный идентификатор провайдера (например, "clipboard", "jetbrains.ai").
     */
    val id: String
    
    /**
     * Человекочитаемое имя провайдера для отображения в UI.
     */
    val name: String
    
    /**
     * Отправляет контент в AI-систему.
     * 
     * @param content Сгенерированный контент для отправки
     * @throws AiProviderException если отправка не удалась
     */
    suspend fun send(content: String)
    
    /**
     * Проверяет доступность провайдера в текущем окружении.
     * 
     * Например:
     * - Для extension-based: проверка наличия и активности плагина
     * - Для CLI-based: проверка наличия утилиты в PATH
     * - Для API-based: проверка наличия API ключа
     * 
     * @return true если провайдер доступен для использования
     */
    suspend fun isAvailable(): Boolean
    
    /**
     * Приоритет провайдера для автоматического выбора (0-100).
     * Выше = предпочтительнее при auto-detection.
     * 
     * Рекомендуемые значения:
     * - clipboard: 10 (всегда доступен, но fallback)
     * - JetBrains AI: 90 (родной, высокий приоритет)
     * - GitHub Copilot: 80 (популярный)
     * - Claude CLI: 50
     */
    val priority: Int
}

/**
 * Исключение при ошибке отправки в AI провайдер.
 */
class AiProviderException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

