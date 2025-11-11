package lg.intellij.ui.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import lg.intellij.LgBundle
import lg.intellij.actions.*
import lg.intellij.models.TagSet
import lg.intellij.models.TagSetsListSchema
import lg.intellij.services.catalog.LgCatalogService
import lg.intellij.services.catalog.TokenizerCatalogService
import lg.intellij.services.state.LgPanelStateService
import lg.intellij.ui.components.LgEncoderCompletionField
import lg.intellij.ui.components.LgLabeledComponent
import lg.intellij.ui.components.LgTaskTextField
import lg.intellij.ui.components.LgTaskTextField.addChangeListener
import lg.intellij.ui.components.LgWrappingPanel
import javax.swing.JButton
import javax.swing.JComponent
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
    private lateinit var encoderField: LgEncoderCompletionField
    private lateinit var tagsButton: JButton
    private var cliSettingsGroup: JComponent? = null

    // Modes panel (self-contained, manages own state and data)
    private val modesPanel = LgModeSetsPanel(project, this)

    // Tag-sets data (для dynamic rendering)
    private var currentTagSets: List<TagSet> = emptyList()
    
    init {
        setContent(createScrollableContent())
        toolbar = createToolbar()

        // Запустить загрузку данных
        loadDataAsync()

        // Подписаться на updates
        subscribeToDataUpdates()

        // Подписаться на изменения Settings (для обновления CLI Settings visibility)
        subscribeToSettingsChanges()
    }
    
    /**
     * Запускает асинхронную загрузку всех каталогов с параллельным выполнением CLI запросов.
     */
    private fun loadDataAsync() {
        scope.launch {
            try {
                // Получаем текущую библиотеку токенизации из состояния
                val currentLib = stateService.state.tokenizerLib!!

                // Параллельная загрузка catalog и tokenizer данных
                coroutineScope {
                    launch { catalogService.loadAll() }
                    launch { tokenizerService.loadLibraries(project) }
                    launch { tokenizerService.getEncoders(currentLib, project) }
                }

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

    /**
     * Подписывается на изменения Settings для динамического обновления UI.
     */
    private fun subscribeToSettingsChanges() {
        ApplicationManager.getApplication().messageBus
            .connect(this)
            .subscribe(lg.intellij.listeners.LgSettingsChangeListener.TOPIC,
                object : lg.intellij.listeners.LgSettingsChangeListener {
                    override fun settingsChanged() {
                        rebuildUI()
                    }
                })
    }

    /**
     * Обновляет видимость CLI Settings блока без пересоздания UI.
     */
    private fun rebuildUI() {
        ApplicationManager.getApplication().invokeLater {
            cliSettingsGroup?.isVisible = shouldShowCliSettings()
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

    private fun updateTagSetsUI(tagSets: TagSetsListSchema?) {
        if (tagSets == null) return
        
        currentTagSets = tagSets.tagSets

        // Update tags button text
        updateTagsButtonText()
        
        LOG.debug("Updated tag-sets UI: ${tagSets.tagSets.size} sets")
    }
    
    private fun updateLibrariesUI(libraries: List<String>) {
        if (!::libraryCombo.isInitialized) return

        val effectiveLib = stateService.state.tokenizerLib

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
    
    
    private fun createControlPanel(): JComponent {
        // Create CLI settings panel separately for visibility management
        cliSettingsGroup = panel {
            group(LgBundle.message("control.group.cli.settings"), indent = false) {
                row {
                    val panel = LgCliSettingsPanel(project).createUI()
                    cell(panel).align(AlignX.FILL)
                }
            }
        }.apply {
            isVisible = shouldShowCliSettings()
        }

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

            // Group 5: CLI Settings (embedded as pre-created component)
            row {
                cell(cliSettingsGroup!!).align(AlignX.FILL)
            }

        }.apply {
            border = JBUI.Borders.empty(8, 12)
        }
    }

    private fun shouldShowCliSettings(): Boolean {
        val settings = lg.intellij.services.state.LgSettingsService.getInstance()
        val providerId = settings.state.aiProvider
        // Show for CLI-based providers
        val cliProviders = listOf("claude.cli")
        return providerId in cliProviders
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
                        val action = LgSendToAiAction()
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
        // Single row with modes panel
        row {
            val modesUI = modesPanel.createUI()
            cell(modesUI)
                .align(AlignX.FILL)
        }
        
        // Configure Tags button
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
                add(LgLabeledComponent.create(LgBundle.message("control.section.label"), sectionCombo))
                
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
            val flowPanel = LgWrappingPanel(hgap = 16).apply {
                // Library ComboBox
                libraryCombo = ComboBox<String>().apply {
                    addActionListener {
                        val newLib = selectedItem as? String
                        if (newLib != null) {
                            stateService.state.tokenizerLib = newLib
                            
                            // Update encoder field library (triggers reload of suggestions)
                            if (::encoderField.isInitialized) {
                                encoderField.setLibrary(newLib)
                            }
                        }
                    }
                }
                add(LgLabeledComponent.create(LgBundle.message("control.library.label"), libraryCombo))
                
                // Encoder Completion Field (with auto-suggestions and custom values)
                encoderField = LgEncoderCompletionField(project, this@LgControlPanel).apply {
                    // Set initial library
                    val initialLib = stateService.state.tokenizerLib!!
                    setLibrary(initialLib)

                    // Set initial value (from state)
                    text = stateService.state.encoder

                    // Update state when text changes (supports custom values)
                    whenTextChangedFromUi(this@LgControlPanel) { newText ->
                        stateService.state.encoder = newText
                    }
                }
                add(LgLabeledComponent.create(LgBundle.message("control.encoder.label"), encoderField))

                // Context Limit TextField
                val ctxLimitField = com.intellij.ui.components.JBTextField(10).apply {
                    // Use context limit with default from delegate
                    val effectiveLimit = stateService.state.ctxLimit
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
                add(LgLabeledComponent.create(LgBundle.message("control.ctx.limit.label"), ctxLimitField))
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
            add(LgCreateStarterConfigAction())
            add(LgOpenConfigAction())
            
            addSeparator()
            
            // Diagnostics
            add(LgRunDoctorAction())
            add(LgResetCacheAction())
            
            add(object : AnAction(
                LgBundle.message("action.settings.text"),
                LgBundle.message("action.settings.description"),
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

