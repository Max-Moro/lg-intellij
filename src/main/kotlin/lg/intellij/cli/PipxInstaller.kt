package lg.intellij.cli

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Manages pipx-based CLI installation for User Mode.
 *
 * Responsibilities:
 * - Check if pipx is available
 * - Install CLI with pinned version
 * - Check installed CLI version
 * - Upgrade CLI within version constraint
 * - Auto-check for patch updates (cached for 24 hours)
 *
 * Thread Safety: All operations are suspend functions executed on Dispatchers.IO
 */
@Service(Service.Level.APP)
class PipxInstaller {

    private val log = logger<PipxInstaller>()

    /**
     * Mutex for synchronizing install/upgrade operations.
     * Prevents race conditions when multiple coroutines try to install CLI simultaneously.
     */
    private val installMutex = Mutex()

    /**
     * Cached fatal error message from first failed install/upgrade attempt.
     * Used to prevent repeated installation attempts and avoid spamming user with notifications.
     * Resets on IDE restart.
     */
    @Volatile
    private var fatalError: String? = null

    /**
     * Timestamp of last update check (in-memory cache).
     * Resets on IDE restart.
     */
    @Volatile
    private var lastUpdateCheck: Long? = null

    /**
     * Ensures CLI is installed via pipx with correct version.
     *
     * Auto-installs or upgrades if needed.
     * Checks for patch updates once per 24 hours.
     *
     * Thread Safety: Uses mutex to prevent concurrent install/upgrade operations.
     * Caches fatal errors to avoid repeated failed attempts.
     *
     * @return Path to installed CLI binary
     * @throws CliNotFoundException if pipx is not available or installation fails
     *         (with silent=true for subsequent failures after fatal error)
     */
    suspend fun ensureCli(): String {
        log.debug("Ensuring CLI is installed")

        // Early check: if previous attempt failed fatally, fail silently
        fatalError?.let {
            log.debug("CLI unavailable due to previous fatal error: $it")
            throw CliNotFoundException(it, silent = true)
        }

        // Fast path: CLI already installed and compatible (most common case)
        val quickCheck = getInstalledVersion()
        if (quickCheck != null &&
            CliVersion.isVersionCompatible(quickCheck) &&
            !shouldCheckForUpdates()) {
            // CLI is good, just return path
            val cliPath = getCliPath()
            if (cliPath != null) {
                return cliPath
            }
            // Path not found - fall through to slow path with mutex
        }

        // Slow path: need to install/upgrade or handle errors
        // Use mutex to prevent concurrent pipx operations
        installMutex.withLock {
            // Re-check fatal error inside lock (double-check locking)
            fatalError?.let {
                log.debug("CLI unavailable (checked inside lock): $it")
                throw CliNotFoundException(it, silent = true)
            }

            // Check if pipx is available
            if (!isPipxAvailable()) {
                val error = "pipx not found. Install pipx (https://pipx.pypa.io/stable/) or enable Developer Mode."
                fatalError = error
                log.warn("pipx not available, caching fatal error")
                throw CliNotFoundException(error, silent = false)
            }

            // Check installed version (re-check inside lock)
            val installedVersion = getInstalledVersion()

            try {
                when {
                    installedVersion == null -> {
                        // Not installed - install now
                        log.info("CLI not installed, installing...")
                        install()
                        lastUpdateCheck = System.currentTimeMillis()
                    }

                    !CliVersion.isVersionCompatible(installedVersion) -> {
                        // Incompatible version - upgrade
                        log.warn("Incompatible CLI version $installedVersion, upgrading...")
                        upgrade()
                        lastUpdateCheck = System.currentTimeMillis()
                    }

                    shouldCheckForUpdates() -> {
                        // Compatible version, but check for patch updates periodically
                        log.info("Checking for patch updates...")
                        upgrade()
                        lastUpdateCheck = System.currentTimeMillis()
                    }

                    else -> {
                        val nextCheckIn = getNextCheckInHours()
                        log.debug("CLI version $installedVersion is compatible, next update check in $nextCheckIn hours")
                    }
                }
            } catch (e: CliNotFoundException) {
                // Cache fatal error from install/upgrade
                fatalError = e.message
                log.error("Failed to install/upgrade CLI, caching fatal error: ${e.message}")
                throw e
            } catch (e: Exception) {
                // Unexpected error - cache and convert to CliNotFoundException
                val error = "Unexpected error during install/upgrade: ${e.message}"
                fatalError = error
                log.error("Unexpected error during install/upgrade", e)
                throw CliNotFoundException(error, silent = false)
            }

            // Get CLI path after successful install/upgrade
            val cliPath = getCliPath()
            if (cliPath == null) {
                val error = "CLI installation failed: binary not found after install"
                fatalError = error
                log.error(error)
                throw CliNotFoundException(error, silent = false)
            }

            return cliPath
        }
    }

    /**
     * Checks if it's time to check for updates.
     *
     * @return true if update check is needed (24 hours elapsed or never checked)
     */
    private fun shouldCheckForUpdates(): Boolean {
        val lastCheck = lastUpdateCheck ?: return true // Never checked

        val elapsed = System.currentTimeMillis() - lastCheck
        return elapsed >= UPDATE_CHECK_INTERVAL_MS
    }

