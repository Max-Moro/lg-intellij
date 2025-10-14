package lg.intellij.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.SimpleListCellRenderer
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
        
        group(LgBundle.message("settings.group.cli")) {
            row(LgBundle.message("settings.cli.path.label")) {
                textFieldWithBrowseButton(
                    FileChooserDescriptorFactory.createSingleFileDescriptor()
                        .withTitle(LgBundle.message("settings.cli.path.browse.title"))
                ).bindText(
                    getter = { settings.state.cliPath ?: "" },
                    setter = { settings.state.cliPath = it }
                ).comment(LgBundle.message("settings.cli.path.comment"))
            }
            
            row(LgBundle.message("settings.python.interpreter.label")) {
                textFieldWithBrowseButton(
                    FileChooserDescriptorFactory.createSingleFileDescriptor()
                        .withTitle(LgBundle.message("settings.python.interpreter.browse.title"))
                ).bindText(
                    getter = { settings.state.pythonInterpreter ?: "" },
                    setter = { settings.state.pythonInterpreter = it }
                ).comment(LgBundle.message("settings.python.interpreter.comment"))
            }
            
            row(LgBundle.message("settings.install.strategy.label")) {
                comboBox(
                    LgSettingsService.InstallStrategy.entries,
                    SimpleListCellRenderer.create { label, value, _ ->
                        label.text = LgBundle.message("settings.install.strategy.${value.name}")
                    }
                ).bindItem(settings.state::installStrategy.toNullableProperty())
                .comment(LgBundle.message("settings.install.strategy.comment"))
            }
        }
        
        group(LgBundle.message("settings.group.ai")) {
            row(LgBundle.message("settings.ai.provider.label")) {
                comboBox(
                    LgSettingsService.AiProvider.entries,
                    SimpleListCellRenderer.create { label, value, _ ->
                        label.text = LgBundle.message("settings.ai.provider.${value.name}")
                    }
                ).bindItem(settings.state::aiProvider.toNullableProperty())
            }.comment(LgBundle.message("settings.ai.provider.comment"))
            
            row(LgBundle.message("settings.openai.key.label")) {
                cell(com.intellij.ui.components.JBPasswordField())
                    .columns(40)
                    .comment(LgBundle.message("settings.openai.key.comment"))
                
                button(LgBundle.message("settings.openai.key.configure")) {
                    // TODO: Phase 19 - Implement PasswordSafe integration
                    com.intellij.openapi.ui.Messages.showInfoMessage(
                        LgBundle.message("settings.openai.key.configure.not.implemented"),
                        LgBundle.message("settings.openai.key.configure.title")
                    )
                }
            }
        }
        
        group(LgBundle.message("settings.group.editor")) {
            row {
                checkBox(LgBundle.message("settings.open.editable.label"))
                    .bindSelected(settings.state::openAsEditable)
            }.comment(LgBundle.message("settings.open.editable.comment"))
        }
        
        separator()
        
        row {
            button(LgBundle.message("settings.reset.defaults")) {
                val result = com.intellij.openapi.ui.Messages.showYesNoDialog(
                    LgBundle.message("settings.reset.defaults.confirm"),
                    LgBundle.message("settings.reset.defaults.title"),
                    com.intellij.openapi.ui.Messages.getQuestionIcon()
                )
                
                if (result == com.intellij.openapi.ui.Messages.YES) {
                    settings.loadState(LgSettingsService.State())
                    reset()
                }
            }
        }
    }
}
