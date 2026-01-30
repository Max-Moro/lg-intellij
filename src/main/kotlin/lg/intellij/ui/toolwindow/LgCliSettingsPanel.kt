package lg.intellij.ui.toolwindow

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import lg.intellij.LgBundle
import lg.intellij.models.ClaudeIntegrationMethod
import lg.intellij.models.ClaudeMethodDescriptor
import lg.intellij.models.ClaudeModel
import lg.intellij.models.ClaudeModelDescriptor
import lg.intellij.models.CodexReasoningEffort
import lg.intellij.models.CodexReasoningEffortDescriptor
import lg.intellij.models.ShellDescriptor
import lg.intellij.models.ShellType
import lg.intellij.services.state.LgPanelStateService
import lg.intellij.services.state.LgSettingsService
import lg.intellij.ui.components.LgLabeledComponent
import lg.intellij.ui.components.LgWrappingPanel
import javax.swing.JComponent
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * CLI Provider Settings panel for Control Panel.
 *
 * Shown when any CLI-based provider is selected (e.g., com.anthropic.claude.cli, com.openai.codex.cli).
 * Provides configuration for:
 * - Scope (relative workspace subdirectory) - for all CLI providers
 * - Shell type (bash/zsh/powershell/cmd) - for all CLI providers
 * - Claude model (haiku/sonnet/opus) - only for com.anthropic.claude.cli
 * - Integration method (memory-file/session) - only for com.anthropic.claude.cli
 * - Reasoning effort (minimal/low/medium/high/xhigh) - only for com.openai.codex.cli
 */
@Suppress("unused") // Instantiated dynamically in LgControlPanel.createContentUI
class LgCliSettingsPanel(
    project: Project
) {

    private val stateService = project.service<LgPanelStateService>()

    /**
     * Create UI panel using Kotlin UI DSL.
     * Returns content rows without wrapping group (group is created by parent).
     */
    @Suppress("unused") // Called from LgControlPanel via reflection
    fun createUI(): JComponent {
        return panel {
            createBasicSettingsRow()

            // Claude-specific settings only for claude.cli provider
            if (isClaudeProvider()) {
                createClaudeSettingsRow()
            }

            // Codex-specific settings only for codex.cli provider
            if (isCodexProvider()) {
                createCodexSettingsRow()
            }
        }
    }

    /**
     * Check if current AI provider is Claude CLI.
     */
    private fun isClaudeProvider(): Boolean {
        return stateService.state.providerId == "com.anthropic.claude.cli"
    }

    /**
     * Check if current AI provider is Codex CLI.
     */
    private fun isCodexProvider(): Boolean {
        return stateService.state.providerId == "com.openai.codex.cli"
    }

    private fun Panel.createBasicSettingsRow() {
        row {
            val flowPanel = LgWrappingPanel(hgap = 16).apply {
                // Scope TextField
                val scopeField = JBTextField(10).apply {
                    text = stateService.state.cliScope ?: ""
                    document.addDocumentListener(object : DocumentListener {
                        override fun insertUpdate(e: DocumentEvent?) = update()
                        override fun removeUpdate(e: DocumentEvent?) = update()
                        override fun changedUpdate(e: DocumentEvent?) = update()
                        private fun update() {
                            stateService.state.cliScope = text
                        }
                    })
                }
                add(
                    LgLabeledComponent.create(
                    LgBundle.message("control.cli.scope.label"),
                    scopeField
                ))

                // Shell ComboBox
                val shellDescriptors = ShellType.getAvailableShells()
                val shellCombo = ComboBox(shellDescriptors.toTypedArray()).apply {
                    selectedItem = shellDescriptors.find { it.id == stateService.state.cliShell }
                    addActionListener {
                        val selected = selectedItem as? ShellDescriptor
                        if (selected != null) {
                            stateService.state.cliShell = selected.id
                        }
                    }
                }
                add(
                    LgLabeledComponent.create(
                    LgBundle.message("control.cli.shell.label"),
                    shellCombo
                ))
            }

            cell(flowPanel).align(AlignX.FILL)
        }
    }

    private fun Panel.createClaudeSettingsRow() {
        row {
            val flowPanel = LgWrappingPanel(hgap = 16).apply {
                // Model ComboBox
                val modelDescriptors = ClaudeModel.getAvailableModels()
                val modelCombo = ComboBox(modelDescriptors.toTypedArray()).apply {
                    selectedItem = modelDescriptors.find { it.id == stateService.state.claudeModel }
                    addActionListener {
                        val selected = selectedItem as? ClaudeModelDescriptor
                        if (selected != null) {
                            stateService.state.claudeModel = selected.id
                        }
                    }
                }
                add(
                    LgLabeledComponent.create(
                    LgBundle.message("control.claude.model.label"),
                    modelCombo
                ))

                // Method ComboBox
                val methodDescriptors = ClaudeIntegrationMethod.getAvailableMethods()
                val methodCombo = ComboBox(methodDescriptors.toTypedArray()).apply {
                    selectedItem = methodDescriptors.find { it.id == stateService.state.claudeIntegrationMethod }
                    addActionListener {
                        val selected = selectedItem as? ClaudeMethodDescriptor
                        if (selected != null) {
                            stateService.state.claudeIntegrationMethod = selected.id
                        }
                    }
                }
                add(
                    LgLabeledComponent.create(
                    LgBundle.message("control.claude.method.label"),
                    methodCombo
                ))
            }

            cell(flowPanel).align(AlignX.FILL)
        }
    }

    private fun Panel.createCodexSettingsRow() {
        row {
            val flowPanel = LgWrappingPanel(hgap = 16).apply {
                // Reasoning Effort ComboBox
                val effortDescriptors = CodexReasoningEffort.getAvailableEfforts()
                val effortCombo = ComboBox(effortDescriptors.toTypedArray()).apply {
                    selectedItem = effortDescriptors.find { it.id == stateService.state.codexReasoningEffort }
                    addActionListener {
                        val selected = selectedItem as? CodexReasoningEffortDescriptor
                        if (selected != null) {
                            stateService.state.codexReasoningEffort = selected.id
                        }
                    }
                }
                add(
                    LgLabeledComponent.create(
                    LgBundle.message("control.codex.reasoning.label"),
                    effortCombo
                ))
            }

            cell(flowPanel).align(AlignX.FILL)
        }
    }
}