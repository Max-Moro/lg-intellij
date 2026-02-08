package lg.intellij.ui.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import lg.intellij.LgBundle
import lg.intellij.actions.*
import lg.intellij.statepce.LgCoordinatorService
import lg.intellij.ai.providers.claudecli.ClaudeIntegrationMethod
import lg.intellij.ai.providers.claudecli.ClaudeModel
import lg.intellij.ai.providers.codexcli.CodexReasoningEffort
import lg.intellij.models.ShellType
import lg.intellij.ai.AiIntegrationService
import lg.intellij.ai.FieldCommand
import lg.intellij.ai.FieldOption
import lg.intellij.ai.FieldType
import lg.intellij.ai.providers.claudecli.SelectClaudeMethod
import lg.intellij.ai.providers.claudecli.SelectClaudeModel
import lg.intellij.ai.providers.codexcli.SelectCodexReasoning
import lg.intellij.statepce.PCEState
import lg.intellij.statepce.PCEStateCoordinator
import lg.intellij.statepce.PCEStateStore
import lg.intellij.ai.ProviderInfo
import lg.intellij.statepce.domains.*
import lg.intellij.ui.components.LgEncoderCompletionField
import lg.intellij.ui.components.LgLabeledComponent
import lg.intellij.ui.components.LgTaskTextField
import lg.intellij.ui.components.LgTaskTextField.addChangeListener
import lg.intellij.ui.components.LgWrappingPanel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import java.awt.BorderLayout

/**
 * Control Panel for Listing Generator Tool Window.
 *
 * Command-driven architecture:
 * - Subscribes to PCEStateStore for all state updates
 * - Dispatches commands via PCEStateCoordinator for all user interactions
 * - Dynamic provider settings UI from ProviderSettingsModule contributions
 */
