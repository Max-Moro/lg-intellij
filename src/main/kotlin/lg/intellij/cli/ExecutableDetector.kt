package lg.intellij.cli

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import java.io.File

/**
 * Utility for detecting executables with fallback to common user installation paths.
 *
 * Problem: On Linux, GUI applications (IntelliJ IDEA) don't inherit shell environment (~/.bashrc, ~/.zshrc).
 * This means ~/.local/bin and other user paths are NOT in PATH, even though EnvironmentUtil is used.
 *
 * Solution: Use Platform API first, then fallback to checking common user installation directories.
 *
 * Comparison:
 * - Windows: User paths (like %USERPROFILE%\.local\bin) are in system PATH → Platform API works ✅
 * - macOS: EnvironmentUtil loads shell environment → Platform API works ✅
 * - Linux: EnvironmentUtil does NOT load shell environment for GUI apps → Need fallback ⚠️
 */
object ExecutableDetector {

    private val log = logger<ExecutableDetector>()

    /**
     * Finds executable in PATH with fallback to common user installation paths.
     *
     * Search order:
     * 1. Platform API (works on Windows, macOS, and Linux when PATH is properly configured)
     * 2. Common user installation paths (fallback for Linux GUI apps)
     *
     * @param name Executable name (e.g., "claude", "pipx", "listing-generator")
     * @return File object if found, null otherwise
     */
    fun findExecutable(name: String): File? {
        // Strategy 1: Use Platform API (works in most cases)
        try {
            val result = PathEnvironmentVariableUtil.findExecutableInPathOnAnyOS(name)
            if (result != null) {
                log.debug("Found '$name' via Platform API: ${result.absolutePath}")
                return result
            }
        } catch (e: Exception) {
            log.debug("Platform API search for '$name' failed: ${e.message}")
        }

        // Strategy 2: Fallback to common user installation paths
        // This is needed for Linux GUI apps where ~/.local/bin is not in PATH
        val userPaths = getUserInstallationPaths()
        for (path in userPaths) {
            val executable = findInDirectory(path, name)
            if (executable != null) {
                log.debug("Found '$name' in user path: ${executable.absolutePath}")
                return executable
            }
        }

        log.debug("Executable '$name' not found")
        return null
    }

    /**
     * Returns list of common user installation paths based on OS.
     *
     * These are directories where users typically install CLI tools manually or via package managers like pipx.
     */
    private fun getUserInstallationPaths(): List<String> {
        val home = System.getProperty("user.home")

        return when {
            SystemInfo.isWindows -> listOf(
                "$home\\.local\\bin",
                "$home\\AppData\\Local\\Programs\\Python\\Python311\\Scripts",
                "$home\\AppData\\Local\\Programs\\Python\\Python312\\Scripts",
                "$home\\AppData\\Roaming\\Python\\Python311\\Scripts",
                "$home\\AppData\\Roaming\\Python\\Python312\\Scripts",
                "$home\\AppData\\Roaming\\npm"
            )
            SystemInfo.isMac -> listOf(
                "$home/.local/bin",
                "$home/bin",
                "/opt/homebrew/bin",
                "/usr/local/bin"
            )
            else -> listOf( // Linux and other Unix-like
                "$home/.local/bin",
                "$home/bin",
                "/usr/local/bin"
            )
        }
    }

    /**
     * Searches for executable in a specific directory.
     *
     * On Windows, checks for .exe, .cmd, .bat extensions.
     * On Unix, checks for exact name with execute permission.
     */
    private fun findInDirectory(dirPath: String, name: String): File? {
        val dir = File(dirPath)
        if (!dir.exists() || !dir.isDirectory) {
            return null
        }

        if (SystemInfo.isWindows) {
            // Windows: check common executable extensions
            val extensions = listOf("", ".exe", ".cmd", ".bat", ".com")
            for (ext in extensions) {
                val file = File(dir, "$name$ext")
                if (file.exists() && file.isFile) {
                    return file
                }
            }
        } else {
            // Unix: check exact name with execute permission
            val file = File(dir, name)
            if (file.exists() && file.isFile && file.canExecute()) {
                return file
            }
        }

        return null
    }
}
