package lg.intellij.services.ai

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lg.intellij.services.ai.providers.ClipboardProvider
import lg.intellij.services.ai.providers.GitHubCopilotProvider
import lg.intellij.services.ai.providers.JetBrainsAiProvider
import lg.intellij.services.ai.providers.JunieProvider
import lg.intellij.services.ai.providers.claudecli.ClaudeCliProvider

/**
 * Центральный сервис для управления AI провайдерами.
 *
 * Предоставляет:
 * - Registry провайдеров
 * - Детекцию доступных провайдеров
 * - Отправку контента в выбранный провайдер
 * - Fallback на clipboard при ошибках
 *
 * Application-level service (singleton).
 */
@Service
class AiIntegrationService {

    private val log = logger<AiIntegrationService>()

    /**
     * Зарегистрированные провайдеры: id -> provider.
     */
    private val providers = mutableMapOf<String, AiProvider>()

    init {
        // Регистрация встроенных провайдеров
        registerProvider(ClipboardProvider())
        registerProvider(JetBrainsAiProvider())
        registerProvider(GitHubCopilotProvider())
        registerProvider(JunieProvider())
        registerProvider(ClaudeCliProvider())

        log.info("AI Integration Service initialized with ${providers.size} providers")
    }

    /**
     * Регистрирует провайдер.
     *
     * @param provider Провайдер для регистрации
     */
    fun registerProvider(provider: AiProvider) {
        providers[provider.id] = provider
        log.debug("Registered AI provider: ${provider.id} (priority: ${provider.priority})")
    }

    /**
     * Возвращает имя провайдера по ID.
     *
     * @return Имя провайдера или сам ID если провайдер не найден
     */
    fun getProviderName(id: String): String {
        return providers[id]?.name ?: id
    }

    /**
     * Детектирует доступные провайдеры в текущем окружении.
     *
     * Проверяет все зарегистрированные провайдеры и возвращает
     * список доступных, отсортированный по приоритету (убывание).
     *
     * @return Список ID доступных провайдеров (sorted by priority desc)
     */
    suspend fun detectAvailableProviders(): List<String> = withContext(Dispatchers.IO) {
        val available = mutableListOf<Pair<String, Int>>() // id to priority

        for ((id, provider) in providers) {
            try {
                if (provider.isAvailable()) {
                    available.add(id to provider.priority)
                    log.debug("Provider '$id' is available (priority: ${provider.priority})")
                } else {
                    log.debug("Provider '$id' is not available")
                }
            } catch (e: Exception) {
                log.warn("Failed to check availability of provider '$id'", e)
            }
        }

        // Сортировать по приоритету (убывание)
        available.sortByDescending { it.second }

        val result = available.map { it.first }
        log.info("Detected ${result.size} available providers: $result")

        result
    }

    /**
     * Детектирует лучший провайдер (с наивысшим приоритетом).
     *
     * @return ID лучшего провайдера (по умолчанию "clipboard" если ничего не найдено)
     */
    suspend fun detectBestProvider(): String {
        val available = detectAvailableProviders()

        return if (available.isNotEmpty()) {
            available.first() // Уже отсортировано по приоритету
        } else {
            log.warn("No available providers detected, falling back to clipboard")
            "clipboard"
        }
    }

    /**
     * Resolves effective provider ID.
     *
     * If providerId is blank/null → auto-detect best.
     * Otherwise → use as is.
     *
     * @param providerId Provider ID from settings (may be blank)
     * @return Resolved provider ID
     */
    suspend fun resolveProvider(providerId: String?): String {
        return if (providerId.isNullOrBlank()) {
            detectBestProvider()
        } else {
            providerId
        }
    }

    /**
     * Отправляет контент в указанный провайдер.
     *
     * @param providerId ID провайдера
     * @param content Контент для отправки
     * @throws AiProviderException если провайдер не найден или отправка не удалась
     */
    suspend fun sendTo(providerId: String, content: String) = withContext(Dispatchers.IO) {
        val provider = providers[providerId]
            ?: throw AiProviderException("Provider not found: $providerId")

        log.info("Sending content to provider: $providerId")

        provider.send(content)
        log.info("Successfully sent content to $providerId")
    }

    companion object {
        /**
         * Возвращает singleton instance сервиса.
         */
        fun getInstance(): AiIntegrationService = service()
    }
}