class LgControlPanel(
    private val project: Project
) : SimpleToolWindowPanel(
    true,   // vertical = true (toolbar at top)
    true    // borderless = true
), Disposable {

    private val coordinator: PCEStateCoordinator = LgCoordinatorService.getInstance(project).coordinator
    private val store: PCEStateStore = PCEStateStore.getInstance(project)

    // Coroutine scope (cancelled on dispose)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // UI component references for dynamic updates
    private lateinit var taskTextField: LgTaskTextField.TaskFieldWrapper
    private lateinit var providerCombo: ComboBox<ProviderInfo>
    private lateinit var templateCombo: ComboBox<String>
    private lateinit var sectionCombo: ComboBox<String>
    private lateinit var libraryCombo: ComboBox<String>
    private lateinit var encoderField: LgEncoderCompletionField
    private lateinit var ctxLimitField: JBTextField
    private lateinit var tagsButton: JButton
    private var providerSettingsContainer: JPanel? = null

    // Modes panel (self-contained, subscribes to store)
    private val modesPanel = LgModeSetsPanel(coordinator, store, this)

    // Track last state for diff-based updates
    private var lastState: PCEState? = null

    // Suppresses dispatch during programmatic UI updates
    private var suppressDispatch = false

    // Loading overlay panel
    private lateinit var loadingPanel: JBLoadingPanel

    init {
        setContent(createScrollableContent())
        toolbar = createToolbar()

        // Subscribe to state changes
        subscribeToStateUpdates()

        // Subscribe to loading state changes
        subscribeToMetaUpdates()

        // Dispatch Initialize command to start loading
        scope.launch {
            coordinator.dispatch(Initialize.create())
        }
    }

    /**
     * Subscribes to PCEState changes from the store.
     */
    private fun subscribeToStateUpdates() {
        val unsubscribe = store.subscribe { state ->
            ApplicationManager.getApplication().invokeLater {
                updateUI(state)
            }
        }

        Disposer.register(this) { unsubscribe() }
    }

    /**
     * Subscribes to coordinator meta changes (loading state).
     * Shows/hides loading overlay based on async operation status.
     */
    private fun subscribeToMetaUpdates() {
        val unsubscribe = coordinator.subscribeToMeta { meta ->
            ApplicationManager.getApplication().invokeLater {
                if (meta.isLoading) {
                    loadingPanel.startLoading()
                } else {
                    loadingPanel.stopLoading()
                }
            }
        }

        Disposer.register(this) { unsubscribe() }
    }

    // =============== UI Updates ===============

    /**
     * Updates all UI components from PCEState.
     * Uses diff-based approach to minimize unnecessary updates.
     */
    private fun updateUI(state: PCEState) {
        suppressDispatch = true
        try {
            val prev = lastState

            if (prev == null || prev.environment.providers != state.environment.providers
                || prev.persistent.providerId != state.persistent.providerId) {
                updateProvidersUI(state)
            }

            if (prev == null || prev.configuration.contexts != state.configuration.contexts
                || prev.persistent.template != state.persistent.template) {
                updateContextsUI(state)
            }

            if (prev == null || prev.configuration.sections != state.configuration.sections
                || prev.persistent.section != state.persistent.section) {
                updateSectionsUI(state)
            }

            if (prev == null || prev.configuration.tokenizerLibs != state.configuration.tokenizerLibs
                || prev.persistent.tokenizerLib != state.persistent.tokenizerLib) {
                updateLibrariesUI(state)
            }

            if (prev == null || prev.persistent.encoder != state.persistent.encoder) {
                updateEncoderUI(state)
            }

            if (prev == null || prev.persistent.ctxLimit != state.persistent.ctxLimit) {
                updateCtxLimitUI(state)
            }

            if (prev == null || prev.persistent.taskText != state.persistent.taskText) {
                updateTaskTextUI(state)
            }

            if (prev == null || prev.persistent.tagsByContext != state.persistent.tagsByContext
                || prev.persistent.template != state.persistent.template) {
                updateTagsButtonText(state)
            }

            if (prev == null || prev.persistent.providerId != state.persistent.providerId) {
                updateProviderSettingsUI(state)
            }

            lastState = state
        } finally {
            suppressDispatch = false
        }
    }

    private fun updateProvidersUI(state: PCEState) {
        if (!::providerCombo.isInitialized) return

        val providers = state.environment.providers
        val selectedId = state.persistent.providerId

        providerCombo.removeAllItems()
        providers.forEach { providerCombo.addItem(it) }

        val selectedProvider = providers.find { it.id == selectedId }
        if (selectedProvider != null) {
            providerCombo.selectedItem = selectedProvider
        }
    }

    private fun updateContextsUI(state: PCEState) {
        if (!::templateCombo.isInitialized) return

        val contexts = state.configuration.contexts
        val selectedTemplate = state.persistent.template

        templateCombo.removeAllItems()
        contexts.forEach { templateCombo.addItem(it) }

        if (selectedTemplate.isNotBlank()) {
            templateCombo.selectedItem = selectedTemplate
        }
    }

    private fun updateSectionsUI(state: PCEState) {
        if (!::sectionCombo.isInitialized) return

        val sectionNames = state.configuration.sections.map { it.name }
        val selectedSection = state.persistent.section

        sectionCombo.removeAllItems()
        sectionNames.forEach { sectionCombo.addItem(it) }

        if (selectedSection.isNotBlank()) {
            sectionCombo.selectedItem = selectedSection
        }
    }

    private fun updateLibrariesUI(state: PCEState) {
        if (!::libraryCombo.isInitialized) return

        val libraries = state.configuration.tokenizerLibs
        val selectedLib = state.persistent.tokenizerLib

        libraryCombo.removeAllItems()
        libraries.forEach { libraryCombo.addItem(it) }

        if (selectedLib.isNotBlank()) {
            libraryCombo.selectedItem = selectedLib
        }
    }

    private fun updateEncoderUI(state: PCEState) {
        if (!::encoderField.isInitialized) return

        val currentLib = state.persistent.tokenizerLib
        encoderField.setLibrary(currentLib)

        if (encoderField.text != state.persistent.encoder) {
            encoderField.text = state.persistent.encoder
        }
    }

    private fun updateCtxLimitUI(state: PCEState) {
        if (!::ctxLimitField.isInitialized) return

        val limitStr = state.persistent.ctxLimit.toString()
        if (ctxLimitField.text != limitStr) {
            ctxLimitField.text = limitStr
        }
    }

    private fun updateTaskTextUI(state: PCEState) {
        if (!::taskTextField.isInitialized) return

        val currentText = taskTextField.editorField.text
        if (currentText != state.persistent.taskText) {
            taskTextField.editorField.text = state.persistent.taskText
        }
    }

    private fun updateTagsButtonText(state: PCEState) {
        if (!::tagsButton.isInitialized) return

        val ctx = state.persistent.template
        val currentTags = state.persistent.tagsByContext[ctx] ?: emptyMap()
        val selectedCount = currentTags.values.sumOf { it.size }

        if (selectedCount > 0) {
            tagsButton.text = LgBundle.message("control.btn.configure.tags.with.count", selectedCount)
        } else {
            tagsButton.text = LgBundle.message("control.btn.configure.tags")
        }
    }

    /**
     * Renders dynamic provider settings from ProviderSettingsModule contributions.
     * Replaces the old hardcoded LgCliSettingsPanel.
     */
    private fun updateProviderSettingsUI(state: PCEState) {
        val container = providerSettingsContainer ?: return

        val aiService = AiIntegrationService.getInstance()
        val contributions = aiService.getAllSettingsModules()
            .map { it.buildContribution(state) }
            .filter { it.visible }

        // Also check if provider is CLI-based for scope/shell fields
        val isCliProvider = state.persistent.providerId.endsWith(".cli")

        if (!isCliProvider && contributions.isEmpty()) {
            container.isVisible = false
            return
        }

        container.removeAll()

        val settingsPanel = panel {
            // CLI scope and shell (for all CLI providers)
            if (isCliProvider) {
                row {
                    val flowPanel = LgWrappingPanel(hgap = 16).apply {
                        // Scope TextField
                        val scopeField = JBTextField(10).apply {
                            text = state.persistent.cliScope
                            document.addDocumentListener(object : DocumentListener {
                                override fun insertUpdate(e: DocumentEvent?) = update()
                                override fun removeUpdate(e: DocumentEvent?) = update()
                                override fun changedUpdate(e: DocumentEvent?) = update()
                                private fun update() {
                                    if (!suppressDispatch) {
                                        scope.launch {
                                            coordinator.dispatch(
                                                SetCliScope.create(text)
                                            )
                                        }
                                    }
                                }
                            })
                        }
                        add(LgLabeledComponent.create(
                            LgBundle.message("control.cli.scope.label"), scopeField
                        ))

                        // Shell ComboBox
                        val shellDescriptors = ShellType.getAvailableShells()
                        val shellCombo = ComboBox(shellDescriptors.toTypedArray()).apply {
                            selectedItem = shellDescriptors.find { it.id == state.persistent.cliShell }
                            addActionListener {
                                if (!suppressDispatch) {
                                    val selected = selectedItem as? lg.intellij.models.ShellDescriptor
                                    if (selected != null) {
                                        scope.launch {
                                            coordinator.dispatch(
                                                SelectCliShell.create(selected.id)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        add(LgLabeledComponent.create(
                            LgBundle.message("control.cli.shell.label"), shellCombo
                        ))
                    }
                    cell(flowPanel).align(AlignX.FILL)
                }
            }

            // Dynamic provider settings from contributions
            for (contrib in contributions) {
                row {
                    val flowPanel = LgWrappingPanel(hgap = 16).apply {
                        for (field in contrib.fields) {
                            when (field.type) {
                                FieldType.SELECT -> {
                                    val options = field.options ?: emptyList()
                                    val combo = ComboBox(options.toTypedArray()).apply {
                                        renderer = SimpleListCellRenderer.create { label, value, _ ->
                                            label.text = value?.label ?: ""
                                            if (value?.description != null) {
                                                label.toolTipText = value.description
                                            }
                                        }
                                        // Select current value
                                        val current = options.find { it.value == field.value }
                                        if (current != null) selectedItem = current

                                        addActionListener {
                                            if (!suppressDispatch) {
                                                val selected = selectedItem as? FieldOption
                                                if (selected != null) {
                                                    dispatchProviderSettingsCommand(field.command, selected.value)
                                                }
                                            }
                                        }
                                    }
                                    add(LgLabeledComponent.create(field.label + ":", combo))
                                }
                                FieldType.TEXT -> {
                                    val textField = JBTextField(field.value, 10).apply {
                                        document.addDocumentListener(object : DocumentListener {
                                            override fun insertUpdate(e: DocumentEvent?) = update()
                                            override fun removeUpdate(e: DocumentEvent?) = update()
                                            override fun changedUpdate(e: DocumentEvent?) = update()
                                            private fun update() {
                                                if (!suppressDispatch) {
                                                    dispatchProviderSettingsCommand(field.command, text)
                                                }
                                            }
                                        })
                                    }
                                    add(LgLabeledComponent.create(field.label + ":", textField))
                                }
                            }
                        }
                    }
                    cell(flowPanel).align(AlignX.FILL)
                }
            }
        }

        container.add(settingsPanel, BorderLayout.CENTER)
        container.isVisible = true
        container.revalidate()
        container.repaint()
    }

    /**
     * Dispatches a typed command from FieldCommand descriptor.
     * Maps command type strings to actual typed command instances.
     */
    private fun dispatchProviderSettingsCommand(cmd: FieldCommand, value: String) {
        scope.launch {
            when (cmd.type) {
                SelectClaudeModel.type -> {
                    val model = ClaudeModel.entries.find { it.name == value } ?: return@launch
                    coordinator.dispatch(SelectClaudeModel.create(model))
                }
                SelectClaudeMethod.type -> {
                    val method = ClaudeIntegrationMethod.entries.find { it.name == value } ?: return@launch
                    coordinator.dispatch(SelectClaudeMethod.create(method))
                }
                SelectCodexReasoning.type -> {
                    val effort = CodexReasoningEffort.entries.find { it.name == value } ?: return@launch
                    coordinator.dispatch(SelectCodexReasoning.create(effort))
                }
                else -> {
                    LOG.warn("Unknown provider settings command: ${cmd.type}")
                }
            }
        }
    }

    // =============== UI Creation ===============

    private fun createScrollableContent(): JComponent {
        val scrollPane = JBScrollPane(createControlPanel()).apply {
            border = null
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        }

        loadingPanel = JBLoadingPanel(BorderLayout(), this).apply {
            add(scrollPane, BorderLayout.CENTER)
        }

        return loadingPanel
    }

    private fun createControlPanel(): JComponent {
        // Provider settings container (managed dynamically)
        providerSettingsContainer = JPanel(BorderLayout()).apply {
            isVisible = false
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

            // Group 5: CLI / Provider Settings (dynamic)
            group(LgBundle.message("control.group.cli.settings"), indent = false) {
                row {
                    cell(providerSettingsContainer!!).align(AlignX.FILL)
                }
            }

        }.apply {
            border = JBUI.Borders.empty(8, 12)
        }
    }

    private fun Panel.createAiContextsSection() {
        // Context selector row
        row {
            templateCombo = ComboBox<String>().apply {
                renderer = SimpleListCellRenderer.create { label, value, _ ->
                    label.text = if (value != null && value.endsWith("/_")) value.dropLast(2) else value ?: ""
                }
                addActionListener {
                    if (!suppressDispatch) {
                        val selected = selectedItem as? String
                        if (selected != null) {
                            scope.launch {
                                coordinator.dispatch(
                                    SelectContext.create(selected)
                                )
                            }
                        }
                    }
                }
            }
            cell(templateCombo).align(AlignX.FILL)
        }

        // Task text input
        row {
            taskTextField = LgTaskTextField.create(
                project = project,
                initialText = "",
                placeholder = LgBundle.message("control.task.placeholder")
            )

            taskTextField.editorField.addChangeListener { newText ->
                if (!suppressDispatch) {
                    scope.launch {
                        coordinator.dispatch(
                            SetTask.create(newText)
                        )
                    }
                }
            }

            cell(taskTextField).align(AlignX.FILL)
        }

        // Provider selector + buttons
        row {
            val flowPanel = LgWrappingPanel().apply {
                // Provider ComboBox
                providerCombo = ComboBox<ProviderInfo>().apply {
                    renderer = SimpleListCellRenderer.create { label, value, _ ->
                        label.text = value?.name ?: ""
                    }
                    addActionListener {
                        if (!suppressDispatch) {
                            val selected = selectedItem as? ProviderInfo
                            if (selected != null) {
                                scope.launch {
                                    coordinator.dispatch(
                                        SelectProvider.create(selected.id)
                                    )
                                }
                            }
                        }
                    }
                }
                add(providerCombo)

                // Send to AI button
                add(JButton(LgBundle.message("control.btn.send.ai"), AllIcons.Actions.Execute).apply {
                    addActionListener {
                        LgSendToAiAction().performSafely(this@LgControlPanel)
                    }
                })

                // Generate Context button
                add(JButton(LgBundle.message("control.btn.generate.context"), AllIcons.Actions.ShowCode).apply {
                    addActionListener {
                        LgGenerateContextAction().performSafely(this@LgControlPanel)
                    }
                })

                // Show Context Stats button
                add(object : JButton(LgBundle.message("control.btn.show.context.stats"), AllIcons.Actions.ListFiles) {
                    override fun isDefaultButton(): Boolean = true
                }.apply {
                    addActionListener {
                        LgShowContextStatsAction().performSafely(this@LgControlPanel)
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
            cell(modesUI).align(AlignX.FILL)
        }

        // Configure Tags button
        row {
            tagsButton = JButton(LgBundle.message("control.btn.configure.tags"), AllIcons.General.Filter).apply {
                addActionListener {
                    LgConfigureTagsAction().performSafely(this@LgControlPanel)

                    // Update button text after dialog closed
                    val state = store.getBusinessState()
                    updateTagsButtonText(state)
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
                        if (!suppressDispatch) {
                            val selected = selectedItem as? String
                            if (selected != null) {
                                scope.launch {
                                    coordinator.dispatch(
                                        SelectSection.create(selected)
                                    )
                                }
                            }
                        }
                    }
                }
                add(LgLabeledComponent.create(LgBundle.message("control.section.label"), sectionCombo))

                // Show Included button
                add(JButton(LgBundle.message("control.btn.show.included"), AllIcons.Actions.ShowAsTree).apply {
                    addActionListener {
                        LgShowIncludedFilesAction().performSafely(this@LgControlPanel)
                    }
                })

                // Generate Listing button
                add(JButton(LgBundle.message("control.btn.generate.listing"), AllIcons.Actions.ShowCode).apply {
                    addActionListener {
                        LgGenerateListingAction().performSafely(this@LgControlPanel)
                    }
                })

                // Show Stats button
                add(JButton(LgBundle.message("control.btn.show.stats"), AllIcons.Actions.ListFiles).apply {
                    addActionListener {
                        LgShowSectionStatsAction().performSafely(this@LgControlPanel)
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
                        if (!suppressDispatch) {
                            val newLib = selectedItem as? String
                            if (newLib != null) {
                                scope.launch {
                                    coordinator.dispatch(
                                        SelectLib.create(newLib)
                                    )
                                }
                            }
                        }
                    }
                }
                add(LgLabeledComponent.create(LgBundle.message("control.library.label"), libraryCombo))

                // Encoder Completion Field
                encoderField = LgEncoderCompletionField(project, this@LgControlPanel).apply {
                    whenTextChangedFromUi(this@LgControlPanel) { newText ->
                        if (!suppressDispatch) {
                            scope.launch {
                                coordinator.dispatch(
                                    SetEncoder.create(newText)
                                )
                            }
                        }
                    }
                }
                add(LgLabeledComponent.create(LgBundle.message("control.encoder.label"), encoderField))

                // Context Limit TextField
                ctxLimitField = JBTextField(10).apply {
                    document.addDocumentListener(object : DocumentListener {
                        override fun insertUpdate(e: DocumentEvent?) = update()
                        override fun removeUpdate(e: DocumentEvent?) = update()
                        override fun changedUpdate(e: DocumentEvent?) = update()
                        private fun update() {
                            if (!suppressDispatch) {
                                val parsed = text.toIntOrNull()?.coerceIn(1000, 2_000_000)
                                if (parsed != null) {
                                    scope.launch {
                                        coordinator.dispatch(
                                            SetCtxLimit.create(parsed)
                                        )
                                    }
                                }
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
            add(LgUpdateAiModesAction())

            addSeparator()

            // Diagnostics
            add(LgRunDoctorAction())
            add(LgResetCacheAction())
            add(LgClearStateAction())

            add(object : AnAction(
                LgBundle.message("action.settings.text"),
                LgBundle.message("action.settings.description"),
                AllIcons.General.Settings
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    com.intellij.ide.actions.ShowSettingsUtilImpl.showSettingsDialog(
                        project, "maxmoro.lg.settings", null
                    )
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

    override fun dispose() {
        scope.cancel()
        LOG.debug("Control Panel disposed")
    }

    /**
     * Helper to properly execute actions using IntelliJ Platform API.
     */
    private fun AnAction.performSafely(component: JComponent) {
        val dataContext = DataManager.getInstance().getDataContext(component)
        val event = AnActionEvent.createEvent(
            this,
            dataContext,
            null,
            ActionPlaces.TOOLWINDOW_CONTENT,
            ActionUiKind.NONE,
            null
        )
        ActionUtil.performAction(this, event)
    }

    companion object {
        private val LOG = logger<LgControlPanel>()
    }
}

