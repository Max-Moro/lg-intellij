package lg.intellij.models

/**
 * Типизированный режим AI-взаимодействия.
 * 
 * Соответствует набору режимов `ai-interaction` из lg-cfg/modes.yaml:
 * - ask: Базовый режим вопрос-ответ
 * - agent: Режим с инструментами и агентными возможностями
 * 
 * Используется для унификации поведения Extension AI-провайдеров.
 */
enum class AiInteractionMode {
    /**
     * Режим "Спросить" — базовый вопрос-ответ.
     * 
     * Соответствует:
     * - JetBrains AI: Chat (SimpleChat)
     * - GitHub Copilot: Ask mode
     * - Junie: IssueType.CHAT
     */
    ASK,
    
    /**
     * Режим "Агентная работа" — с инструментами.
     * 
     * Соответствует:
     * - JetBrains AI: Quick Edit (SmartChat)
     * - GitHub Copilot: Agent mode
     * - Junie: IssueType.ISSUE
     */
    AGENT
}

