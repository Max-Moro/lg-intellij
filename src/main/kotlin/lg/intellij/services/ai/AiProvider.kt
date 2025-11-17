package lg.intellij.services.ai

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
     * @throws AiProviderException if sending failed
     */
    suspend fun send(content: String)

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
}

/**
 * Exception raised when sending to an AI provider fails.
 */
class AiProviderException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

