package lg.intellij.models

/**
 * Typed AI interaction mode.
 *
 * Corresponds to the `ai-interaction` mode set from lg-cfg/modes.yaml:
 * - ask: Basic question-answer mode
 * - agent: Mode with tools and agent capabilities
 *
 * Used to unify behavior of Extension AI providers.
 */
enum class AiInteractionMode {
    /**
     * "Ask" mode — basic question-answer.
     *
     * Corresponds to:
     * - JetBrains AI: Chat (SimpleChat)
     * - GitHub Copilot: Ask mode
     * - Junie: IssueType.CHAT
     */
    ASK,

    /**
     * "Agent" mode — with tools.
     *
     * Corresponds to:
     * - JetBrains AI: Quick Edit (SmartChat)
     * - GitHub Copilot: Agent mode
     * - Junie: IssueType.ISSUE
     */
    AGENT
}

