package lg.intellij.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.dsl.builder.*
import kotlinx.coroutines.*
import lg.intellij.LgBundle
import lg.intellij.listeners.LgSettingsChangeListener
import lg.intellij.services.ai.AiIntegrationService
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
    private val aiService = AiIntegrationService.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // UI reference for manual state management
    private var providerCombo: ComboBox<String>? = null
    private var initialProvider: String = ""
    
    // Start detection after UI is created
    private fun startProviderDetection() {
        scope.launch {
            val availableIds = aiService.detectAvailableProviders()
            
            // Use invokeLater for modal dialog (Settings is modal)
            ApplicationManager.getApplication().invokeLater(
                { updateProviderCombo(availableIds) },
                ModalityState.any()
            )
        }
    }
    
    override fun reset() {
        super.reset()
        
        // Restore initial provider selection
        providerCombo?.selectedItem = initialProvider
    }
    
    override fun isModified(): Boolean {
        if (super.isModified()) return true
        
        // Check if provider changed
        val currentProvider = providerCombo?.selectedItem as? String
        return currentProvider != initialProvider
    }
    
    override fun apply() {
        super.apply()
        
        // Save provider selection
        val selectedProvider = providerCombo?.selectedItem as? String
        if (selectedProvider != null) {
            settings.state.aiProvider = selectedProvider
            initialProvider = selectedProvider
        }
        
        // Notify listeners about Settings changes (invalidates CLI resolver cache, etc.)
        LgSettingsChangeListener.notifySettingsChanged()
    }
    
    override fun disposeUIResources() {
        super.disposeUIResources()
        scope.cancel()
    }
    
    private fun updateProviderCombo(availableIds: List<String>) {
        val combo = providerCombo ?: return
        
        // Clear and repopulate
        combo.removeAllItems()
        availableIds.forEach { combo.addItem(it) }
        
        // Restore initial selection if still valid
        if (initialProvider in availableIds) {
            combo.selectedItem = initialProvider
        } else if (availableIds.isNotEmpty()) {
            combo.selectedItem = availableIds.first()
            initialProvider = availableIds.first()
        }
    }
    
    override fun createPanel(): DialogPanel = panel {
        
        group(LgBundle.message("settings.group.cli")) {
            row(LgBundle.message("settings.cli.path.label")) {
                @Suppress("UnstableApiUsage")
                textFieldWithBrowseButton(
                    FileChooserDescriptorFactory.singleFile()
                        .withTitle(LgBundle.message("settings.cli.path.browse.title"))
                ).bindText(
                    getter = { settings.state.cliPath ?: "" },
                    setter = { settings.state.cliPath = it }
                ).comment(LgBundle.message("settings.cli.path.comment"))
            }
            
            row(LgBundle.message("settings.python.interpreter.label")) {
                @Suppress("UnstableApiUsage")
                textFieldWithBrowseButton(
                    FileChooserDescriptorFactory.singleFile()
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
                // Initialize with saved value or auto-detect
                initialProvider = runBlocking {
                    aiService.resolveProvider(settings.state.aiProvider)
                }
                
                providerCombo = ComboBox(arrayOf(initialProvider)).apply {
                    renderer = SimpleListCellRenderer.create { label, value, _ ->
                        label.text = aiService.getProviderName(value)
                    }
                    selectedItem = initialProvider
                }
                
                cell(providerCombo!!)
            }.comment(LgBundle.message("settings.ai.provider.comment"))
            
            // Start async detection after UI is created
            startProviderDetection()
        }
        
        group(LgBundle.message("settings.group.editor")) {
            row {
                checkBox(LgBundle.message("settings.open.editable.label"))
                    .bindSelected(settings.state::openAsEditable)
            }.comment(LgBundle.message("settings.open.editable.comment"))
        }
    }
}
