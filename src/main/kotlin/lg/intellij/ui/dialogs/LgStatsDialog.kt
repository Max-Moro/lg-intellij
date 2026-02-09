package lg.intellij.ui.dialogs

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import lg.intellij.LgBundle
import lg.intellij.actions.LgGenerateContextAction
import lg.intellij.actions.LgGenerateListingAction
import lg.intellij.actions.LgSendToAiAction
import lg.intellij.cli.CliTarget
import lg.intellij.statepce.LgCoordinatorService
import lg.intellij.models.ReportSchema
import lg.intellij.models.Scope
import lg.intellij.services.generation.LgStatsService
import lg.intellij.statepce.PCEStateStore
import lg.intellij.statepce.domains.SetTask
import lg.intellij.ui.components.LgGroupedTable
import lg.intellij.ui.components.LgTaskTextField
import lg.intellij.ui.components.LgTaskTextField.addChangeListener
import lg.intellij.utils.LgFormatUtils
import java.awt.Font
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.table.DefaultTableModel

/**
 * Dialog for displaying detailed statistics for sections or contexts.
 *
 * Features grouped table with collapsible sections and summary cards.
 */
class LgStatsDialog(
    private val project: Project,
    initialStats: ReportSchema,
    private val target: String
) : DialogWrapper(project) {
    
    private val statsService = project.service<LgStatsService>()
    private val store = PCEStateStore.getInstance(project)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private var currentStats: ReportSchema = initialStats
    
    // UI components
    private lateinit var summaryPanel: javax.swing.JPanel
    private lateinit var filesTable: LgGroupedTable
    private lateinit var adapterMetricsPanel: javax.swing.JPanel
    private lateinit var rawJsonArea: JBTextArea
    
    // Actions (must be declared before init block)
    private val refreshAction = object : DialogWrapperAction("Refresh") {
        init {
            putValue(SMALL_ICON, AllIcons.Actions.Refresh)
        }
        
        override fun doAction(e: ActionEvent) {
            refreshStats()
        }
    }
    
    private val sendToAiAction = object : DialogWrapperAction("Send to AI") {
        init {
            putValue(SMALL_ICON, AllIcons.Actions.Execute)
            putValue(DEFAULT_ACTION, true)
        }

        override fun doAction(e: ActionEvent) {
            // Trigger Send to AI action (will use current task text from panel state)
            LgSendToAiAction().performSafely(contentPanel)
            close(OK_EXIT_CODE)
        }
    }
    
    private val generateAction = object : DialogWrapperAction("Generate") {
        init {
            putValue(SMALL_ICON, AllIcons.Actions.ShowCode)
        }

        override fun doAction(e: ActionEvent) {
            // Trigger appropriate generation action and close dialog
            val action = if (currentStats.scope == Scope.Context) {
                LgGenerateContextAction()
            } else {
                LgGenerateListingAction()
            }

            action.performSafely(contentPanel)
            close(OK_EXIT_CODE)
        }
    }
    
    private val copyJsonAction = object : DialogWrapperAction("Copy JSON") {
        init {
            putValue(SMALL_ICON, AllIcons.Actions.Copy)
        }
        
        override fun doAction(e: ActionEvent) {
            if (::rawJsonArea.isInitialized) {
                CopyPasteManager.getInstance().setContents(
                    StringSelection(rawJsonArea.text)
                )
            }
        }
    }
    
    init {
        val scopeName = if (currentStats.scope == Scope.Context) "Context" else "Section"
        val name = extractName(target)
        title = "$scopeName: $name — Statistics"
        
        isResizable = true
        isModal = false  // Allow editing files while stats dialog is open
        init()
        
        // Initial render
        updateUI(currentStats)
    }
    
    private fun extractName(target: String): String {
        return CliTarget.extractName(target)
    }
    
    override fun createCenterPanel(): JComponent {
        return panel {
            // Info header
            row {
                val scopeName = if (currentStats.scope == Scope.Context) "context" else "section"
                val name = extractName(target)
                val info = "Scope: $scopeName • Name: $name • " +
                           "Tokenizer: ${currentStats.tokenizerLIB} • " +
                           "Encoder: ${currentStats.encoder} • " +
                           "Ctx limit: ${LgFormatUtils.formatInt(currentStats.ctxLimit)} tokens"
                
                label(info).apply {
                    component.foreground = UIUtil.getLabelDisabledForeground()
                    component.font = JBUI.Fonts.smallFont()
                }
            }
            
            separator()
            
            // Summary Cards (dynamic panel)
            row {
                summaryPanel = javax.swing.JPanel(java.awt.GridLayout(1, 0, 10, 0)).apply {
                    border = JBUI.Borders.empty(8)
                }
                cell(summaryPanel)
                    .align(Align.FILL)
            }
            
            separator()
            
            // Task Text Input (only for contexts)
            if (currentStats.scope == Scope.Context) {
                row {
                    val wrapper = LgTaskTextField.create(
                        project = project,
                        initialText = store.getBusinessState().persistent.taskText,
                        placeholder = LgBundle.message("control.task.placeholder")
                    )

                    // Debounced refresh on text change
                    var debounceJob: Job? = null
                    wrapper.editorField.addChangeListener { newText ->
                        // Update shared state via coordinator dispatch
                        scope.launch {
                            LgCoordinatorService.getInstance(project).coordinator.dispatch(
                                SetTask.create(newText)
                            )
                        }

                        // Debounced stats refresh
                        debounceJob?.cancel()
                        @Suppress("ASSIGNED_VALUE_IS_NEVER_READ") // Job reference needed for cancellation
                        debounceJob = scope.launch {
                            delay(500)
                            withContext(Dispatchers.Main) {
                                refreshStats()
                            }
                        }
                    }

                    cell(wrapper)
                        .align(Align.FILL)
                }

                separator()
            }
            
            // Files Table
            createFilesTableSection()
            
            separator()
            
            // Collapsible sections
            createCollapsibleSections()
            
        }.apply {
            preferredSize = JBUI.size(900, 700)
        }
    }
    
    private fun Panel.createFilesTableSection() {
        row {
            // Build columns config from stats
            val hideSaved = (currentStats.total.savedTokens) == 0L
            
            val columns = buildList {
                add(
                    LgGroupedTable.ColumnConfig(
                        key = "path",
                        label = "Path",
                        format = LgGroupedTable.ColumnFormat.TEXT,
                        align = LgGroupedTable.Align.LEFT,
                        sortable = true
                    )
                )
                add(
                    LgGroupedTable.ColumnConfig(
                        key = "sizeBytes",
                        label = "Size",
                        format = LgGroupedTable.ColumnFormat.SIZE,
                        align = LgGroupedTable.Align.RIGHT,
                        sortable = true,
                        aggregate = LgGroupedTable.Aggregate.SUM
                    )
                )
                add(
                    LgGroupedTable.ColumnConfig(
                        key = "tokensRaw",
                        label = "Raw",
                        format = LgGroupedTable.ColumnFormat.INT,
                        align = LgGroupedTable.Align.RIGHT,
                        sortable = true,
                        aggregate = LgGroupedTable.Aggregate.SUM
                    )
                )
                add(
                    LgGroupedTable.ColumnConfig(
                        key = "tokensProcessed",
                        label = "Processed",
                        format = LgGroupedTable.ColumnFormat.INT,
                        align = LgGroupedTable.Align.RIGHT,
                        sortable = true,
                        aggregate = LgGroupedTable.Aggregate.SUM
                    )
                )
                
                if (!hideSaved) {
                    add(
                        LgGroupedTable.ColumnConfig(
                            key = "savedTokens",
                            label = "Saved",
                            format = LgGroupedTable.ColumnFormat.INT,
                            align = LgGroupedTable.Align.RIGHT,
                            sortable = true,
                            aggregate = LgGroupedTable.Aggregate.SUM
                        )
                    )
                    add(
                        LgGroupedTable.ColumnConfig(
                            key = "savedPct",
                            label = "Saved%",
                            format = LgGroupedTable.ColumnFormat.PERCENT,
                            align = LgGroupedTable.Align.RIGHT,
                            sortable = true,
                            aggregateFormula = { aggregated ->
                                val saved = aggregated["savedTokens"] as? Number
                                val raw = aggregated["tokensRaw"] as? Number
                                
                                if (saved != null && raw != null && raw.toDouble() > 0) {
                                    (saved.toDouble() / raw.toDouble()) * 100.0
                                } else {
                                    0.0
                                }
                            }
                        )
                    )
                }
                
                add(
                    LgGroupedTable.ColumnConfig(
                        key = "promptShare",
                        label = "Prompt%",
                        format = LgGroupedTable.ColumnFormat.PERCENT,
                        align = LgGroupedTable.Align.RIGHT,
                        sortable = true,
                        aggregate = LgGroupedTable.Aggregate.SUM
                    )
                )
                add(
                    LgGroupedTable.ColumnConfig(
                        key = "ctxShare",
                        label = "Ctx%",
                        format = LgGroupedTable.ColumnFormat.PERCENT,
                        align = LgGroupedTable.Align.RIGHT,
                        sortable = true,
                        aggregate = LgGroupedTable.Aggregate.SUM
                    )
                )
            }
            
            // Create grouped table
            filesTable = LgGroupedTable(
                columns = columns,
                onRowClick = { path ->
                    CopyPasteManager.getInstance().setContents(
                        StringSelection(path)
                    )
                }
            )
            
            cell(filesTable)
                .align(Align.FILL)
        }.resizableRow()
    }
    
    private fun Panel.createCollapsibleSections() {
        collapsibleGroup(LgBundle.message("stats.adapter.metrics.title")) {
            row {
                adapterMetricsPanel = javax.swing.JPanel(java.awt.BorderLayout())
                cell(adapterMetricsPanel)
                    .align(Align.FILL)
            }
        }
        
        collapsibleGroup(LgBundle.message("stats.raw.json.title")) {
            row {
                rawJsonArea = JBTextArea(15, 80).apply {
                    isEditable = false
                    font = Font(Font.MONOSPACED, Font.PLAIN, 12)
                }
                
                scrollCell(rawJsonArea)
                    .align(Align.FILL)
            }.resizableRow()
        }
    }
    
    override fun createActions(): Array<Action> {
        return buildList {
            // Send to AI only for contexts
            if (currentStats.scope == Scope.Context) {
                add(sendToAiAction)
            }
            add(generateAction)
            add(copyJsonAction)
            add(refreshAction)
            add(cancelAction)
        }.toTypedArray()
    }
    
    /**
     * Refreshes stats with current task text from shared state.
     */
    private fun refreshStats() {
        
        object : Task.Backgroundable(
            project,
            "Refreshing statistics...",
            true
        ) {
            private var newStats: ReportSchema? = null
            
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                newStats = runBlocking {
                    statsService.getStats(target)
                }
            }
            
            override fun onSuccess() {
                newStats?.let {
                    currentStats = it
                    updateUI(it)
                }
            }
        }.queue()
    }
    
    /**
     * Updates all UI components with new stats data.
     */
    private fun updateUI(stats: ReportSchema) {
        updateSummaryCards(stats)
        updateFilesTable(stats)
        updateAdapterMetrics(stats)
        updateRawJson(stats)
    }
    
    private fun updateSummaryCards(stats: ReportSchema) {
        if (!::summaryPanel.isInitialized) return
        
        summaryPanel.removeAll()
        
        val total = stats.total
        val hideSaved = total.savedTokens == 0L
        val hasRendered = total.renderedTokens != null
        val hasFinal = stats.context?.finalRenderedTokens != null
        
        // Source Data card
        summaryPanel.add(createCard(
            LgBundle.message("stats.summary.source.title"),
            buildString {
                appendLine("📦 ${LgFormatUtils.formatSize(total.sizeBytes)}")
                append("🔤 ${LgFormatUtils.formatInt(total.tokensRaw)} tokens")
            }
        ))
        
        // Processed Data card (if savings exist)
        if (!hideSaved) {
            summaryPanel.add(createCard(
                LgBundle.message("stats.summary.processed.title"),
                buildString {
                    appendLine("🔤 ${LgFormatUtils.formatInt(total.tokensProcessed)}")
                    appendLine("💾 ${LgFormatUtils.formatInt(total.savedTokens)} saved")
                    append("📊 ${LgFormatUtils.formatPercent(total.ctxShare)} of context")
                }
            ))
        }
        
        // Rendered Data card (if exists)
        if (hasRendered) {
            val renderedTokens = total.renderedTokens
            val overhead = total.renderedOverheadTokens ?: 0
            val overheadPct = if (renderedTokens > 0) (100.0 * overhead / renderedTokens) else 0.0
            
            summaryPanel.add(createCard(
                LgBundle.message("stats.summary.rendered.title"),
                buildString {
                    appendLine("🔤 ${LgFormatUtils.formatInt(renderedTokens)}")
                    appendLine("📐 ${LgFormatUtils.formatInt(overhead)}")
                    append("◔ ${LgFormatUtils.formatPercent(overheadPct)}")
                }
            ))
        }
        
        // Template Overhead card (contexts only)
        if (hasFinal) {
            val ctx = stats.context
            summaryPanel.add(createCard(
                LgBundle.message("stats.summary.template.title"),
                buildString {
                    appendLine("🧩 ${LgFormatUtils.formatInt(ctx.templateOnlyTokens ?: 0)}")
                    append("◔ ${LgFormatUtils.formatPercent(ctx.templateOverheadPct ?: 0.0)}")
                }
            ))
        }
        
        // Final Rendered card (contexts only)
        if (hasFinal) {
            val ctx = stats.context
            summaryPanel.add(createCard(
                LgBundle.message("stats.summary.final.title"),
                buildString {
                    appendLine("🔤 ${LgFormatUtils.formatInt(ctx.finalRenderedTokens)}")
                    append("📊 ${LgFormatUtils.formatPercent(ctx.finalCtxShare ?: 0.0)} of context")
                }
            ))
        }
        
        summaryPanel.revalidate()
        summaryPanel.repaint()
    }
    
    /**
     * Creates a summary card panel.
     */
    private fun createCard(title: String, content: String): javax.swing.JPanel {
        return javax.swing.JPanel(java.awt.BorderLayout()).apply {
            border = javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createLineBorder(
                    UIUtil.getBoundsColor(),
                    1
                ),
                JBUI.Borders.empty(8)
            )
            
            add(
                JBLabel(title).apply {
                    font = font.deriveFont(Font.BOLD)
                },
                java.awt.BorderLayout.NORTH
            )
            
            add(
                JBLabel("<html>${content.replace("\n", "<br/>")}</html>"),
                java.awt.BorderLayout.CENTER
            )
        }
    }
    
    private fun updateFilesTable(stats: ReportSchema) {
        if (!::filesTable.isInitialized) return
        
        // Convert stats files to RowData
        val data = stats.files.map { file ->
            LgGroupedTable.RowData(
                values = mapOf(
                    "path" to file.path,
                    "sizeBytes" to file.sizeBytes,
                    "tokensRaw" to file.tokensRaw,
                    "tokensProcessed" to file.tokensProcessed,
                    "savedTokens" to file.savedTokens,
                    "savedPct" to file.savedPct,
                    "promptShare" to file.promptShare,
                    "ctxShare" to file.ctxShare
                )
            )
        }
        
        filesTable.setData(data)
    }
    
    private fun updateAdapterMetrics(stats: ReportSchema) {
        if (!::adapterMetricsPanel.isInitialized) return
        
        adapterMetricsPanel.removeAll()
        
        val metaSummary = stats.total.metaSummary
        if (metaSummary.isEmpty()) {
            adapterMetricsPanel.add(
                JBLabel("No adapter metrics available"),
                java.awt.BorderLayout.CENTER
            )
            adapterMetricsPanel.revalidate()
            adapterMetricsPanel.repaint()
            return
        }
        
        // Group by adapter prefix (e.g., "md", "py")
        val grouped = metaSummary.entries
            .filter { !it.key.startsWith("_") && it.value != 0L }
            .groupBy { entry ->
                val dot = entry.key.indexOf('.')
                if (dot > 0) entry.key.substring(0, dot) else "other"
            }
        
        if (grouped.isEmpty()) {
            adapterMetricsPanel.add(
                JBLabel("No adapter metrics available"),
                java.awt.BorderLayout.CENTER
            )
        } else {
            val metricsPanel = javax.swing.JPanel().apply {
                layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
            }
            
            for ((adapter, entries) in grouped.toSortedMap()) {
                val adapterTitle = adapter.replaceFirstChar { it.uppercase() }
                
                metricsPanel.add(JBLabel(adapterTitle).apply {
                    font = font.deriveFont(Font.BOLD)
                    border = JBUI.Borders.emptyTop(8)
                })
                
                // Create key-value table for this adapter
                val tableData = entries
                    .sortedBy { it.key }
                    .map { (key, value) ->
                        val metricName = key.substringAfter('.')
                        arrayOf(metricName, LgFormatUtils.formatInt(value))
                    }
                    .toTypedArray()
                
                val table = JBTable(
                    object : DefaultTableModel(tableData, arrayOf("Metric", "Value")) {
                        override fun isCellEditable(row: Int, column: Int) = false
                    }
                ).apply {
                    setShowGrid(false)
                    tableHeader = null // No header
                    font = Font(Font.MONOSPACED, Font.PLAIN, 12)
                }
                
                metricsPanel.add(table)
            }
            
            adapterMetricsPanel.add(
                JBScrollPane(metricsPanel),
                java.awt.BorderLayout.CENTER
            )
        }
        
        adapterMetricsPanel.revalidate()
        adapterMetricsPanel.repaint()
    }
    
    private fun updateRawJson(stats: ReportSchema) {
        if (!::rawJsonArea.isInitialized) return
        
        // Pretty-print JSON
        val json = kotlinx.serialization.json.Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }
        
        try {
            val jsonString = json.encodeToString(
                ReportSchema.serializer(),
                stats
            )
            rawJsonArea.text = jsonString
        } catch (e: Exception) {
            rawJsonArea.text = "Failed to serialize JSON: ${e.message}"
            LOG.error("Failed to serialize stats to JSON", e)
        }
    }
    
    override fun dispose() {
        scope.cancel()
        super.dispose()
    }
    
    companion object {
        private val LOG = logger<LgStatsDialog>()
    }
}

/**
 * Helper function to properly execute actions using IntelliJ Platform API.
 *
 * Uses ActionUtil.performAction instead of direct actionPerformed() call
 * to avoid override-only API violations.
 */
private fun AnAction.performSafely(component: JComponent) {
    val dataContext = DataManager.getInstance().getDataContext(component)
    val event = AnActionEvent.createEvent(
        this,
        dataContext,
        null,
        ActionPlaces.UNKNOWN,
        ActionUiKind.NONE,
        null
    )
    ActionUtil.performAction(this, event)
}

