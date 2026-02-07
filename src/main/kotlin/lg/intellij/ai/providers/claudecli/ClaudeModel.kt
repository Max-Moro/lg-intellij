package lg.intellij.ai.providers.claudecli

enum class ClaudeModel {
    HAIKU,
    SONNET,
    OPUS;

    companion object {
        fun getAvailableModels(): List<ClaudeModelDescriptor> = listOf(
            ClaudeModelDescriptor(HAIKU, "Haiku", "Fast and cost-effective"),
            ClaudeModelDescriptor(SONNET, "Sonnet", "Balanced performance"),
            ClaudeModelDescriptor(OPUS, "Opus", "Most powerful")
        )
    }
}

data class ClaudeModelDescriptor(
    val id: ClaudeModel,
    val label: String,
    val description: String
) {
    override fun toString(): String = label
}
