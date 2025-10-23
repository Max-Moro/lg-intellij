package lg.intellij.ui.dialogs

import com.intellij.icons.AllIcons
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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import lg.intellij.LgBundle
import lg.intellij.models.DiagReportSchema
import lg.intellij.models.Severity
import lg.intellij.services.diagnostics.DiagnosticsException
import lg.intellij.services.diagnostics.LgDiagnosticsService
import lg.intellij.utils.LgFormatUtils
import java.awt.BorderLayout
import java.awt.Font
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.table.DefaultTableModel

/**
 * Dialog for displaying diagnostic information from CLI 'lg diag' command.
 * 
 * Features:
 * - Summary cards: Config, Cache, Contexts, Environment
 * - Checks table with status icons
 * - Detailed sections (collapsible)
 * - Raw JSON viewer
 * - Actions: Refresh, Rebuild Cache, Build Bundle, Copy JSON
 * 
 * Phase 14: Doctor Diagnostics implementation.
 */
class LgDoctorDialog(
    private val project: Project,
    private val initialReport: DiagReportSchema
) : DialogWrapper(project) {
    
    private val diagnosticsService = project.service<LgDiagnosticsService>()
    
    private var currentReport: DiagReportSchema = initialReport
    
    // UI components
    private lateinit var summaryPanel: JPanel
    private lateinit var checksPanel: JPanel
    private lateinit var configDetailsPanel: JPanel
    private lateinit var cacheDetailsPanel: JPanel
    private lateinit var migrationsPanel: JPanel
    private lateinit var rawJsonArea: JBTextArea
    
    // Actions
    private val refreshAction = object : DialogWrapperAction(LgBundle.message("doctor.btn.refresh")) {
        init {
            putValue(SMALL_ICON, AllIcons.Actions.Refresh)
            putValue(DEFAULT_ACTION, true)
        }
        
        override fun doAction(e: ActionEvent) {
            refreshDiagnostics()
        }
    }
    
    private val resetCacheAction = object : DialogWrapperAction(LgBundle.message("doctor.btn.reset.cache")) {
        init {
            putValue(SMALL_ICON, AllIcons.Actions.GC)
        }
        
        override fun doAction(e: ActionEvent) {
            resetCache()
        }
    }
    
    private val buildBundleAction = object : DialogWrapperAction(LgBundle.message("doctor.btn.bundle")) {
        init {
            putValue(SMALL_ICON, AllIcons.Actions.Download)
        }
        
        override fun doAction(e: ActionEvent) {
            buildBundle()
        }
    }
    
    private val copyJsonAction = object : DialogWrapperAction(LgBundle.message("doctor.btn.copy.json")) {
        init {
            putValue(SMALL_ICON, AllIcons.Actions.Copy)
        }
        
        override fun doAction(e: ActionEvent) {
            copyJsonToClipboard()
        }
    }
    
    init {
        title = LgBundle.message("doctor.dialog.title")
        isResizable = true
        isModal = false
        init()
        
        // Initial render
        updateUI(currentReport)
    }
    
    override fun createCenterPanel(): JComponent {
        return panel {
            // Info header
            row {
                val info = buildString {
                    append("Tool: ${currentReport.toolVersion}")
                    append(" • Protocol: ${currentReport.protocol}")
                    append(" • Root: ${currentReport.root}")
                }
                
                label(info).apply {
                    component.foreground = UIUtil.getLabelDisabledForeground()
                    component.font = JBUI.Fonts.smallFont()
                }
            }
            
            separator()
            
            // Summary Cards
            row {
                summaryPanel = createSummaryPanel()
                cell(summaryPanel)
                    .align(Align.FILL)
            }
            
            separator()
            
            // Checks Table
            row {
                checksPanel = JPanel(BorderLayout())
                cell(checksPanel)
                    .align(Align.FILL)
            }.resizableRow()
            
            separator()
            
            // Collapsible details
            createCollapsibleSections()
            
        }.apply {
            preferredSize = JBUI.size(900, 700)
        }
    }
    
    private fun createSummaryPanel(): JPanel {
        return JPanel(java.awt.GridLayout(1, 4, 10, 0)).apply {
            border = JBUI.Borders.empty(8)
        }
    }
    
    private fun Panel.createCollapsibleSections() {
        collapsibleGroup(LgBundle.message("doctor.section.config.details")) {
            row {
                configDetailsPanel = JPanel(BorderLayout())
                cell(configDetailsPanel)
                    .align(Align.FILL)
            }
        }
        
        collapsibleGroup(LgBundle.message("doctor.section.cache.details")) {
            row {
                cacheDetailsPanel = JPanel(BorderLayout())
                cell(cacheDetailsPanel)
                    .align(Align.FILL)
            }
        }
        
        collapsibleGroup(LgBundle.message("doctor.section.migrations")) {
            row {
                migrationsPanel = JPanel(BorderLayout())
                cell(migrationsPanel)
                    .align(Align.FILL)
            }
        }
        
        collapsibleGroup(LgBundle.message("doctor.section.raw.json")) {
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
        return arrayOf(
            refreshAction,
            resetCacheAction,
            buildBundleAction,
            copyJsonAction,
            cancelAction
        )
    }
    
    /**
     * Refreshes diagnostics data.
     */
    private fun refreshDiagnostics() {
        object : Task.Backgroundable(
            project,
            LgBundle.message("doctor.progress.refresh"),
            true
        ) {
            private var report: DiagReportSchema? = null
            
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                
                try {
                    report = runBlocking {
                        diagnosticsService.runDiagnostics()
                    }
                } catch (e: DiagnosticsException) {
                    LOG.warn("Diagnostics refresh failed: ${e.message}")
                }
            }
            
            override fun onSuccess() {
                report?.let {
                    currentReport = it
                    updateUI(it)
                }
            }
        }.queue()
    }
    
    /**
     * Resets cache and refreshes diagnostics.
     */
    private fun resetCache() {
        object : Task.Backgroundable(
            project,
            LgBundle.message("doctor.progress.reset.cache"),
            true
        ) {
            private var report: DiagReportSchema? = null
            
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                
                try {
                    report = runBlocking {
                        diagnosticsService.rebuildCache()
                    }
                } catch (e: DiagnosticsException) {
                    LOG.warn("Cache reset failed: ${e.message}")
                }
            }
            
            override fun onSuccess() {
                report?.let {
                    currentReport = it
                    updateUI(it)
                }
            }
        }.queue()
    }
    
    /**
     * Builds diagnostic bundle.
     */
    private fun buildBundle() {
        object : Task.Backgroundable(
            project,
            LgBundle.message("doctor.progress.bundle"),
            true
        ) {
            private var result: Pair<DiagReportSchema, String?>? = null
            
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                
                try {
                    result = runBlocking {
                        diagnosticsService.buildBundle()
                    }
                } catch (e: DiagnosticsException) {
                    LOG.warn("Bundle build failed: ${e.message}")
                }
            }
            
            override fun onSuccess() {
                result?.let { (report, bundlePath) ->
                    currentReport = report
                    updateUI(report)
                    
                    if (bundlePath != null) {
                        com.intellij.notification.NotificationGroupManager.getInstance()
                            .getNotificationGroup("LG Notifications")
                            .createNotification(
                                LgBundle.message("doctor.bundle.success.title"),
                                LgBundle.message("doctor.bundle.success.message", bundlePath),
                                com.intellij.notification.NotificationType.INFORMATION
                            )
                            .notify(project)
                    }
                }
            }
        }.queue()
    }
    
    /**
     * Copies raw JSON to clipboard.
     */
    private fun copyJsonToClipboard() {
        if (::rawJsonArea.isInitialized) {
            CopyPasteManager.getInstance().setContents(
                StringSelection(rawJsonArea.text)
            )
        }
    }
    
    /**
     * Updates all UI components with new report data.
     */
    private fun updateUI(report: DiagReportSchema) {
        updateSummaryCards(report)
        updateChecksTable(report)
        updateConfigDetails(report)
        updateCacheDetails(report)
        updateMigrations(report)
        updateRawJson(report)
    }
    
    private fun updateSummaryCards(report: DiagReportSchema) {
        if (!::summaryPanel.isInitialized) return
        
        summaryPanel.removeAll()
        
        // Config card
        summaryPanel.add(createCard(
            LgBundle.message("doctor.card.config"),
            buildString {
                appendLine(if (report.config.exists) "Present" else "Not found")
                if (report.config.actual != null) {
                    append("Actual: ${report.config.actual}")
                }
            },
            if (report.config.exists) Severity.Ok else Severity.Error
        ))
        
        // Cache card
        summaryPanel.add(createCard(
            LgBundle.message("doctor.card.cache"),
            buildString {
                appendLine(if (report.cache.exists) "Exists" else "Missing")
                appendLine("Size: ${LgFormatUtils.formatSize(report.cache.sizeBytes)}")
                append("Entries: ${report.cache.entries}")
            },
            if (report.cache.exists) Severity.Ok else Severity.Warn
        ))
        
        // Contexts card
        summaryPanel.add(createCard(
            LgBundle.message("doctor.card.contexts"),
            "${report.contexts.size} contexts",
            Severity.Ok
        ))
        
        // Environment card
        summaryPanel.add(createCard(
            LgBundle.message("doctor.card.environment"),
            buildString {
                appendLine("Python: ${report.env.python}")
                append(report.env.platform)
            },
            Severity.Ok
        ))
        
        summaryPanel.revalidate()
        summaryPanel.repaint()
    }
    
    private fun createCard(title: String, content: String, severity: Severity): JPanel {
        return JPanel(BorderLayout()).apply {
            border = javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createLineBorder(
                    when (severity) {
                        Severity.Ok -> UIUtil.getBoundsColor()
                        Severity.Warn -> JBUI.CurrentTheme.Banner.WARNING_BORDER_COLOR
                        Severity.Error -> JBUI.CurrentTheme.Banner.ERROR_BORDER_COLOR
                    },
                    1
                ),
                JBUI.Borders.empty(8)
            )
            
            add(
                JBLabel(title).apply {
                    font = font.deriveFont(Font.BOLD)
                },
                BorderLayout.NORTH
            )
            
            add(
                JBLabel("<html>${content.replace("\n", "<br/>")}</html>"),
                BorderLayout.CENTER
            )
        }
    }
    
    private fun updateChecksTable(report: DiagReportSchema) {
        if (!::checksPanel.isInitialized) return
        
        checksPanel.removeAll()
        
        val tableData = report.checks.map { check ->
            arrayOf(
                severityIcon(check.level),
                check.name,
                check.details ?: ""
            )
        }.toTypedArray()
        
        val model = object : DefaultTableModel(
            tableData,
            arrayOf("", "Check", "Details")
        ) {
            override fun isCellEditable(row: Int, column: Int) = false
        }
        
        val table = JBTable(model).apply {
            setShowGrid(true)
            autoResizeMode = javax.swing.JTable.AUTO_RESIZE_LAST_COLUMN
            
            // Set column widths
            columnModel.getColumn(0).preferredWidth = 30
            columnModel.getColumn(0).maxWidth = 30
            columnModel.getColumn(1).preferredWidth = 200
            columnModel.getColumn(1).maxWidth = 200
        }
        
        checksPanel.add(JBScrollPane(table), BorderLayout.CENTER)
        checksPanel.revalidate()
        checksPanel.repaint()
    }
    
    private fun severityIcon(severity: Severity): String {
        return when (severity) {
            Severity.Ok -> "✔️"
            Severity.Warn -> "⚠️"
            Severity.Error -> "❌"
        }
    }
    
    private fun updateConfigDetails(report: DiagReportSchema) {
        if (!::configDetailsPanel.isInitialized) return
        
        configDetailsPanel.removeAll()
        
        val config = report.config
        val tableData = buildList {
            add(arrayOf("Exists", if (config.exists) "Yes" else "No"))
            add(arrayOf("Path", config.path))
            if (config.current != null) add(arrayOf("Current", config.current.toString()))
            if (config.actual != null) add(arrayOf("Actual", config.actual.toString()))
            if (config.fingerprint != null) add(arrayOf("Fingerprint", config.fingerprint))
            if (config.sections != null) {
                val sectionsStr = config.sections.take(20).joinToString(", ") +
                    if (config.sections.size > 20) "..." else ""
                add(arrayOf("Sections", sectionsStr))
            }
            if (config.error != null) add(arrayOf("Error", config.error))
            if (config.lastError != null) {
                add(arrayOf("Last Error", config.lastError.message))
            }
        }.toTypedArray()
        
        val model = object : DefaultTableModel(tableData, arrayOf("Property", "Value")) {
            override fun isCellEditable(row: Int, column: Int) = false
        }
        
        val table = JBTable(model).apply {
            setShowGrid(false)
            tableHeader = null
            columnModel.getColumn(0).preferredWidth = 150
        }
        
        configDetailsPanel.add(JBScrollPane(table), BorderLayout.CENTER)
        configDetailsPanel.revalidate()
        configDetailsPanel.repaint()
    }
    
    private fun updateCacheDetails(report: DiagReportSchema) {
        if (!::cacheDetailsPanel.isInitialized) return
        
        cacheDetailsPanel.removeAll()
        
        val cache = report.cache
        val tableData = arrayOf(
            arrayOf("Enabled", cache.enabled.toString()),
            arrayOf("Exists", cache.exists.toString()),
            arrayOf("Path", cache.path),
            arrayOf("Size", LgFormatUtils.formatSize(cache.sizeBytes)),
            arrayOf("Entries", cache.entries.toString()),
            arrayOf("Rebuilt", cache.rebuilt.toString())
        ) + if (cache.error != null) {
            arrayOf(arrayOf("Error", cache.error))
        } else emptyArray()
        
        val model = object : DefaultTableModel(tableData, arrayOf("Property", "Value")) {
            override fun isCellEditable(row: Int, column: Int) = false
        }
        
        val table = JBTable(model).apply {
            setShowGrid(false)
            tableHeader = null
            columnModel.getColumn(0).preferredWidth = 150
        }
        
        cacheDetailsPanel.add(JBScrollPane(table), BorderLayout.CENTER)
        cacheDetailsPanel.revalidate()
        cacheDetailsPanel.repaint()
    }
    
    private fun updateMigrations(report: DiagReportSchema) {
        if (!::migrationsPanel.isInitialized) return
        
        migrationsPanel.removeAll()
        
        val applied = report.config.applied
        if (applied == null || applied.isEmpty()) {
            migrationsPanel.add(
                JBLabel("No migrations applied"),
                BorderLayout.CENTER
            )
        } else {
            val tableData = applied.map { migration ->
                arrayOf(migration.id.toString(), migration.title)
            }.toTypedArray()
            
            val model = object : DefaultTableModel(
                tableData,
                arrayOf("ID", "Title")
            ) {
                override fun isCellEditable(row: Int, column: Int) = false
            }
            
            val table = JBTable(model).apply {
                setShowGrid(true)
                columnModel.getColumn(0).preferredWidth = 50
            }
            
            migrationsPanel.add(JBScrollPane(table), BorderLayout.CENTER)
        }
        
        migrationsPanel.revalidate()
        migrationsPanel.repaint()
    }
    
    private fun updateRawJson(report: DiagReportSchema) {
        if (!::rawJsonArea.isInitialized) return
        
        val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }
        
        try {
            val jsonString = json.encodeToString(DiagReportSchema.serializer(), report)
            rawJsonArea.text = jsonString
        } catch (e: Exception) {
            rawJsonArea.text = "Failed to serialize JSON: ${e.message}"
            LOG.error("Failed to serialize diagnostics to JSON", e)
        }
    }

    companion object {
        private val LOG = logger<LgDoctorDialog>()
    }
}

