package lg.intellij.cli

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger

/**
 * Resolves the path to listing-generator CLI executable.
 * 
 * Resolution strategies (in order):
 * 1. Explicit path from Settings (lg.cli.path)
 * 2. Search in system PATH
 * 3. Fallback to Python module invocation (python -m lg.cli)
 * 
 * Phase 1 Note: This is a STUB implementation that always returns "listing-generator".
 * Real resolution logic will be implemented in Phase 2 after Settings infrastructure is ready.
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
        
        // STUB: Always return hardcoded value for Phase 1
        // Real implementation will check Settings and PATH in Phase 2
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
     * STUB for Phase 1: returns hardcoded "listing-generator"
     * TODO Phase 2: Implement real resolution:
     * - Check Settings.state.cliPath
     * - Search in PATH (using ProcessBuilder or which/where command)
     * - Try python -m lg.cli
     * - Throw CliNotFoundException if all fail
     */
    private fun resolveInternal(): String {
        LOG.warn("Using stub CLI resolver - returns hardcoded 'listing-generator'")
        
        // STUB: Return hardcoded value
        // This assumes listing-generator is in PATH or developer has it available
        return "listing-generator"
        
        // TODO Phase 2: Real implementation
        /*
        // 1. Check explicit path from Settings
        val explicitPath = service<LgSettingsService>().state.cliPath
        if (explicitPath.isNotBlank() && isExecutable(explicitPath)) {
            return explicitPath
        }
        
        // 2. Search in PATH
        val inPath = findInPath("listing-generator")
        if (inPath != null) {
            return inPath
        }
        
        // 3. Try Python module
        if (isPythonAvailable()) {
            return "python -m lg.cli"  // Will need special handling in CliExecutor
        }
        
        // 4. Give up
        throw CliNotFoundException(
            "listing-generator CLI not found. Please configure path in Settings."
        )
        */
    }
    
    // TODO Phase 2: Helper methods for real resolution
    /*
    private fun isExecutable(path: String): Boolean {
        val file = File(path)
        return file.exists() && file.canExecute()
    }
    
    private fun findInPath(command: String): String? {
        val pathVar = System.getenv("PATH") ?: return null
        val separator = if (SystemInfo.isWindows) ";" else ":"
        
        for (dir in pathVar.split(separator)) {
            val executable = if (SystemInfo.isWindows) {
                File(dir, "$command.exe")
            } else {
                File(dir, command)
            }
            
            if (executable.exists() && executable.canExecute()) {
                return executable.absolutePath
            }
        }
        
        return null
    }
    
    private fun isPythonAvailable(): Boolean {
        // Try common Python commands
        for (cmd in listOf("python3", "python", "py")) {
            try {
                val process = ProcessBuilder(cmd, "--version")
                    .redirectErrorStream(true)
                    .start()
                
                val exitCode = process.waitFor()
                if (exitCode == 0) {
                    return true
                }
            } catch (e: Exception) {
                continue
            }
        }
        
        return false
    }
    */
    
    companion object {
        private val LOG = logger<CliResolver>()
    }
}
