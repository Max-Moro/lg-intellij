package lg.intellij.services.state

import com.intellij.openapi.components.*

/**
 * Application-level settings for Listing Generator plugin.
 *
 * Stores global configuration that applies to all projects:
 * - Developer mode toggle and Python interpreter
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
     * Persistent state class.
     */
    class State : BaseState() {
        /** Developer Mode toggle */
        var developerMode by property(false)

        /** Path to Python interpreter (Developer Mode only) */
        var pythonInterpreter by string("")

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
