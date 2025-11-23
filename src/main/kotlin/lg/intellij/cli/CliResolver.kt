package lg.intellij.cli

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.runBlocking
import lg.intellij.services.state.LgSettingsService
import java.io.File

/**
 * Structured CLI command specification.
 *
 * Equivalent to RunSpec from VS Code extension.
 *
 * @property cmd Executable path (e.g., "listing-generator" or "python3")
 * @property args Additional arguments before CLI args (e.g., ["-m", "lg.cli"] for Python module)
 */
data class CliRunSpec(
    val cmd: String,
    val args: List<String> = emptyList()
)

/**
 * Resolves the path to listing-generator CLI executable.
 *
 * Supports two modes:
 * 1. User Mode (default) - Auto-managed pipx installation with version pinning
 * 2. Developer Mode - Manual Python interpreter for testing unreleased CLI
 *
 * Equivalent to CliResolver.ts from VS Code extension.
 */
@Service(Service.Level.APP)
class CliResolver {

    private val log = logger<CliResolver>()

    @Volatile
    private var cachedSpec: CliRunSpec? = null

    /**
     * Resolves CLI run specification with caching.
     *
     * @return CLI run specification (command + args)
     * @throws CliNotFoundException if CLI cannot be located
     */
    fun resolve(): CliRunSpec {
        // Check cache first
        cachedSpec?.let {
            log.debug("Using cached CLI spec: ${it.cmd} ${it.args.joinToString(" ")}")
            return it
        }

        val resolved = resolveInternal()
        cachedSpec = resolved

        log.info("Resolved CLI spec: ${resolved.cmd} ${resolved.args.joinToString(" ")}")
        return resolved
    }

    /**
     * Invalidates cached spec.
     * Should be called when Settings change.
     */
    fun invalidateCache() {
        log.debug("Invalidating CLI spec cache")
        cachedSpec = null
    }

    /**
     * Internal resolution logic.
     *
     * Implements two-mode resolution: User Mode (pipx) or Developer Mode (Python interpreter).
     */
    private fun resolveInternal(): CliRunSpec {
        val settings = service<LgSettingsService>()
        val isDeveloperMode = settings.state.developerMode

        return if (isDeveloperMode) {
            log.info("Developer Mode enabled")
            resolveDeveloperMode(settings)
        } else {
            log.info("User Mode (pipx auto-install)")
            resolveUserMode()
        }
    }

    /**
     * Resolves CLI in Developer Mode.
     *
     * Uses configured Python interpreter with -m lg.cli
     *
     * @param settings Settings service
     * @return RunSpec for Python module execution
     * @throws CliNotFoundException if Python interpreter not configured or not found
     */
    private fun resolveDeveloperMode(settings: LgSettingsService): CliRunSpec {
        val pythonPath = settings.state.pythonInterpreter?.trim() ?: ""

        if (pythonPath.isEmpty()) {
            throw CliNotFoundException(
                "Developer Mode requires Python interpreter.\n" +
                "Configure it in Settings > Tools > Listing Generator."
            )
        }

        if (!File(pythonPath).exists()) {
            throw CliNotFoundException("Python interpreter not found: $pythonPath")
        }

        log.debug("Using Python: $pythonPath")

        return CliRunSpec(
            cmd = pythonPath,
            args = listOf("-m", "lg.cli")
        )
    }

    /**
     * Resolves CLI in User Mode.
     *
     * Uses pipx to auto-install/upgrade CLI with version pinning.
     *
     * @return RunSpec for listing-generator command
     * @throws CliNotFoundException if pipx is not available or installation fails
     */
    private fun resolveUserMode(): CliRunSpec {
        val installer = service<PipxInstaller>()

        // Ensure CLI is installed with correct version (blocking call)
        val cliPath = runBlocking { installer.ensureCli() }

        log.debug("Using pipx CLI: $cliPath")

        return CliRunSpec(cmd = cliPath)
    }
}
