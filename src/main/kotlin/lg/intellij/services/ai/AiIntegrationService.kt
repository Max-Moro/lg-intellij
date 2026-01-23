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
import lg.intellij.services.ai.providers.codexcli.CodexCliProvider

/**
 * Central service for managing AI providers.
 *
 * Provides:
 * - Provider registry
 * - Detection of available providers
 * - Sending content to selected provider
 * - Fallback to clipboard on errors
 *
 * Application-level service (singleton).
 */
@Service
class AiIntegrationService {

    private val log = logger<AiIntegrationService>()

    /**
     * Registered providers: id -> provider.
     */
    private val providers = mutableMapOf<String, AiProvider>()

    init {
        // Register built-in providers
        registerProvider(ClipboardProvider())
        registerProvider(JetBrainsAiProvider())
        registerProvider(GitHubCopilotProvider())
        registerProvider(JunieProvider())
        registerProvider(ClaudeCliProvider())
        registerProvider(CodexCliProvider())

        log.info("AI Integration Service initialized with ${providers.size} providers")
    }

    /**
     * Registers a provider.
     *
     * @param provider Provider to register
     */
    fun registerProvider(provider: AiProvider) {
        providers[provider.id] = provider
        log.debug("Registered AI provider: ${provider.id} (priority: ${provider.priority})")
    }

    /**
     * Returns the provider name by ID.
     *
     * @return Provider name or the ID itself if provider is not found
     */
    fun getProviderName(id: String): String {
        return providers[id]?.name ?: id
    }

    /**
     * Detects available providers in the current environment.
     *
     * Checks all registered providers and returns
     * list of available ones sorted by priority (descending).
     *
     * @return List of available provider IDs (sorted by priority desc)
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

        // Sort by priority (descending)
        available.sortByDescending { it.second }

        val result = available.map { it.first }
        log.info("Detected ${result.size} available providers: $result")

        result
    }

    /**
     * Detects the best provider (with highest priority).
     *
     * @return ID of the best provider (defaults to "clipboard" if none found)
     */
    suspend fun detectBestProvider(): String {
        val available = detectAvailableProviders()

        return if (available.isNotEmpty()) {
            available.first() // Already sorted by priority
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
     * Sends content to the specified provider.
     *
     * @param providerId Provider ID
     * @param content Content to send
     * @throws AiProviderException if provider not found or sending failed
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
         * Returns the singleton instance of the service.
         */
        fun getInstance(): AiIntegrationService = service()
    }
}

