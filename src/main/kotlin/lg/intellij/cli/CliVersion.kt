package lg.intellij.cli

/**
 * CLI version management for Listing Generator plugin.
 *
 * Defines the CLI version that this plugin is compatible with
 * and provides version constraint generation for pip/pipx install.
 */
object CliVersion {
    /**
     * CLI version that this plugin is compatible with.
     * Should match plugin version in gradle.properties.
     *
     * Format: Semantic versioning (e.g., "0.9.0")
     * Compatibility: ^0.10.0 means >=0.10.0 <0.11.0
     */
    const val REQUIRED_VERSION = "0.10.0"

    /**
     * PyPI package name for Listing Generator CLI.
     */
    const val PYPI_PACKAGE = "listing-generator"

    /**
     * Generates version constraint for pip/pipx install.
     * Returns a caret range: ^0.9.0 � >=0.9.0,<0.10.0
     *
     * @return Version constraint string for pip install
     */
    fun getVersionConstraint(): String {
        val parts = REQUIRED_VERSION.split(".")
        val major = parts[0]
        val minor = parts[1]
        val nextMinor = (minor.toIntOrNull() ?: 0) + 1

        return ">=$REQUIRED_VERSION,<$major.$nextMinor.0"
    }

    /**
     * Parses semantic version string into components.
     *
     * @param version Version string (e.g., "0.9.1")
     * @return Triple of (major, minor, patch) numbers
     */
    fun parseVersion(version: String): Triple<Int, Int, Int> {
        val parts = version.split(".").map { it.toIntOrNull() ?: 0 }
        return Triple(
            parts.getOrElse(0) { 0 },
            parts.getOrElse(1) { 0 },
            parts.getOrElse(2) { 0 }
        )
    }

    /**
     * Checks if installed version is compatible with required version.
     *
     * Compatible means same major.minor, any patch.
     *
     * @param installedVersion Installed version string (e.g., "0.9.1")
     * @return true if compatible, false otherwise
     */
    fun isVersionCompatible(installedVersion: String): Boolean {
        val (installedMajor, installedMinor, _) = parseVersion(installedVersion)
        val (requiredMajor, requiredMinor, _) = parseVersion(REQUIRED_VERSION)

        return installedMajor == requiredMajor && installedMinor == requiredMinor
    }
}
