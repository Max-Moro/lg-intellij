package lg.intellij.ui.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.ide.ui.UISettingsUtils
import com.intellij.ide.ui.laf.darcula.ui.DarculaEditorTextFieldBorder
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.*
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.Cell
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import lg.intellij.LgBundle
import lg.intellij.services.state.LgPanelStateService
import lg.intellij.ui.components.LgWrappingPanel
import lg.intellij.utils.LgStubNotifications
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Control Panel for Listing Generator Tool Window.
 * 
 * Phase 4: Full UI implementation with Kotlin UI DSL.
 * All buttons are functional but show stub notifications.
 * Data in selectors is hardcoded (mock).
 * 
 * Layout structure:
 * - Group 1: AI Contexts (template selection, generation, task input)
 * - Group 2: Adaptive Settings (modes, tags, target branch)
 * - Group 3: Inspect (section selection, included files, stats)
 * - Group 4: Tokenization Settings (library, encoder, context limit)
 * - Group 5: Utilities (config management, diagnostics)
 */
class LgControlPanel(
    private val project: Project
) : SimpleToolWindowPanel(
    true,   // vertical = true (toolbar at top)
    true    // borderless = true
) {
    
    private val stateService = project.service<LgPanelStateService>()
    
    // Mock data for Phase 4
    private val mockTemplates = listOf("default", "api-docs", "review")
    private val mockSections = listOf("all", "core", "tests")
    private val mockLibraries = listOf("tiktoken", "tokenizers", "sentencepiece")
    private val mockModes = listOf("planning", "development", "review")
    private val mockBranches = listOf("main", "develop", "feature/xyz")
    
    // Cell references for conditional visibility
    private lateinit var modeComboCell: Cell<ComboBox<String>>
    
    init {
        setContent(createScrollableContent())
        toolbar = createToolbar()
    }
    
    /**
     * Wraps the control panel in a scroll pane for proper vertical scrolling.
     */
    private fun createScrollableContent(): JComponent {
        val scrollPane = JBScrollPane(createControlPanel()).apply {
            border = null
            horizontalScrollBarPolicy = javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        }
        return scrollPane
    }
    
    /**
     * Creates a non-breakable label+component pair.
     * Label and component stay together when wrapping.
     */
    private fun createLabeledComponent(labelText: String, component: JComponent): JPanel {
        return JPanel(BorderLayout(2, 0)).apply {
            isOpaque = false
            add(JBLabel(labelText), BorderLayout.WEST)
            add(component, BorderLayout.CENTER)
        }
    }
    
    /**
     * Creates the main control panel with all UI groups.
     * 
     * Uses standard IntelliJ Platform spacing with JBUI.Borders for proper padding.
     * Groups use indent=false to align content with group titles.
     */
    private fun createControlPanel(): JComponent {
        return panel {
            // Group 1: AI Contexts
            group(LgBundle.message("control.group.ai.contexts"), indent = false) {
                createAiContextsSection()
            }
            
            // Group 2: Adaptive Settings
            group(LgBundle.message("control.group.adaptive.settings"), indent = false) {
                createAdaptiveSettingsSection()
            }
            
            // Group 3: Inspect
            group(LgBundle.message("control.group.inspect"), indent = false) {
                createInspectSection()
            }
            
            // Group 4: Tokenization Settings
            group(LgBundle.message("control.group.tokenization"), indent = false) {
                createTokenizationSection()
            }
            
        }.apply {
            // Add padding around the entire panel for better visual separation
            border = JBUI.Borders.empty(8, 12)
        }
    }
    
    /**
     * Group 1: AI Contexts
     * Template selector, task input, generation buttons.
     */
    private fun Panel.createAiContextsSection() {
        // Task text input (multi-line, expandable)
        row {
            val editorField = createTaskTextField()
            
            // Wrapper panel that will handle updateUI() to refresh color scheme and border on theme change
            val wrapperPanel = object : JPanel(BorderLayout()) {
                override fun updateUI() {
                    super.updateUI()
                    // Re-apply color scheme customization on theme change
                    val editor = editorField.editor
                    if (editor is EditorEx) {
                        editor.setBackgroundColor(null)
                        editor.colorsScheme = getEditorColorScheme(editor)
                        
                        // Re-create border to support theme change
                        editorField.border = DarculaEditorTextFieldBorder(
                            editorField,
                            editor
                        )
                    }
                }
            }.apply {
                add(editorField, BorderLayout.CENTER)
                isOpaque = false
            }
            
            // Manual binding
            editorField.addDocumentListener(object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    stateService.state.taskText = editorField.text
                }
            })
            
            cell(wrapperPanel)
                .align(AlignX.FILL)
        }
        
        // Template selector + buttons (adaptive flow layout)
        row {
            val flowPanel = LgWrappingPanel().apply {
                // Template ComboBox
                val templateCombo = ComboBox(mockTemplates.toTypedArray()).apply {
                    selectedItem = stateService.state.selectedTemplate
                    addActionListener {
                        stateService.state.selectedTemplate = selectedItem as? String
                    }
                }
                add(templateCombo)
                
                // Send to AI button
                add(JButton(LgBundle.message("control.btn.send.ai"), AllIcons.Actions.Execute).apply {
                    addActionListener {
                        LgStubNotifications.showNotImplemented(
                            project,
                            LgBundle.message("control.stub.send.ai"),
                            10
                        )
                    }
                })
                
                // Generate Context button
                add(JButton(LgBundle.message("control.btn.generate.context"), AllIcons.Actions.ShowCode).apply {
                    addActionListener {
                        LgStubNotifications.showNotImplemented(
                            project,
                            LgBundle.message("control.stub.generate.context"),
                            7
                        )
                    }
                })
                
                // Show Context Stats button
                add(JButton(LgBundle.message("control.btn.show.context.stats"), AllIcons.FileTypes.Csv).apply {
                    addActionListener {
                        LgStubNotifications.showNotImplemented(
                            project,
                            LgBundle.message("control.stub.show.context.stats"),
                            9
                        )
                    }
                })
            }
            
            cell(flowPanel)
                .align(AlignX.FILL)
        }.resizableRow()
    }
    
    /**
     * Group 2: Adaptive Settings
     * Mode selector, tags button, target branch (conditional).
     */
    private fun Panel.createAdaptiveSettingsSection() {
        // Mode selector
        row(LgBundle.message("control.mode.label")) {
            modeComboCell = comboBox(mockModes)
                .bindItem(
                    getter = { stateService.state.selectedMode },
                    setter = { stateService.state.selectedMode = it }
                )
        }
        
        // Target Branch selector (visible only when mode == "review")
        // Note: Simple implementation without ComponentPredicate for Phase 4
        // Will be improved in later phases if needed
        val targetBranchRow = row(LgBundle.message("control.target.branch.label")) {
            comboBox(mockBranches)
                .bindItem(
                    getter = { stateService.state.targetBranch },
                    setter = { stateService.state.targetBranch = it }
                )
        }
        
        // Update visibility based on mode changes
        modeComboCell.component.addActionListener {
            val isReview = modeComboCell.component.selectedItem == "review"
            targetBranchRow.visible(isReview)
        }
        
        // Set initial visibility
        targetBranchRow.visible(stateService.state.selectedMode == "review")
        
        // Configure Tags button
        row {
            cell(JButton(LgBundle.message("control.btn.configure.tags"), AllIcons.General.Filter).apply {
                addActionListener {
                    LgStubNotifications.showNotImplemented(
                        project,
                        LgBundle.message("control.stub.configure.tags"),
                        13
                    )
                }
            })
        }
    }
    
    /**
     * Group 3: Inspect
     * Section selector, inspection actions.
     */
    private fun Panel.createInspectSection() {
        // Section selector + action buttons (adaptive flow layout)
        row {
            val flowPanel = LgWrappingPanel().apply {
                // Section ComboBox
                val sectionCombo = ComboBox(mockSections.toTypedArray()).apply {
                    selectedItem = stateService.state.selectedSection
                    addActionListener {
                        stateService.state.selectedSection = selectedItem as? String
                    }
                }
                add(createLabeledComponent(LgBundle.message("control.section.label"), sectionCombo))
                
                // Show Included button
                add(JButton(LgBundle.message("control.btn.show.included"), AllIcons.Actions.ShowAsTree).apply {
                    addActionListener {
                        LgStubNotifications.showNotImplemented(
                            project,
                            LgBundle.message("control.stub.show.included"),
                            11
                        )
                    }
                })
                
                // Generate Listing button
                add(JButton(LgBundle.message("control.btn.generate.listing"), AllIcons.Actions.ShowCode).apply {
                    addActionListener {
                        LgStubNotifications.showNotImplemented(
                            project,
                            LgBundle.message("control.stub.generate.listing"),
                            7
                        )
                    }
                })
                
                // Show Stats button
                add(JButton(LgBundle.message("control.btn.show.stats"), AllIcons.FileTypes.Csv).apply {
                    addActionListener {
                        LgStubNotifications.showNotImplemented(
                            project,
                            LgBundle.message("control.stub.show.stats"),
                            9
                        )
                    }
                })
            }
            
            cell(flowPanel)
                .align(AlignX.FILL)
        }
    }
    
    /**
     * Group 4: Tokenization Settings
     * Library, encoder, context limit configuration.
     */
    private fun Panel.createTokenizationSection() {
        // Library selector + Encoder input + Context limit (adaptive flow layout)
        row {
            val flowPanel = LgWrappingPanel().apply {
                // Tokenizer Library ComboBox
                val libraryCombo = ComboBox(mockLibraries.toTypedArray()).apply {
                    selectedItem = stateService.state.tokenizerLib
                    addActionListener {
                        stateService.state.tokenizerLib = selectedItem as? String
                    }
                }
                add(createLabeledComponent(LgBundle.message("control.library.label"), libraryCombo))
                
                // Encoder TextField
                val encoderField = com.intellij.ui.components.JBTextField(20).apply {
                    text = stateService.state.encoder ?: ""
                    document.addDocumentListener(object : javax.swing.event.DocumentListener {
                        override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = update()
                        override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = update()
                        override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = update()
                        private fun update() {
                            stateService.state.encoder = text
                        }
                    })
                }
                add(createLabeledComponent(LgBundle.message("control.encoder.label"), encoderField))
                
                // Context Limit TextField
                val ctxLimitField = com.intellij.ui.components.JBTextField(10).apply {
                    text = stateService.state.ctxLimit.toString()
                    document.addDocumentListener(object : javax.swing.event.DocumentListener {
                        override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = update()
                        override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = update()
                        override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = update()
                        private fun update() {
                            stateService.state.ctxLimit = text.toIntOrNull()?.coerceIn(1000, 2_000_000) ?: 128000
                        }
                    })
                }
                add(createLabeledComponent(LgBundle.message("control.ctx.limit.label"), ctxLimitField))
            }
            
            cell(flowPanel)
                .align(AlignX.FILL)
        }
    }
    
    
    /**
     * Creates the toolbar with all actions grouped and separated.
     */
    private fun createToolbar(): JComponent {
        val actionGroup = DefaultActionGroup().apply {
            // Группа 1: Refresh
            add(object : AnAction(
                LgBundle.message("control.btn.refresh"),
                LgBundle.message("control.stub.refresh"),
                AllIcons.Actions.Refresh
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    LgStubNotifications.showNotImplemented(
                        project,
                        LgBundle.message("control.stub.refresh"),
                        5
                    )
                }
            })
            
            addSeparator()
            
            // Группа 2: Config Management
            add(object : AnAction(
                LgBundle.message("control.btn.create.config"),
                LgBundle.message("control.stub.create.config"),
                AllIcons.Actions.AddDirectory
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    LgStubNotifications.showNotImplemented(
                        project,
                        LgBundle.message("control.stub.create.config"),
                        15
                    )
                }
            })
            
            add(object : AnAction(
                LgBundle.message("control.btn.open.config"),
                LgBundle.message("control.stub.open.config"),
                AllIcons.Ide.ConfigFile
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    LgStubNotifications.showNotImplemented(
                        project,
                        LgBundle.message("control.stub.open.config"),
                        15
                    )
                }
            })
            
            addSeparator()
            
            // Группа 3: Diagnostics & Settings
            add(object : AnAction(
                LgBundle.message("control.btn.doctor"),
                LgBundle.message("control.stub.doctor"),
                AllIcons.Actions.Checked
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    LgStubNotifications.showNotImplemented(
                        project,
                        LgBundle.message("control.stub.doctor"),
                        14
                    )
                }
            })
            
            add(object : AnAction(
                LgBundle.message("control.btn.reset.cache"),
                LgBundle.message("control.stub.reset.cache"),
                AllIcons.Actions.GC
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    LgStubNotifications.showNotImplemented(
                        project,
                        LgBundle.message("control.stub.reset.cache"),
                        14
                    )
                }
            })
            
            add(object : AnAction(
                LgBundle.message("control.btn.settings"),
                LgBundle.message("control.stub.settings"),
                AllIcons.General.Settings
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    openSettings()
                }
            })
            
            addSeparator()
            
            // Группа 4: View Mode Toggle (заглушка для Фазы 11)
            add(object : AnAction(
                LgBundle.message("control.btn.toggle.view"),
                LgBundle.message("control.stub.toggle.view"),
                AllIcons.Actions.ShowAsTree
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    LgStubNotifications.showNotImplemented(
                        project,
                        LgBundle.message("control.stub.toggle.view"),
                        11
                    )
                }
            })
        }
        
        val toolbar = ActionManager.getInstance()
            .createActionToolbar("LgControlPanel", actionGroup, true)
        
        toolbar.targetComponent = this
        return toolbar.component
    }
    
    /**
     * Opens the settings dialog.
     */
    private fun openSettings() {
        ShowSettingsUtilImpl.showSettingsDialog(
            project,
            "lg.intellij.settings",
            null
        )
    }
    
    /**
     * Creates multi-line EditorTextField for task input.
     * Uses EditorTextFieldProvider with customizations for proper theming and borders.
     * Supports theme switching and focus indication.
     */
    private fun createTaskTextField(): EditorTextField {
        val features = mutableSetOf<EditorCustomization>()
        
        features.add(SoftWrapsEditorCustomization.ENABLED)
        features.add(AdditionalPageAtBottomEditorCustomization.DISABLED)
        
        // Color scheme customization for proper background and theme switching
        features.add(EditorCustomization { editor ->
            editor.setBackgroundColor(null) // use background from color scheme
            editor.colorsScheme = getEditorColorScheme(editor)

            // Add internal padding (contentInsets)
            editor.settings.additionalLinesCount = 0
            editor.settings.isAdditionalPageAtBottom = false

            // Set content insets for internal padding
            editor.contentComponent.border = BorderFactory.createEmptyBorder(4, 6, 4, 6)
        })
        
        val editorField = EditorTextFieldProvider.getInstance()
            .getEditorField(PlainTextLanguage.INSTANCE, project, features)
        
        editorField.setFontInheritedFromLAF(false)
        editorField.setPlaceholder(LgBundle.message("control.task.placeholder"))
        editorField.setShowPlaceholderWhenFocused(true)
        editorField.text = stateService.state.taskText ?: ""
        editorField.preferredSize = java.awt.Dimension(400, 60)
        
        // Add border with focus support and theme switching
        editorField.border = DarculaEditorTextFieldBorder(
            editorField,
            editorField.editor as? EditorEx
        )
        
        return editorField
    }
    
    /**
     * Gets proper color scheme for editor based on current UI theme.
     * Ensures editor colors match the current LaF (Light/Dark theme).
     */
    private fun getEditorColorScheme(editor: EditorEx): EditorColorsScheme {
        val isLaFDark = ColorUtil.isDark(UIUtil.getPanelBackground())
        val isEditorDark = EditorColorsManager.getInstance().isDarkEditor
        
        val colorsScheme = if (isLaFDark == isEditorDark) {
            EditorColorsManager.getInstance().globalScheme
        } else {
            EditorColorsManager.getInstance().schemeForCurrentUITheme
        }
        
        // Wrap in delegate to avoid editing global scheme
        val wrappedScheme = editor.createBoundColorSchemeDelegate(colorsScheme)
        wrappedScheme.editorFontSize = UISettingsUtils.getInstance().scaledEditorFontSize.toInt()
        
        return wrappedScheme
    }
}
