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
import lg.intellij.services.ai.providers.claudecli.claudeCliSettings
import lg.intellij.services.ai.providers.codexcli.CodexCliProvider
import lg.intellij.services.ai.providers.codexcli.codexCliSettings

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

    /**
     * Registered provider settings modules: providerId -> module.
     */
    private val settingsModules = mutableMapOf<String, ProviderSettingsModule>()

    init {
        // Register built-in providers
        registerProvider(ClipboardProvider())
        registerProvider(JetBrainsAiProvider())
        registerProvider(GitHubCopilotProvider())
        registerProvider(JunieProvider())
        registerProvider(ClaudeCliProvider())
        registerProvider(CodexCliProvider())

        // Register provider settings modules
        registerSettingsModule(claudeCliSettings)
        registerSettingsModule(codexCliSettings)

        log.info("AI Integration Service initialized with ${providers.size} providers, ${settingsModules.size} settings modules")
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
     * Detects available providers and returns ProviderInfo list for UI display.
     * Delegates to [detectAvailableProviders] and enriches with provider metadata.
     * Sorted by priority (descending).
     */
    suspend fun detectAvailableProvidersInfo(): List<ProviderInfo> {
        val availableIds = detectAvailableProviders()
        return availableIds.mapNotNull { id ->
            providers[id]?.let { ProviderInfo(id, it.name, it.priority) }
        }
    }

    /**
     * Provider information for UI display.
     */
    data class ProviderInfo(
        val id: String,
        val name: String,
        val priority: Int
    )

    /**
     * Get all supported modes from all providers.
     * Used for generating ai-interaction.sec.yaml
     *
     * @return Map of modeId to Map of providerId to runs string
     */
    fun getAllSupportedModes(): Map<String, Map<String, String>> {
        val allModes = mutableMapOf<String, MutableMap<String, String>>()

        for ((providerId, provider) in providers) {
            val supportedModes = provider.getSupportedModes()
            for ((modeId, runs) in supportedModes) {
                allModes.getOrPut(modeId) { mutableMapOf() }[providerId] = runs
            }
        }

        return allModes
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
     * Sends content to the specified provider.
     *
     * @param providerId Provider ID
     * @param content Content to send
     * @param runs Provider-specific runs configuration string
     * @throws AiProviderException if provider not found or sending failed
     */
    suspend fun sendTo(providerId: String, content: String, runs: String) = withContext(Dispatchers.IO) {
        val provider = providers[providerId]
            ?: throw AiProviderException("Provider not found: $providerId")

        log.info("Sending content to provider: $providerId (runs: ${runs.ifBlank { "(empty)" }})")

        provider.send(content, runs)
        log.info("Successfully sent content to $providerId")
    }

    // ========== Provider Settings Modules ==========

    /**
     * Registers a provider settings module.
     *
     * Settings modules allow providers to define their own
     * UI contributions (fields, commands) without modifying
     * the central Control Panel code.
     *
     * @param module Settings module to register
     */
    fun registerSettingsModule(module: ProviderSettingsModule) {
        settingsModules[module.providerId] = module
        log.debug("Registered provider settings module: ${module.providerId}")
    }

    /**
     * Returns all registered settings modules.
     *
     * Used by the Control Panel to render dynamic provider settings.
     */
    fun getAllSettingsModules(): List<ProviderSettingsModule> {
        return settingsModules.values.toList()
    }

    companion object {
        /**
         * Returns the singleton instance of the service.
         */
        fun getInstance(): AiIntegrationService = service()
    }
}
