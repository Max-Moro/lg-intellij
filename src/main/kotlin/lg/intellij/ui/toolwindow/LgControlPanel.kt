package lg.intellij.ui.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import lg.intellij.LgBundle
import lg.intellij.actions.*
import lg.intellij.models.ModeSet
import lg.intellij.models.ModeSetsListSchema
import lg.intellij.models.TagSet
import lg.intellij.models.TagSetsListSchema
import lg.intellij.services.catalog.LgCatalogService
import lg.intellij.services.catalog.TokenizerCatalogService
import lg.intellij.services.state.LgPanelStateService
import lg.intellij.ui.components.LgTaskTextField
import lg.intellij.ui.components.LgTaskTextField.addChangeListener
import lg.intellij.ui.components.LgWrappingPanel
import lg.intellij.utils.LgStubNotifications
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

/**
 * Control Panel for Listing Generator Tool Window.
 * 
 * Integrates:
 * - LgCatalogService (sections/contexts/mode-sets/tag-sets)
 * - TokenizerCatalogService (libraries/encoders)
 * - Reactive updates через Flow collectors
 * - Auto-reload через VFS listener
 */
class LgControlPanel(
    private val project: Project
) : SimpleToolWindowPanel(
    true,   // vertical = true (toolbar at top)
    true    // borderless = true
), Disposable {
    
    private val stateService = project.service<LgPanelStateService>()
    private val catalogService = project.service<LgCatalogService>()
    private val tokenizerService = TokenizerCatalogService.getInstance()
    
    // Scope для coroutines (отменяется при dispose)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // UI component references для dynamic updates
    private lateinit var taskTextField: LgTaskTextField.TaskFieldWrapper
    private lateinit var templateCombo: ComboBox<String>
    private lateinit var sectionCombo: ComboBox<String>
    private lateinit var libraryCombo: ComboBox<String>
    private lateinit var encoderField: com.intellij.ui.components.JBTextField
    private lateinit var modeCombo: ComboBox<String>
    private lateinit var targetBranchRow: Row
    private lateinit var tagsButton: JButton
    
    // Mode-sets data (для dynamic rendering)
    private var currentModeSets: List<ModeSet> = emptyList()
    private var currentTagSets: List<TagSet> = emptyList()
    
    init {
        setContent(createScrollableContent())
        toolbar = createToolbar()
        
        // Запустить загрузку данных
        loadDataAsync()
        
        // Подписаться на updates
        subscribeToDataUpdates()
    }
    
    /**
     * Запускает асинхронную загрузку всех каталогов.
     */
    private fun loadDataAsync() {
        scope.launch {
            try {
                // Загрузить catalog data
                catalogService.loadAll()
                
                // Загрузить tokenizer libraries
                tokenizerService.loadLibraries(project)
                
                // Загрузить encoders для текущей библиотеки
                val currentLib = stateService.state.tokenizerLib ?: "tiktoken"
                tokenizerService.getEncoders(currentLib, project)
                
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LOG.error("Failed to load initial catalog data", e)
            }
        }
    }
    
    /**
     * Подписывается на Flow updates от catalog services.
     */
    private fun subscribeToDataUpdates() {
        // Sections
        scope.launch {
            catalogService.sections.collectLatest { sections ->
                withContext(Dispatchers.EDT) {
                    updateSectionsUI(sections)
                }
            }
        }
        
        // Contexts
        scope.launch {
            catalogService.contexts.collectLatest { contexts ->
                withContext(Dispatchers.EDT) {
                    updateContextsUI(contexts)
                }
            }
        }
        
        // Mode-sets
        scope.launch {
            catalogService.modeSets.collectLatest { modeSets ->
                withContext(Dispatchers.EDT) {
                    updateModeSetsUI(modeSets)
                }
            }
        }
        
        // Tag-sets
        scope.launch {
            catalogService.tagSets.collectLatest { tagSets ->
                withContext(Dispatchers.EDT) {
                    updateTagSetsUI(tagSets)
                }
            }
        }
        
        // Tokenizer libraries
        scope.launch {
            tokenizerService.libraries.collectLatest { libraries ->
                withContext(Dispatchers.EDT) {
                    updateLibrariesUI(libraries)
                }
            }
        }
        
        // Task text (from Stats Dialog or other sources)
        scope.launch {
            stateService.taskTextFlow.collectLatest { newText ->
                withContext(Dispatchers.EDT) {
                    updateTaskTextUI(newText)
                }
            }
        }
    }
    
    // =============== UI Updates ===============
    
    private fun updateSectionsUI(sections: List<String>) {
        if (!::sectionCombo.isInitialized) return
        
        val savedSection = stateService.state.selectedSection
        
        sectionCombo.removeAllItems()
        sections.forEach { sectionCombo.addItem(it) }
        
        // Restore selection from state
        if (!savedSection.isNullOrBlank() && savedSection in sections) {
            sectionCombo.selectedItem = savedSection
        } else if (sections.isNotEmpty()) {
            sectionCombo.selectedIndex = 0
        }
    }
    
    private fun updateContextsUI(contexts: List<String>) {
        if (!::templateCombo.isInitialized) return
        
        val savedTemplate = stateService.state.selectedTemplate
        
        templateCombo.removeAllItems()
        contexts.forEach { templateCombo.addItem(it) }
        
        // Restore selection from state
        if (!savedTemplate.isNullOrBlank() && savedTemplate in contexts) {
            templateCombo.selectedItem = savedTemplate
        } else if (contexts.isNotEmpty()) {
            templateCombo.selectedIndex = 0
        }
    }
    
    private fun updateModeSetsUI(modeSets: ModeSetsListSchema?) {
        if (modeSets == null) return
        
        currentModeSets = modeSets.modeSets
        
        // TODO Phase 13: Dynamic mode-sets rendering
        // Пока обновляем только один режим (dev-stage)
        if (currentModeSets.isNotEmpty() && ::modeCombo.isInitialized) {
            val firstModeSet = currentModeSets.first()
            val modes = firstModeSet.modes.map { it.id }
            
            modeCombo.removeAllItems()
            modes.forEach { modeCombo.addItem(it) }
            
            // Restore from saved state
            val savedMode = stateService.state.modes[firstModeSet.id]
            
            if (savedMode != null && savedMode in modes) {
                modeCombo.selectedItem = savedMode
            } else if (modes.isNotEmpty()) {
                modeCombo.selectedIndex = 0
                stateService.state.modes[firstModeSet.id] = modes[0]
            }
        }
        
        LOG.debug("Updated mode-sets UI: ${modeSets.modeSets.size} sets")
    }
    
    private fun updateTagSetsUI(tagSets: TagSetsListSchema?) {
        if (tagSets == null) return
        
        currentTagSets = tagSets.tagSets
        
        // Update tags button text (Phase 13)
        updateTagsButtonText()
        
        LOG.debug("Updated tag-sets UI: ${tagSets.tagSets.size} sets")
    }
    
    private fun updateLibrariesUI(libraries: List<String>) {
        if (!::libraryCombo.isInitialized) return
        
        val effectiveLib = stateService.getEffectiveTokenizerLib()
        
        libraryCombo.removeAllItems()
        libraries.forEach { libraryCombo.addItem(it) }
        
        if (effectiveLib in libraries) {
            libraryCombo.selectedItem = effectiveLib
        } else if (libraries.isNotEmpty()) {
            libraryCombo.selectedIndex = 0
            stateService.state.tokenizerLib = libraries[0]
        }
    }
    
    private fun updateTaskTextUI(newText: String) {
        if (!::taskTextField.isInitialized) return
        
        // Update UI only if text differs (avoid loops)
        val currentText = taskTextField.editorField.text
        if (currentText != newText) {
            taskTextField.editorField.text = newText
        }
    }
    
    // =============== UI Creation ===============
    
    private fun createScrollableContent(): JComponent {
        val scrollPane = JBScrollPane(createControlPanel()).apply {
            border = null
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
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
            border = JBUI.Borders.empty(8, 12)
        }
    }
    
    private fun Panel.createAiContextsSection() {
        // Task text input
        row {
            taskTextField = LgTaskTextField.create(
                project = project,
                initialText = stateService.state.taskText ?: "",
                placeholder = LgBundle.message("control.task.placeholder")
            )
            
            taskTextField.editorField.addChangeListener { newText ->
                stateService.updateTaskText(newText)
            }
            
            cell(taskTextField).align(AlignX.FILL)
        }
        
        // Template selector + buttons
        row {
            val flowPanel = LgWrappingPanel().apply {
                // Template ComboBox
                templateCombo = ComboBox<String>().apply {
                    addActionListener {
                        val selected = selectedItem as? String
                        if (selected != null) {
                            stateService.state.selectedTemplate = selected
                        }
                    }
                }
                add(templateCombo)
                
                // Send to AI button
                add(JButton(LgBundle.message("control.btn.send.ai"), AllIcons.Actions.Execute).apply {
                    addActionListener {
                        LgStubNotifications.showNotImplemented(project, LgBundle.message("control.stub.send.ai"), 10)
                    }
                })
                
                // Generate Context button
                add(JButton(LgBundle.message("control.btn.generate.context"), AllIcons.Actions.ShowCode).apply {
                    addActionListener {
                        val action = LgGenerateContextAction()
                        val dataContext = DataManager.getInstance().getDataContext(this@LgControlPanel)
                        val event = AnActionEvent.createEvent(
                            action,
                            dataContext,
                            null,
                            ActionPlaces.TOOLWINDOW_CONTENT,
                            ActionUiKind.NONE,
                            null
                        )
                        action.actionPerformed(event)
                    }
                })
                
                // Show Context Stats button
                add(object : JButton(LgBundle.message("control.btn.show.context.stats"), AllIcons.Actions.ListFiles) {
                    override fun isDefaultButton(): Boolean = true
                }.apply {
                    addActionListener {
                        val action = LgShowContextStatsAction()
                        val dataContext = DataManager.getInstance().getDataContext(this@LgControlPanel)
                        val event = AnActionEvent.createEvent(
                            action,
                            dataContext,
                            null,
                            ActionPlaces.TOOLWINDOW_CONTENT,
                            ActionUiKind.NONE,
                            null
                        )
                        action.actionPerformed(event)
                    }
                })
            }
            
            cell(flowPanel).align(AlignX.FILL)
        }.resizableRow()
    }
    
    private fun Panel.createAdaptiveSettingsSection() {
        // Mode selector (single for now, динамический в Phase 13)
        row(LgBundle.message("control.mode.label")) {
            modeCombo = ComboBox<String>().apply {
                // Will be populated by updateModeSetsUI when data loads
                addActionListener {
                    val selectedMode = selectedItem as? String
                    if (selectedMode != null && currentModeSets.isNotEmpty()) {
                        val firstModeSet = currentModeSets.first()
                        stateService.state.modes[firstModeSet.id] = selectedMode
                        
                        // Update target branch visibility
                        val isReview = selectedMode == "review"
                        targetBranchRow.visible(isReview)
                    }
                }
            }
            cell(modeCombo)
        }
        
        // Target Branch selector
        targetBranchRow = row(LgBundle.message("control.target.branch.label")) {
            comboBox(emptyList<String>())
                .bindItem(
                    getter = { stateService.state.targetBranch },
                    setter = { stateService.state.targetBranch = it }
                )
        }
        
        // Initial visibility state based on saved mode
        val firstModeSetId = currentModeSets.firstOrNull()?.id
        val savedMode = if (firstModeSetId != null) stateService.state.modes[firstModeSetId] else null
        targetBranchRow.visible(savedMode == "review")
        
        // Configure Tags button (Phase 13)
        row {
            tagsButton = JButton(LgBundle.message("control.btn.configure.tags"), AllIcons.General.Filter).apply {
                addActionListener {
                    val action = LgConfigureTagsAction()
                    val dataContext = DataManager.getInstance().getDataContext(this@LgControlPanel)
                    val event = AnActionEvent.createEvent(
                        action,
                        dataContext,
                        null,
                        ActionPlaces.TOOLWINDOW_CONTENT,
                        ActionUiKind.NONE,
                        null
                    )
                    action.actionPerformed(event)
                    
                    // Update button text after dialog closed
                    updateTagsButtonText()
                }
            }
            cell(tagsButton)
        }
    }
    
    private fun Panel.createInspectSection() {
        row {
            val flowPanel = LgWrappingPanel().apply {
                // Section ComboBox
                sectionCombo = ComboBox<String>().apply {
                    addActionListener {
                        val selected = selectedItem as? String
                        if (selected != null) {
                            stateService.state.selectedSection = selected
                        }
                    }
                }
                add(createLabeledComponent(LgBundle.message("control.section.label"), sectionCombo))
                
                // Show Included button
                add(JButton(LgBundle.message("control.btn.show.included"), AllIcons.Actions.ShowAsTree).apply {
                    addActionListener {
                        val action = LgShowIncludedFilesAction()
                        val dataContext = DataManager.getInstance().getDataContext(this@LgControlPanel)
                        val event = AnActionEvent.createEvent(
                            action,
                            dataContext,
                            null,
                            ActionPlaces.TOOLWINDOW_CONTENT,
                            ActionUiKind.NONE,
                            null
                        )
                        action.actionPerformed(event)
                    }
                })

                // Generate Listing button
                add(JButton(LgBundle.message("control.btn.generate.listing"), AllIcons.Actions.ShowCode).apply {
                    addActionListener {
                        val action = LgGenerateListingAction()
                        val dataContext = DataManager.getInstance().getDataContext(this@LgControlPanel)
                        val event = AnActionEvent.createEvent(
                            action,
                            dataContext,
                            null,
                            ActionPlaces.TOOLWINDOW_CONTENT,
                            ActionUiKind.NONE,
                            null
                        )
                        action.actionPerformed(event)
                    }
                })
                
                // Show Stats button
                add(JButton(LgBundle.message("control.btn.show.stats"), AllIcons.Actions.ListFiles).apply {
                    addActionListener {
                        val action = LgShowSectionStatsAction()
                        val dataContext = DataManager.getInstance().getDataContext(this@LgControlPanel)
                        val event = AnActionEvent.createEvent(
                            action,
                            dataContext,
                            null,
                            ActionPlaces.TOOLWINDOW_CONTENT,
                            ActionUiKind.NONE,
                            null
                        )
                        action.actionPerformed(event)
                    }
                })
            }
            
            cell(flowPanel).align(AlignX.FILL)
        }
    }
    
    private fun Panel.createTokenizationSection() {
        row {
            val flowPanel = LgWrappingPanel().apply {
                // Library ComboBox
                libraryCombo = ComboBox<String>().apply {
                    addActionListener {
                        val newLib = selectedItem as? String
                        if (newLib != null) {
                            stateService.state.tokenizerLib = newLib
                            
                            // Reload encoders для новой библиотеки
                            scope.launch {
                                tokenizerService.getEncoders(newLib, project)
                            }
                        }
                    }
                }
                add(createLabeledComponent(LgBundle.message("control.library.label"), libraryCombo))
                
                // Encoder TextField (custom values supported)
                encoderField = com.intellij.ui.components.JBTextField(20).apply {
                    // Use effective encoder (with fallback to application defaults)
                    text = stateService.getEffectiveEncoder()
                    
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
                    // Use effective context limit (with fallback to application defaults)
                    val effectiveLimit = stateService.getEffectiveContextLimit()
                    text = effectiveLimit.toString()
                    
                    document.addDocumentListener(object : javax.swing.event.DocumentListener {
                        override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = update()
                        override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = update()
                        override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = update()
                        private fun update() {
                            val parsed = text.toIntOrNull()?.coerceIn(1000, 2_000_000)
                            if (parsed != null) {
                                stateService.state.ctxLimit = parsed
                            }
                        }
                    })
                }
                add(createLabeledComponent(LgBundle.message("control.ctx.limit.label"), ctxLimitField))
            }
            
            cell(flowPanel).align(AlignX.FILL)
        }
    }
    
    private fun createToolbar(): JComponent {
        val actionGroup = DefaultActionGroup().apply {
            // Refresh
            add(LgRefreshCatalogsAction())
            
            addSeparator()
            
            // Config Management
            add(object : AnAction(
                LgBundle.message("control.btn.create.config"),
                LgBundle.message("control.stub.create.config"),
                AllIcons.Actions.AddDirectory
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    LgStubNotifications.showNotImplemented(project, LgBundle.message("control.stub.create.config"), 15)
                }
            })
            
            add(object : AnAction(
                LgBundle.message("control.btn.open.config"),
                LgBundle.message("control.stub.open.config"),
                AllIcons.Ide.ConfigFile
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    LgStubNotifications.showNotImplemented(project, LgBundle.message("control.stub.open.config"), 15)
                }
            })
            
            addSeparator()
            
            // Diagnostics
            add(object : AnAction(
                LgBundle.message("control.btn.doctor"),
                LgBundle.message("control.stub.doctor"),
                AllIcons.Actions.Checked
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    LgStubNotifications.showNotImplemented(project, LgBundle.message("control.stub.doctor"), 14)
                }
            })
            
            add(object : AnAction(
                LgBundle.message("control.btn.reset.cache"),
                LgBundle.message("control.stub.reset.cache"),
                AllIcons.Actions.GC
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    LgStubNotifications.showNotImplemented(project, LgBundle.message("control.stub.reset.cache"), 14)
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

            // View Mode Toggle
            add(LgToggleTreeViewModeAction())
        }
        
        val toolbar = ActionManager.getInstance().createActionToolbar("LgControlPanel", actionGroup, true)
        toolbar.targetComponent = this
        return toolbar.component
    }
    
    private fun openSettings() {
        ShowSettingsUtilImpl.showSettingsDialog(project, "lg.intellij.settings", null)
    }
    
    /**
     * Updates tags button text to show selection count.
     */
    private fun updateTagsButtonText() {
        if (!::tagsButton.isInitialized) return
        
        val selectedCount = stateService.state.tags.values.sumOf { it.size }
        if (selectedCount > 0) {
            tagsButton.text = LgBundle.message("control.btn.configure.tags.with.count", selectedCount)
        } else {
            tagsButton.text = LgBundle.message("control.btn.configure.tags")
        }
    }

    override fun dispose() {
        scope.cancel()
        LOG.debug("Control Panel disposed")
    }
    
    companion object {
        private val LOG = logger<LgControlPanel>()
    }
}

