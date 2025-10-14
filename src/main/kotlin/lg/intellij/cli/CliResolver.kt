package lg.intellij.cli

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import lg.intellij.services.state.LgSettingsService
import java.io.File

/**
 * Resolves the path to listing-generator CLI executable.
 * 
 * Resolution strategies (in order):
 * 1. Explicit path from Settings (cliPath)
 * 2. Search in system PATH for "listing-generator"
 * 3. Try Python module invocation (python -m lg.cli) if interpreter configured
 * 4. Fallback to "python -m lg.cli" with auto-detected Python
 * 
 * Equivalent to CliResolver.ts from VS Code extension.
 */
@Service(Service.Level.APP)
class CliResolver {
    
    private val LOG = logger<CliResolver>()
    
    @Volatile
    private var cachedPath: String? = null
    
    /**
     * Resolves CLI executable path with caching.
     * 
     * @return CLI command to execute (executable path or python invocation)
     * @throws CliNotFoundException if CLI cannot be located
     */
    fun resolve(): String {
        // Check cache first
        cachedPath?.let { 
            LOG.debug("Using cached CLI path: $it")
            return it 
        }
        
        val resolved = resolveInternal()
        cachedPath = resolved
        
        LOG.info("Resolved CLI path: $resolved")
        return resolved
    }
    
    /**
     * Invalidates cached path.
     * Should be called when Settings change.
     */
    fun invalidateCache() {
        LOG.debug("Invalidating CLI path cache")
        cachedPath = null
    }
    
    /**
     * Internal resolution logic.
     * 
     * Implements full resolution chain matching VS Code extension behavior.
     */
    private fun resolveInternal(): String {
        val settings = service<LgSettingsService>()
        
        // Strategy 1: Explicit path from Settings
        val explicitPath = settings.state.cliPath?.trim() ?: ""
        if (explicitPath.isNotEmpty()) {
            if (isExecutable(explicitPath)) {
                LOG.info("Using explicit CLI path from Settings: $explicitPath")
                return explicitPath
            } else {
                LOG.warn("Configured CLI path not executable or not found: $explicitPath")
            }
        }
        
        // Strategy 2: System strategy - use configured Python interpreter
        if (settings.state.installStrategy == LgSettingsService.InstallStrategy.SYSTEM) {
            val interpreter = settings.state.pythonInterpreter?.trim() ?: ""
            if (interpreter.isNotEmpty() && isExecutable(interpreter)) {
                LOG.info("Using system Python interpreter: $interpreter")
                return interpreter
            }
        }
        
        // Strategy 3: Search in PATH for "listing-generator"
        val inPath = findInPath("listing-generator")
        if (inPath != null) {
            LOG.info("Found listing-generator in PATH: $inPath")
            return inPath
        }
        
        // Strategy 4: Try Python module with configured interpreter
        val configuredPython = settings.state.pythonInterpreter?.trim() ?: ""
        if (configuredPython.isNotEmpty() && isExecutable(configuredPython)) {
            LOG.info("Falling back to Python module with configured interpreter: $configuredPython")
            return configuredPython
        }
        
        // Strategy 5: Auto-detect Python and use module
        val detectedPython = findPython()
        if (detectedPython != null) {
            LOG.info("Falling back to Python module with auto-detected Python: $detectedPython")
            return detectedPython
        }
        
        // Give up
        throw CliNotFoundException(
            "listing-generator CLI not found. Please configure CLI path or Python interpreter in Settings."
        )
    }
    
    /**
     * Checks if file exists and is executable.
     */
    private fun isExecutable(path: String): Boolean {
        val file = File(path)
        return file.exists() && file.canExecute()
    }
    
    /**
     * Searches for executable in system PATH.
     * 
     * @param command Executable name (e.g., "listing-generator")
     * @return Absolute path if found, null otherwise
     */
    private fun findInPath(command: String): String? {
        val pathVar = System.getenv("PATH") ?: return null
        val separator = if (SystemInfo.isWindows) ";" else ":"
        val extensions = if (SystemInfo.isWindows) listOf(".exe", ".cmd", ".bat", "") else listOf("")
        
        for (dir in pathVar.split(separator)) {
            for (ext in extensions) {
                val executable = File(dir, command + ext)
                if (executable.exists() && executable.canExecute()) {
                    return executable.absolutePath
                }
            }
        }
        
        return null
    }
    
    /**
     * Auto-detects Python interpreter.
     * 
     * Tries common Python commands: python3, python, py (Windows).
     * 
     * @return Path to Python executable if found, null otherwise
     */
    private fun findPython(): String? {
        val candidates = if (SystemInfo.isWindows) {
            listOf("py", "python3", "python")
        } else {
            listOf("python3", "python")
        }
        
        for (cmd in candidates) {
            val python = findInPath(cmd)
            if (python != null && isPythonValid(python)) {
                return python
            }
        }
        
        return null
    }
    
    /**
     * Validates Python executable by running --version.
     * 
     * @param pythonPath Path to Python executable
     * @return true if Python works, false otherwise
     */
    private fun isPythonValid(pythonPath: String): Boolean {
        return try {
            val commandLine = GeneralCommandLine(pythonPath, "--version")
            val handler = CapturingProcessHandler(commandLine)
            val result = handler.runProcess(4000) // 4 second timeout
            
            result.exitCode == 0
        } catch (e: Exception) {
            LOG.debug("Failed to validate Python at $pythonPath: ${e.message}")
            false
        }
    }
}
