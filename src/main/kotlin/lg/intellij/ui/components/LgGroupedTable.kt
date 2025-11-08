package lg.intellij.ui.components

import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.Timer
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.AbstractTableModel

/**
 * Grouped table component with filtering, sorting, and hierarchical grouping.
 *
 * Inspired by VS Code Extension grouped-table component.
 *
 * Features:
 * - Hierarchical grouping by path segments (1..N levels or ∞ for flat)
 * - Filtering by path or extension
 * - Sorting by any column (group-aware)
 * - Aggregation of numeric columns for group rows
 */
class LgGroupedTable(
    private val columns: List<ColumnConfig>,
    private val onRowClick: ((String) -> Unit)? = null
) : JPanel(BorderLayout()) {
    
    private val tableModel = GroupedTableModel()
    private val table = JBTable(tableModel)
    private val filterField = JBTextField(20)
    
    // Grouping state
    private var groupLevel: Int = Int.MAX_VALUE // ∞ by default
    private var maxDepth: Int = 0
    
    // Sorting state
    private var sortColumn: String? = null
    private var sortAscending: Boolean = true
    
    // Data
    private var allData: List<RowData> = emptyList()
    
    init {
        setupToolbar()
        setupTable()
        
        LOG.debug("LgGroupedTable initialized with ${columns.size} columns")
    }
    
    /**
     * Sets data for the table.
     * 
     * @param data List of row data objects
     */
    fun setData(data: List<RowData>) {
        allData = data
        maxDepth = calculateMaxDepth(data)
        
        // Default grouping: show all levels (∞)
        groupLevel = maxDepth + 1
        
        LOG.debug("Data set: ${data.size} rows, max depth: $maxDepth")
        
        rebuild()
    }
    
    /**
     * Updates grouping level.
     * 
     * @param level 1..maxDepth for grouping, > maxDepth for flat (∞)
     */
    fun setGroupLevel(level: Int) {
        groupLevel = level
        rebuild()
    }

    private fun setupToolbar() {
        val toolbar = JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(8, 4)
        }
        
        val gbc = GridBagConstraints().apply {
            insets = JBUI.insets(0, 4)
            anchor = GridBagConstraints.WEST
        }
        
        // Grouping control
        gbc.gridx = 0
        toolbar.add(JBLabel("Group:"), gbc)
        
        gbc.gridx = 1
        val prevButton = JButton("←").apply {
            toolTipText = "Decrease grouping level"
            addActionListener {
                if (groupLevel > 1) {
                    setGroupLevel(groupLevel - 1)
                }
            }
        }
        toolbar.add(prevButton, gbc)
        
        gbc.gridx = 2
        val levelLabel = JBLabel(formatGroupLevel()).apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            border = JBUI.Borders.empty(0, 8)
        }
        toolbar.add(levelLabel, gbc)
        
        gbc.gridx = 3
        val nextButton = JButton("→").apply {
            toolTipText = "Increase grouping level"
            addActionListener {
                if (groupLevel <= maxDepth) {
                    setGroupLevel(groupLevel + 1)
                }
            }
        }
        toolbar.add(nextButton, gbc)
        
        // Filter
        gbc.gridx = 4
        gbc.weightx = 1.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        toolbar.add(Box.createHorizontalStrut(20), gbc)
        
        gbc.gridx = 5
        gbc.weightx = 0.0
        gbc.fill = GridBagConstraints.NONE
        toolbar.add(JBLabel("Filter:"), gbc)
        
        gbc.gridx = 6
        filterField.apply {
            emptyText.text = "path or ext"
            document.addDocumentListener(object : DocumentListener {
                private var timer: Timer? = null
                
                override fun insertUpdate(e: DocumentEvent) = scheduleRebuild()
                override fun removeUpdate(e: DocumentEvent) = scheduleRebuild()
                override fun changedUpdate(e: DocumentEvent) = scheduleRebuild()
                
                private fun scheduleRebuild() {
                    timer?.stop()
                    timer = Timer(300) {
                        rebuild()
                    }.apply {
                        isRepeats = false
                        start()
                    }
                }
            })
        }
        toolbar.add(filterField, gbc)
        
        add(toolbar, BorderLayout.NORTH)
        
        // Store references for updates
        prevButton.putClientProperty("levelLabel", levelLabel)
        nextButton.putClientProperty("levelLabel", levelLabel)
    }
    
    private fun setupTable() {
        table.apply {
            setShowGrid(true)
            autoResizeMode = JBTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
            
            // Clickable column headers for sorting
            tableHeader.addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    val columnIndex = table.columnAtPoint(e.point)
                    if (columnIndex >= 0) {
                        val column = columns.getOrNull(columnIndex)
                        if (column?.sortable == true) {
                            handleColumnClick(column.key)
                        }
                    }
                }
            })
            
            // Row selection listener
            selectionModel.addListSelectionListener {
                if (!it.valueIsAdjusting) {
                    val row = selectedRow
                    if (row >= 0) {
                        val path = tableModel.getPathAt(row)
                        if (path != null) {
                            onRowClick?.invoke(path)
                        }
                    }
                }
            }
        }
        
        val scrollPane = JBScrollPane(table)
        add(scrollPane, BorderLayout.CENTER)
    }
    
    /**
     * Handles column header click for sorting.
     */
    private fun handleColumnClick(columnKey: String) {
        if (sortColumn == columnKey) {
            // Toggle direction
            sortAscending = !sortAscending
        } else {
            // New column: default direction based on column type
            sortColumn = columnKey
            val col = columns.find { it.key == columnKey }
            sortAscending = col?.format != ColumnFormat.PERCENT && 
                            col?.format != ColumnFormat.INT
        }
        
        rebuild()
        
        // Update column headers with sort indicators
        updateColumnHeaders()
        
        LOG.debug("Sorting by $columnKey (${if (sortAscending) "asc" else "desc"})")
    }
    
    /**
     * Updates column headers with sort indicators.
     */
    private fun updateColumnHeaders() {
        val columnModel = table.columnModel
        
        for (i in 0 until columnModel.columnCount) {
            val column = columnModel.getColumn(i)
            val col = columns.getOrNull(i) ?: continue
            
            // Add sort indicator if this column is sorted
            column.headerValue = if (sortColumn == col.key) {
                val arrow = if (sortAscending) "▲" else "▼"
                "${col.label} $arrow"
            } else {
                col.label
            }
        }
        
        table.tableHeader.repaint()
    }
    
    /**
     * Rebuilds table content based on current state.
     */
    private fun rebuild() {
        val filtered = applyFilter(allData)
        val sorted = applySorting(filtered)
        
        val rows = if (groupLevel > maxDepth) {
            // Flat mode (∞)
            buildFlatRows(sorted)
        } else {
            // Grouped mode
            buildGroupedRows(sorted, groupLevel)
        }
        
        tableModel.setRows(rows)
        
        // Auto-size columns based on content
        adjustColumnWidths()
        
        // Update grouping control
        updateGroupingControl()
        
        LOG.debug("Table rebuilt: ${rows.size} rows (groupLevel=$groupLevel, filter='${filterField.text}')")
    }
    
    private fun applyFilter(data: List<RowData>): List<RowData> {
        val query = filterField.text.trim().lowercase()
        if (query.isEmpty()) return data
        
        return data.filter { row ->
            val path = row.values["path"]?.toString()?.lowercase() ?: ""
            path.contains(query) || (query.startsWith(".") && path.endsWith(query))
        }
    }
    
    private fun applySorting(data: List<RowData>): List<RowData> {
        val column = sortColumn ?: return data
        
        val comparator = Comparator<RowData> { a, b ->
            val valA = a.values[column]
            val valB = b.values[column]
            
            val cmp = when {
                valA == null && valB == null -> 0
                valA == null -> 1
                valB == null -> -1
                valA is Number && valB is Number -> {
                    valA.toDouble().compareTo(valB.toDouble())
                }
                else -> {
                    valA.toString().compareTo(valB.toString(), ignoreCase = true)
                }
            }
            
            if (sortAscending) cmp else -cmp
        }
        
        return data.sortedWith(comparator)
    }
    
    private fun buildFlatRows(data: List<RowData>): List<TableRow> {
        return data.map { row ->
            TableRow.FileRow(
                path = row.values["path"]?.toString() ?: "",
                values = columns.map { col -> 
                    formatValue(row.values[col.key], col)
                }
            )
        }
    }
    
    private fun buildGroupedRows(data: List<RowData>, level: Int): List<TableRow> {
        // Normalize paths: insert 'self' for files alongside directories
        val normalized = normalizePaths(data, level)
        
        // Build hierarchy tree
        val tree = buildTree(normalized, level)
        
        // Flatten tree to rows (groups first, then files)
        return flattenTree(tree)
    }
    
    /**
     * Normalizes paths to ensure consistent grouping depth.
     * 
     * Files with fewer segments than grouping level get 'self' inserted before filename.
     */
    private fun normalizePaths(data: List<RowData>, level: Int): List<RowData> {
        return data.map { row ->
            val path = row.values["path"]?.toString() ?: return@map row
            val parts = path.split("/").filter { it.isNotEmpty() }
            
            if (parts.size <= level) {
                // Insert 'self' before filename to reach required depth
                val withSelf = buildList {
                    addAll(parts.dropLast(1))
                    while (size < level) {
                        add("self")
                    }
                    add(parts.last())
                }
                
                val normalizedPath = withSelf.joinToString("/")
                row.copy(
                    values = row.values + ("_normalizedPath" to normalizedPath)
                )
            } else {
                row.copy(
                    values = row.values + ("_normalizedPath" to path)
                )
            }
        }
    }
    
    /**
     * Builds tree structure for grouping.
     * 
     * Returns map: group prefix -> list of files
     */
    private fun buildTree(data: List<RowData>, level: Int): Map<String, List<RowData>> {
        val tree = mutableMapOf<String, MutableList<RowData>>()
        
        for (row in data) {
            val path = row.values["_normalizedPath"]?.toString() ?: continue
            val parts = path.split("/").filter { it.isNotEmpty() }
            
            val prefix = parts.take(level).joinToString("/")
            
            if (prefix.isNotEmpty()) {
                tree.getOrPut(prefix) { mutableListOf() }.add(row)
            }
        }
        
        return tree
    }
    
    /**
     * Flattens tree to table rows (group row + file rows).
     */
    private fun flattenTree(tree: Map<String, List<RowData>>): List<TableRow> {
        val rows = mutableListOf<TableRow>()
        
        // Sort groups
        val sortedGroups = if (sortColumn != null && sortColumn != "path") {
            // Sort by aggregated column value
            tree.entries.sortedWith { a, b ->
                val aggA = aggregateGroup(a.value)
                val aggB = aggregateGroup(b.value)
                
                val valA = aggA[sortColumn]
                val valB = aggB[sortColumn]
                
                val cmp = when {
                    valA == null && valB == null -> 0
                    valA == null -> 1
                    valB == null -> -1
                    valA is Number && valB is Number -> {
                        valA.toDouble().compareTo(valB.toDouble())
                    }
                    else -> {
                        valA.toString().compareTo(valB.toString(), ignoreCase = true)
                    }
                }
                
                if (sortAscending) cmp else -cmp
            }
        } else {
            // Sort by prefix (path)
            tree.entries.sortedWith { a, b ->
                val cmp = a.key.compareTo(b.key, ignoreCase = true)
                if (sortAscending) cmp else -cmp
            }
        }
        
        for ((prefix, files) in sortedGroups) {
            // Aggregate numeric columns for group row
            val aggregated = aggregateGroup(files)
            
            // Display path: remove trailing 'self' segments
            val displayPath = cleanDisplayPath(prefix)
            
            // Group row
            val groupValues = columns.map { col ->
                if (col.key == "path") {
                    if (displayPath == "/") "📁 /" else "📁 $displayPath/"
                } else {
                    formatValue(aggregated[col.key], col)
                }
            }
            
            rows.add(
                TableRow.GroupRow(
                    path = prefix,
                    values = groupValues,
                    displayName = displayPath
                )
            )
        }
        
        return rows
    }
    
    /**
     * Aggregates numeric columns for a group.
     */
    private fun aggregateGroup(files: List<RowData>): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        
        for (col in columns) {
            if (col.key == "path") continue
            
            // Custom formula has priority
            if (col.aggregateFormula != null) {
                // First, aggregate all other columns
                val baseAggregated = mutableMapOf<String, Any?>()
                for (otherCol in columns) {
                    if (otherCol.key == "path" || otherCol.key == col.key) continue
                    if (otherCol.aggregateFormula != null) continue // Skip other formula columns
                    
                    val values = files.mapNotNull { it.values[otherCol.key] as? Number }
                    if (values.isNotEmpty()) {
                        baseAggregated[otherCol.key] = when (otherCol.aggregate) {
                            Aggregate.AVG -> values.map { it.toDouble() }.average()
                            else -> values.sumOf { it.toDouble() }
                        }
                    }
                }
                
                // Calculate formula
                result[col.key] = col.aggregateFormula.invoke(baseAggregated)
                continue
            }
            
            val values = files.mapNotNull { it.values[col.key] as? Number }
            
            if (values.isNotEmpty()) {
                result[col.key] = when (col.aggregate) {
                    Aggregate.AVG -> values.map { it.toDouble() }.average()
                    else -> values.sumOf { it.toDouble() } // Default: SUM
                }
            }
        }
        
        return result
    }
    
    /**
     * Cleans display path by removing trailing 'self' segments.
     */
    private fun cleanDisplayPath(prefix: String): String {
        val parts = prefix.split("/").filter { it.isNotEmpty() }
        
        // Remove trailing 'self'
        val cleaned = parts.dropLastWhile { it == "self" }
        
        return if (cleaned.isEmpty()) "/" else cleaned.joinToString("/")
    }
    
    private fun formatValue(value: Any?, column: ColumnConfig): String {
        if (value == null) return ""
        
        return when (column.format) {
            ColumnFormat.INT -> {
                val num = value as? Number ?: return value.toString()
                "%,d".format(num.toLong())
            }
            ColumnFormat.PERCENT -> {
                val num = value as? Number ?: return value.toString()
                "%.1f%%".format(num.toDouble())
            }
            ColumnFormat.SIZE -> {
                val bytes = value as? Number ?: return value.toString()
                formatSize(bytes.toLong())
            }
            ColumnFormat.TEXT -> value.toString()
        }
    }
    
    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        
        val units = arrayOf("KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var unitIndex = -1
        
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        
        return "%.1f ${units[unitIndex]}".format(size)
    }
    
    private fun calculateMaxDepth(data: List<RowData>): Int {
        var max = 0
        
        for (row in data) {
            val path = row.values["path"]?.toString() ?: continue
            val depth = path.split("/").filter { it.isNotEmpty() }.size - 1
            max = maxOf(max, depth)
        }
        
        return maxOf(1, max)
    }
    
    private fun formatGroupLevel(): String {
        return if (groupLevel > maxDepth) "∞" else groupLevel.toString()
    }
    
    private fun updateGroupingControl() {
        val levelLabel = getComponent(0) // Toolbar
            .let { it as? JPanel }
            ?.components?.firstNotNullOfOrNull { c -> (c as? JButton)?.getClientProperty("levelLabel") as? JBLabel }

        levelLabel?.text = formatGroupLevel()
    }
    
    /**
     * Adjusts column widths based on content.
     */
    private fun adjustColumnWidths() {
        val columnModel = table.columnModel
        
        for (i in 0 until columnModel.columnCount) {
            val column = columnModel.getColumn(i)
            val colConfig = columns.getOrNull(i) ?: continue
            
            // Calculate preferred width based on content
            var maxWidth = 0
            
            // Header width
            val headerRenderer = table.tableHeader.defaultRenderer
            val headerComp = headerRenderer.getTableCellRendererComponent(
                table, column.headerValue, false, false, 0, i
            )
            maxWidth = maxOf(maxWidth, headerComp.preferredSize.width)
            
            // Cell widths (sample first 50 rows for performance)
            val rowCount = minOf(table.rowCount, 50)
            for (row in 0 until rowCount) {
                val renderer = table.getCellRenderer(row, i)
                val comp = table.prepareRenderer(renderer, row, i)
                maxWidth = maxOf(maxWidth, comp.preferredSize.width)
            }
            
            // Apply width with padding
            val padding = 10
            val preferredWidth = maxWidth + padding
            
            // Set min/max/preferred
            when (colConfig.key) {
                "path" -> {
                    // Path column: wider, flexible
                    column.preferredWidth = maxOf(300, preferredWidth)
                    column.minWidth = 200
                }
                else -> {
                    // Other columns: compact
                    column.preferredWidth = maxOf(80, preferredWidth)
                    column.minWidth = 60
                    column.maxWidth = 200
                }
            }
        }
    }
    
    /**
     * Table model for grouped table.
     */
    private inner class GroupedTableModel : AbstractTableModel() {
        
        private var rows: List<TableRow> = emptyList()
        
        fun setRows(newRows: List<TableRow>) {
            rows = newRows
            fireTableDataChanged()
        }
        
        fun getPathAt(rowIndex: Int): String? {
            return rows.getOrNull(rowIndex)?.path
        }
        
        override fun getRowCount(): Int = rows.size
        
        override fun getColumnCount(): Int = columns.size
        
        override fun getColumnName(column: Int): String {
            val col = columns.getOrNull(column) ?: return ""
            return col.label
        }
        
        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
            val row = rows.getOrNull(rowIndex) ?: return null
            return row.values.getOrNull(columnIndex) ?: ""
        }
        
        override fun getColumnClass(columnIndex: Int): Class<*> {
            return String::class.java
        }
    }
    
    /**
     * Represents a row in the table (group or file).
     */
    sealed class TableRow {
        abstract val path: String
        abstract val values: List<String>
        
        data class GroupRow(
            override val path: String,
            override val values: List<String>,
            val displayName: String
        ) : TableRow()
        
        data class FileRow(
            override val path: String,
            override val values: List<String>
        ) : TableRow()
    }
    
    /**
     * Column configuration.
     */
    data class ColumnConfig(
        val key: String,
        val label: String,
        val format: ColumnFormat = ColumnFormat.TEXT,
        val align: Align = Align.LEFT,
        val sortable: Boolean = true,
        val aggregate: Aggregate? = null,
        val aggregateFormula: ((Map<String, Any?>) -> Any?)? = null
    )
    
    enum class ColumnFormat {
        TEXT, INT, PERCENT, SIZE
    }
    
    enum class Align {
        LEFT, RIGHT
    }
    
    enum class Aggregate {
        SUM, AVG
    }
    
    /**
     * Row data (input).
     */
    data class RowData(
        val values: Map<String, Any?>
    )
    
    companion object {
        private val LOG = logger<LgGroupedTable>()
    }
}

