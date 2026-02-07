package lg.intellij.ai.providers.codexcli

/**
 * Codex reasoning effort levels.
 * Controls how much "thinking" the model does before responding.
 */
enum class CodexReasoningEffort {
    MINIMAL,
    LOW,
    MEDIUM,
    HIGH,
    XHIGH;

    companion object {
        fun getAvailableEfforts(): List<CodexReasoningEffortDescriptor> = listOf(
            CodexReasoningEffortDescriptor(MINIMAL, "Minimal", "Fastest, least thorough"),
            CodexReasoningEffortDescriptor(LOW, "Low", "Quick tasks"),
            CodexReasoningEffortDescriptor(MEDIUM, "Medium", "Balanced (default)"),
            CodexReasoningEffortDescriptor(HIGH, "High", "Complex tasks"),
            CodexReasoningEffortDescriptor(XHIGH, "Extra High", "Most thorough, slowest")
        )
    }
}

data class CodexReasoningEffortDescriptor(
    val id: CodexReasoningEffort,
    val label: String,
    val description: String
) {
    override fun toString(): String = label
}
