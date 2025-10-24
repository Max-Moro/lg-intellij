package lg.intellij.services.ai.base

import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.diagnostic.logger
import lg.intellij.services.ai.AiProvider
import lg.intellij.services.ai.AiProviderException
import java.net.URI
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
abstract class BaseNetworkProvider : AiProvider {
    
    private val LOG = logger<BaseNetworkProvider>()
    
    /**
     * URL endpoint API (например, "https://api.openai.com/v1/chat/completions").
     */
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
            LOG.debug("API token not found for provider '$id'", e)
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