    /**
     * Calculates hours until next update check.
     *
     * @return Hours remaining (rounded down)
     */
    private fun getNextCheckInHours(): Long {
        val lastCheck = lastUpdateCheck ?: return 0

        val elapsed = System.currentTimeMillis() - lastCheck
        val remaining = UPDATE_CHECK_INTERVAL_MS - elapsed
        return maxOf(0, remaining / (60 * 60 * 1000))
    }

    /**
     * Checks if pipx is available on the system.
     *
     * @return true if pipx command is found, false otherwise
     */
    suspend fun isPipxAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val commandLine = GeneralCommandLine("pipx", "--version")
                .withCharset(StandardCharsets.UTF_8)

            val handler = CapturingProcessHandler(commandLine)
            val result = handler.runProcess(4000) // 4 second timeout

            result.exitCode == 0
        } catch (e: Exception) {
            log.debug("pipx availability check failed: ${e.message}")
            false
        }
    }

    /**
     * Installs CLI with version constraint using pipx.
     *
     * @throws CliNotFoundException if installation fails
     */
    suspend fun install() = withContext(Dispatchers.IO) {
        val versionConstraint = CliVersion.getVersionConstraint()
        val packageSpec = "${CliVersion.PYPI_PACKAGE}$versionConstraint"

        log.info("Installing $packageSpec")

        try {
            val commandLine = GeneralCommandLine("pipx", "install", packageSpec)
                .withCharset(StandardCharsets.UTF_8)

            val handler = CapturingProcessHandler(commandLine)
            val result = handler.runProcess(120_000) // 2 minute timeout

            if (result.exitCode != 0) {
                throw CliNotFoundException(
                    "pipx install failed with exit code ${result.exitCode}: ${result.stderr}"
                )
            }

            log.debug("Install output: ${result.stdout}")
            log.info("CLI installed successfully")
        } catch (e: CliNotFoundException) {
            throw e
        } catch (e: Exception) {
            log.error("Installation failed", e)
            throw CliNotFoundException("Failed to install CLI via pipx: ${e.message}")
        }
    }

    /**
     * Upgrades CLI to latest compatible version.
     *
     * Uses reinstall with version constraint to ensure we stay within
     * compatible major.minor range (e.g., ^0.9.0 → latest 0.9.x, not 0.10.0).
     *
     * @throws CliNotFoundException if upgrade fails
     */
    suspend fun upgrade() = withContext(Dispatchers.IO) {
        log.info("Upgrading CLI")

        try {
            // Uninstall current version
            log.debug("Uninstalling current version")
            val uninstallCmd = GeneralCommandLine("pipx", "uninstall", CliVersion.PYPI_PACKAGE)
                .withCharset(StandardCharsets.UTF_8)

            val uninstallHandler = CapturingProcessHandler(uninstallCmd)
            val uninstallResult = uninstallHandler.runProcess(60_000) // 1 minute timeout

            if (uninstallResult.exitCode != 0) {
                throw CliNotFoundException(
                    "pipx uninstall failed with exit code ${uninstallResult.exitCode}: ${uninstallResult.stderr}"
                )
            }

            // Reinstall with version constraint (will get latest patch version)
            log.debug("Reinstalling with version constraint")
            install()

            log.info("CLI upgraded successfully")
        } catch (e: CliNotFoundException) {
            throw e
        } catch (e: Exception) {
            log.error("Upgrade failed", e)
            throw CliNotFoundException("Failed to upgrade CLI via pipx: ${e.message}")
        }
    }

    /**
     * Checks installed CLI version.
     *
     * @return Version string (e.g., "0.9.0") or null if not installed
     */
    suspend fun getInstalledVersion(): String? = withContext(Dispatchers.IO) {
        try {
            val commandLine = GeneralCommandLine("listing-generator", "--version")
                .withCharset(StandardCharsets.UTF_8)

            val handler = CapturingProcessHandler(commandLine)
            val result = handler.runProcess(4000) // 4 second timeout

            if (result.exitCode != 0) {
                return@withContext null
            }

            // Parse version from output (e.g., "listing-generator 0.9.0")
            val versionRegex = Regex("""(\d+\.\d+\.\d+)""")
            val match = versionRegex.find(result.stdout)
            match?.groups?.get(1)?.value
        } catch (e: Exception) {
            log.debug("Failed to get installed version: ${e.message}")
            null
        }
    }

    /**
     * Gets path to installed CLI binary.
     *
     * @return Path to listing-generator binary or null if not found
     */
    private suspend fun getCliPath(): String? = withContext(Dispatchers.IO) {
        try {
            val cmd = if (SystemInfo.isWindows) "where" else "which"
            val commandLine = GeneralCommandLine(cmd, "listing-generator")
                .withCharset(StandardCharsets.UTF_8)

            val handler = CapturingProcessHandler(commandLine)
            val result = handler.runProcess(4000) // 4 second timeout

            if (result.exitCode != 0) {
                return@withContext null
            }

            // Return first line (path to binary)
            result.stdout.trim().lines().firstOrNull()?.let { path ->
                if (File(path).exists()) path else null
            }
        } catch (e: Exception) {
            log.debug("Failed to get CLI path: ${e.message}")
            null
        }
    }

    companion object {
        /**
         * Update check interval: 24 hours in milliseconds.
         */
        private const val UPDATE_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    }
}
