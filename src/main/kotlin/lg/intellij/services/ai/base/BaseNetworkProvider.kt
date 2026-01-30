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
 * Base class for Network-based AI providers.
 *
 * Used for providers that work through HTTP API
 * (e.g., OpenAI API, Anthropic API).
 *
 * Key features:
 * - Secure API token storage via PasswordSafe
 * - HTTP requests with timeout
 * - Centralized network error handling
 */
@Suppress("unused") // Base class for future network-based AI providers
abstract class BaseNetworkProvider : AiProvider {
    
    private val log = logger<BaseNetworkProvider>()
    
    /**
     * API endpoint URL.
     */
    @Suppress("unused") // Part of base class API for derived implementations
    protected abstract val apiEndpoint: String

    /**
     * Key for storing token in PasswordSafe (e.g., "lg.openai.apiKey").
     */
    protected abstract val credentialKey: String

    /**
     * HTTP client for executing requests.
     */
    protected val httpClient: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()
    }

    /**
     * Checks API token presence.
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
     * Gets API token from PasswordSafe.
     *
     * @return API token
     * @throws AiProviderException if token not found
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
     * Saves API token to PasswordSafe.
     *
     * @param token API token to save
     */
    @Suppress("unused") // Part of base class API for configuration
    fun setApiToken(token: String) {
        PasswordSafe.instance.setPassword(
            com.intellij.credentialStore.CredentialAttributes(credentialKey),
            token
        )
    }
    
    /**
     * Sends content through the API.
     *
     * Retrieves token and delegates to sendToApi.
     */
    override suspend fun send(content: String, runs: String) {
        val token = getApiToken()

        sendToApi(content, token)
    }

    /**
     * Executes HTTP request with timeout.
     *
     * @param request HTTP request
     * @param timeoutSeconds Timeout in seconds
     * @return HTTP response
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
     * Sends content to a specific API.
     *
     * Implemented by subclasses for API-specific interaction logic.
     * Should handle request formation, sending, and response parsing.
     *
     * @param content Content to send
     * @param token API token
     */
    protected abstract suspend fun sendToApi(content: String, token: String)
}

