package lg.intellij.cli

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.io.HttpRequests
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

                        // Query PyPI for latest compatible version
                        val latestVersion = getLatestCompatibleVersion()

                        if (latestVersion != null) {
                            if (isNewerVersion(latestVersion, installedVersion)) {
                                log.info("New patch version available: $latestVersion (current: $installedVersion)")
                                upgrade()
                            } else {
                                log.debug("Already on latest patch version: $installedVersion")
                            }
                        } else {
                            // PyPI check failed - skip upgrade this time
                            log.debug("Skipping upgrade check (PyPI unavailable or version check failed)")
                        }

                        // Always update timestamp to avoid repeated checks
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
     * Fetches latest compatible version from PyPI.
     *
     * Queries PyPI JSON API to get the latest version within compatible range
     * (same major.minor, any patch).
     *
     * @return Latest compatible version string (e.g., "0.9.3") or null if unavailable/error
     */
    private suspend fun getLatestCompatibleVersion(): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://pypi.org/pypi/${CliVersion.PYPI_PACKAGE}/json"
            log.debug("Fetching latest version from PyPI: $url")

            val json = HttpRequests.request(url)
                .connectTimeout(5000)
                .readTimeout(5000)
                .readString()

            // Extract version from JSON: {"info": {"version": "0.9.2"}}
            // Using simple regex instead of full JSON parser for efficiency
            val versionRegex = Regex(""""version"\s*:\s*"(\d+\.\d+\.\d+)"""")
            val match = versionRegex.find(json)
            val latestVersion = match?.groups?.get(1)?.value

            if (latestVersion == null) {
                log.warn("Failed to parse version from PyPI response")
                return@withContext null
            }

            // Check if latest version is compatible (same major.minor)
            if (!CliVersion.isVersionCompatible(latestVersion)) {
                log.debug("Latest PyPI version $latestVersion is not compatible with ${CliVersion.REQUIRED_VERSION}")
                return@withContext null
            }

            log.debug("Latest compatible version from PyPI: $latestVersion")
            latestVersion
        } catch (e: Exception) {
            log.debug("Failed to fetch latest version from PyPI: ${e.message}")
            null
        }
    }

    /**
     * Compares two version strings to determine if first is newer than second.
     *
     * @param latest First version string (e.g., "0.9.3")
     * @param current Second version string (e.g., "0.9.2")
     * @return true if latest > current, false otherwise
     */
    private fun isNewerVersion(latest: String, current: String): Boolean {
        val (latestMajor, latestMinor, latestPatch) = CliVersion.parseVersion(latest)
        val (currentMajor, currentMinor, currentPatch) = CliVersion.parseVersion(current)

        return when {
            latestMajor > currentMajor -> true
            latestMajor < currentMajor -> false
            latestMinor > currentMinor -> true
            latestMinor < currentMinor -> false
            latestPatch > currentPatch -> true
            else -> false
        }
    }

    /**
     * Checks if pipx is available on the system.
     *
     * Uses ExecutableDetector with fallback to common user paths.
     * This correctly handles shell environment on all platforms (including Linux GUI apps).
     *
     * @return true if pipx command is found, false otherwise
     */
    suspend fun isPipxAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val pipxExecutable = ExecutableDetector.findExecutable("pipx")
            pipxExecutable != null
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
        // No quotes needed - GeneralCommandLine passes args directly to process without shell
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
     * Uses ExecutableDetector to find CLI binary (handles Linux GUI apps where ~/.local/bin is not in PATH).
     *
     * @return Version string (e.g., "0.9.0") or null if not installed
     */
    suspend fun getInstalledVersion(): String? = withContext(Dispatchers.IO) {
        try {
            // Use ExecutableDetector to find CLI (handles Linux GUI apps)
            val cliExecutable = ExecutableDetector.findExecutable("listing-generator")
            if (cliExecutable == null) {
                log.debug("CLI executable not found via ExecutableDetector")
                return@withContext null
            }

            val commandLine = GeneralCommandLine(cliExecutable.absolutePath, "--version")
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
     * Tries multiple strategies:
     * 1. Use ExecutableDetector with fallback to common user paths (works on all platforms)
     * 2. Query pipx for PIPX_BIN_DIR and check there (fallback for edge cases)
     *
     * @return Path to listing-generator binary or null if not found
     */
    private suspend fun getCliPath(): String? = withContext(Dispatchers.IO) {
        // Strategy 1: Use ExecutableDetector with fallback to common user paths
        // This correctly handles shell environment on all platforms (including Linux GUI apps)
        try {
            val cliExecutable = ExecutableDetector.findExecutable("listing-generator")
            if (cliExecutable != null) {
                log.debug("Found CLI via ExecutableDetector: ${cliExecutable.absolutePath}")
                return@withContext cliExecutable.absolutePath
            }
        } catch (e: Exception) {
            log.debug("ExecutableDetector search failed: ${e.message}")
        }

        // Strategy 2: Query pipx for bin directory (fallback)
        // This is needed for edge cases where PATH might not be properly configured
        try {
            val pipxBinDir = getPipxBinDir()
            if (pipxBinDir != null) {
                val binaryName = if (SystemInfo.isWindows) "listing-generator.exe" else "listing-generator"
                val cliPath = File(pipxBinDir, binaryName)
                if (cliPath.exists() && cliPath.canExecute()) {
                    log.debug("Found CLI in PIPX_BIN_DIR: ${cliPath.absolutePath}")
                    return@withContext cliPath.absolutePath
                }
            }
        } catch (e: Exception) {
            log.debug("PIPX_BIN_DIR lookup failed: ${e.message}")
        }

        log.debug("CLI binary not found via any strategy")
        null
    }

    /**
     * Gets PIPX_BIN_DIR from pipx environment.
     *
     * @return Path to pipx bin directory or null if unavailable
     */
    private fun getPipxBinDir(): String? {
        return try {
            val commandLine = GeneralCommandLine("pipx", "environment", "--value", "PIPX_BIN_DIR")
                .withCharset(StandardCharsets.UTF_8)

            val handler = CapturingProcessHandler(commandLine)
            val result = handler.runProcess(4000)

            if (result.exitCode == 0) {
                val binDir = result.stdout.trim()
                if (binDir.isNotEmpty()) {
                    log.debug("PIPX_BIN_DIR: $binDir")
                    binDir
                } else {
                    null
                }
            } else {
                log.debug("pipx environment failed with exit code ${result.exitCode}")
                null
            }
        } catch (e: Exception) {
            log.debug("Failed to get PIPX_BIN_DIR: ${e.message}")
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
