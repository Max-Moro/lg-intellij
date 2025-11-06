package lg.intellij.models

enum class ClaudeIntegrationMethod {
    MEMORY_FILE,
    SESSION;

    companion object {
        fun getAvailableMethods(): List<ClaudeMethodDescriptor> = listOf(
            ClaudeMethodDescriptor(
                MEMORY_FILE,
                "Memory File",
                "Stable method using CLAUDE.local.md (visible to all subagents)"
            ),
            ClaudeMethodDescriptor(
                SESSION,
                "Session",
                "Better isolation - content visible only to orchestrator, not subagents"
            )
        )
    }
}

data class ClaudeMethodDescriptor(
    val id: ClaudeIntegrationMethod,
    val label: String,
    val description: String
) {
    override fun toString(): String = label
}
