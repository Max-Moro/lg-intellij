package lg.intellij.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import lg.intellij.LgBundle
import lg.intellij.listeners.LgSettingsChangeListener
import lg.intellij.services.state.LgSettingsService

/**
 * Settings page for Listing Generator plugin.
 * 
 * Provides UI for configuring:
 * - CLI installation and paths
 * - Default tokenization parameters
 * - AI integration
 * - Editor behavior
 * 
 * All fields are bound to [LgSettingsService.State] and persist automatically.
 */
class LgSettingsConfigurable : BoundConfigurable(LgBundle.message("settings.display.name")) {

    private val settings = service<LgSettingsService>()

    override fun apply() {
        super.apply()

        // Notify listeners about Settings changes (invalidates CLI resolver cache, etc.)
        LgSettingsChangeListener.notifySettingsChanged()
    }
    
    override fun createPanel(): DialogPanel = panel {
        // Reference to developer mode checkbox for reactive visibility binding
        lateinit var developerModeCheckBox: Cell<JBCheckBox>

        group(LgBundle.message("settings.group.cli")) {
            row {
                developerModeCheckBox = checkBox(LgBundle.message("settings.developer.mode.label"))
                    .bindSelected(settings.state::developerMode)
            }.comment(LgBundle.message("settings.developer.mode.comment"))

            row(LgBundle.message("settings.python.interpreter.label")) {
                @Suppress("UnstableApiUsage")
                textFieldWithBrowseButton(
                    FileChooserDescriptorFactory.singleFile()
                        .withTitle(LgBundle.message("settings.python.interpreter.browse.title"))
                ).bindText(
                    getter = { settings.state.pythonInterpreter ?: "" },
                    setter = { settings.state.pythonInterpreter = it }
                ).comment(LgBundle.message("settings.python.interpreter.comment"))
            }.visibleIf(developerModeCheckBox.selected)
        }

        group(LgBundle.message("settings.group.editor")) {
            row {
                checkBox(LgBundle.message("settings.open.editable.label"))
                    .bindSelected(settings.state::openAsEditable)
            }.comment(LgBundle.message("settings.open.editable.comment"))
        }
    }
}
