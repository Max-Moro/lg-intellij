package lg.intellij.ai

/**
 * Information about provider-supported mode.
 * Used for generating ai-interaction.sec.yaml
 */
data class ProviderModeInfo(
    /** Mode identifier (ask, agent, plan) */
    val modeId: String,
    /** Value for runs field in YAML */
    val runs: String
)

/**
 * Interface for AI providers.
 *
 * Each provider represents a way to send generated content
 * to an AI system (clipboard, JetBrains AI, GitHub Copilot, Claude CLI, etc.)
 */
interface AiProvider {
    /**
     * Unique provider identifier (e.g., "clipboard", "jetbrains.ai").
     */
    val id: String

    /**
     * Human-readable provider name for UI display.
     */
    val name: String

    /**
     * Sends content to the AI system.
     *
     * @param content Generated content to send
     * @param runs Provider-specific run configuration string (opaque, interpreted by provider)
     * @throws AiProviderException if sending failed
     */
    suspend fun send(content: String, runs: String)

    /**
     * Checks provider availability in the current environment.
     *
     * For example:
     * - For extension-based: checks plugin presence and enablement
     * - For CLI-based: checks utility availability in PATH
     * - For API-based: checks API key availability
     *
     * @return true if provider is available for use
     */
    suspend fun isAvailable(): Boolean

    /**
     * Provider priority for automatic selection (0-100).
     * Higher = more preferred during auto-detection.
     *
     * Recommended values:
     * - clipboard: 10 (always available, but fallback)
     * - JetBrains AI: 90 (native, high priority)
     * - GitHub Copilot: 80 (popular)
     * - Claude CLI: 50
     */
    val priority: Int

    /**
     * Returns list of modes supported by this provider.
     * Used for generating ai-interaction.sec.yaml
     *
     * @return List of supported modes with their runs values
     */
    fun getSupportedModes(): List<ProviderModeInfo>
}

/**
 * Exception raised when sending to an AI provider fails.
 */
class AiProviderException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
