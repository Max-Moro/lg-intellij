package lg.intellij.models

enum class ShellType {
    BASH,
    ZSH,
    SH,
    POWERSHELL,
    CMD;

    companion object {
        fun getDefault(): ShellType {
            val os = System.getProperty("os.name").lowercase()
            return when {
                os.contains("win") -> POWERSHELL
                os.contains("mac") -> ZSH
                else -> BASH
            }
        }

        fun getAvailableShells(): List<ShellDescriptor> {
            val os = System.getProperty("os.name").lowercase()
            return when {
                os.contains("win") -> listOf(
                    ShellDescriptor(POWERSHELL, "PowerShell"),
                    ShellDescriptor(CMD, "Command Prompt"),
                    ShellDescriptor(BASH, "Bash (WSL/Git Bash)")
                )
                os.contains("mac") -> listOf(
                    ShellDescriptor(ZSH, "Zsh"),
                    ShellDescriptor(BASH, "Bash")
                )
                else -> listOf(
                    ShellDescriptor(BASH, "Bash"),
                    ShellDescriptor(ZSH, "Zsh"),
                    ShellDescriptor(SH, "Sh")
                )
            }
        }
    }
}

data class ShellDescriptor(
    val id: ShellType,
    val label: String
) {
    override fun toString(): String = label
}
