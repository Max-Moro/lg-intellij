package lg.intellij.services.state

import com.intellij.openapi.components.*

/**
 * Application-level settings for Listing Generator plugin.
 * 
 * Stores global configuration that applies to all projects:
 * - CLI path and installation strategy
 * - Default tokenization parameters
 * - AI integration preferences
 * - Editor behavior
 * 
 * Storage: Application config directory (~/.config/JetBrains/.../lg-settings.xml)
 * Sync: Enabled (roaming between machines)
 */
@Service
@State(
    name = "LgSettings",
    storages = [Storage("lg-settings.xml")],
    category = SettingsCategory.TOOLS
)
class LgSettingsService : SimplePersistentStateComponent<LgSettingsService.State>(State()) {
    
    /**
     * Installation strategies for CLI.
     */
    enum class InstallStrategy {
        /** Managed virtual environment (recommended) */
        MANAGED_VENV,
        
        /** System-wide Python installation */
        SYSTEM,
        
        /** pipx installation */
        PIPX
    }
    
    /**
     * Persistent state class.
     */
    class State : BaseState() {
        /** Explicit path to listing-generator executable (empty for auto-detection) */
        var cliPath by string("")
        
        /** Path to Python interpreter (for managed venv or system strategy) */
        var pythonInterpreter by string("")
        
        /** CLI installation strategy */
        var installStrategy by enum(InstallStrategy.MANAGED_VENV)
        
        /** AI provider ID for "Send to AI" action (empty = auto-detect best) */
        var aiProvider by string("")
        
        /** Open generated files as editable (instead of read-only) */
        var openAsEditable by property(false)
    }
    
    companion object {
        /**
         * Returns the singleton instance of LgSettingsService.
         */
        fun getInstance(): LgSettingsService = service()
    }
}
