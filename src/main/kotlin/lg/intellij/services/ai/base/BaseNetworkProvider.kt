package lg.intellij.services.ai.base

import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.diagnostic.logger
import lg.intellij.services.ai.AiProvider
import lg.intellij.services.ai.AiProviderException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Базовый класс для Network-based AI провайдеров.
 *
 * Используется для провайдеров, которые работают через HTTP API
 * (например, OpenAI API, Anthropic API).
 *
 * Основные возможности:
 * - Безопасное хранение API токенов через PasswordSafe
 * - HTTP запросы с таймаутом
 * - Централизованная обработка ошибок сети
 */
@Suppress("unused") // Base class for future network-based AI providers
abstract class BaseNetworkProvider : AiProvider {
    
    private val log = logger<BaseNetworkProvider>()
    
    /**
     * URL endpoint API (например, "https://api.openai.com/v1/chat/completions").
     */
    @Suppress("unused") // Part of base class API for derived implementations
    protected abstract val apiEndpoint: String
    
    /**
     * Ключ для хранения токена в PasswordSafe (например, "lg.openai.apiKey").
     */
    protected abstract val credentialKey: String
    
    /**
     * HTTP клиент для выполнения запросов.
     */
    protected val httpClient: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()
    }
    
    /**
     * Проверяет наличие API токена.
     */
    override suspend fun isAvailable(): Boolean {
        return try {
            val token = getApiToken()
            token.isNotBlank()
        } catch (e: Exception) {
            log.debug("API token not found for provider '$id'", e)
            false
        }
    }
    
    /**
     * Получает API токен из PasswordSafe.
     * 
     * @return API токен
     * @throws AiProviderException если токен не найден
     */
    protected fun getApiToken(): String {
        val token = PasswordSafe.instance.getPassword(
            com.intellij.credentialStore.CredentialAttributes(credentialKey)
        )
        
        if (token.isNullOrBlank()) {
            throw AiProviderException(
                "API token not configured for $name. Please set it in Settings."
            )
        }
        
        return token
    }
    
    /**
     * Сохраняет API токен в PasswordSafe.
     *
     * @param token API токен для сохранения
     */
    @Suppress("unused") // Part of base class API for configuration
    fun setApiToken(token: String) {
        PasswordSafe.instance.setPassword(
            com.intellij.credentialStore.CredentialAttributes(credentialKey),
            token
        )
    }
    
    /**
     * Отправляет контент через API.
     * 
     * Получает токен и делегирует sendToApi.
     */
    override suspend fun send(content: String) {
        val token = getApiToken()

        sendToApi(content, token)
    }
    
    /**
     * Выполняет HTTP запрос с таймаутом.
     *
     * @param request HTTP запрос
     * @param timeoutSeconds Таймаут в секундах
     * @return HTTP ответ
     */
    @Suppress("unused") // Part of base class API for HTTP operations
    protected fun executeRequest(
        request: HttpRequest,
        timeoutSeconds: Long = 30
    ): HttpResponse<String> {
        return httpClient.send(
            request,
            HttpResponse.BodyHandlers.ofString()
        )
    }
    
    /**
     * Отправляет контент в конкретный API.
     * 
     * Реализуется наследниками для специфичной логики взаимодействия с API.
     * Должен обрабатывать формирование запроса, отправку и разбор ответа.
     * 
     * @param content Контент для отправки
     * @param token API токен
     */
    protected abstract suspend fun sendToApi(content: String, token: String)
}

