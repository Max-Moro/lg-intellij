package lg.intellij.models

/**
 * CLI execution context with scope and shell configuration.
 *
 * Used by BaseCliProvider to pass execution parameters to provider implementations.
 */
data class CliExecutionContext(
    /** Workspace scope (subdirectory) for CLI execution */
    val scope: String,

    /** Terminal shell type */
    val shell: ShellType,

    /** AI interaction mode */
    val mode: AiInteractionMode,

    /** Claude model (optional, only for Claude CLI provider) */
    val claudeModel: ClaudeModel? = null
)
