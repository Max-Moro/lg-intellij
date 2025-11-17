## LgGroupedTable

Advanced table with hierarchical grouping, filtering, and sorting.

### Features

- **Hierarchical Grouping**: Group files by directory nesting levels (1..N or ∞ for flat)
- **Filtering**: Fast filtering by path or extension (debounced 300ms)
- **Sorting**: Sorting by any column with visual indicators (▲/▼)
- **Aggregation**: Automatic summation/averaging of numeric columns for groups
- **Path Normalization**: Automatic path alignment (files alongside directories → virtual `self/`)

### Usage

```kotlin
import lg.intellij.ui.components.LgGroupedTable

// Define columns
val columns = listOf(
    LgGroupedTable.ColumnConfig(
        key = "path",
        label = "Path",
        format = LgGroupedTable.ColumnFormat.TEXT,
        sortable = true
    ),
    LgGroupedTable.ColumnConfig(
        key = "size",
        label = "Size",
        format = LgGroupedTable.ColumnFormat.SIZE,
        align = LgGroupedTable.Align.RIGHT,
        sortable = true,
        aggregate = LgGroupedTable.Aggregate.SUM
    ),
    LgGroupedTable.ColumnConfig(
        key = "tokens",
        label = "Tokens",
        format = LgGroupedTable.ColumnFormat.INT,
        align = LgGroupedTable.Align.RIGHT,
        sortable = true,
        aggregate = LgGroupedTable.Aggregate.SUM
    ),
    LgGroupedTable.ColumnConfig(
        key = "share",
        label = "Share%",
        format = LgGroupedTable.ColumnFormat.PERCENT,
        align = LgGroupedTable.Align.RIGHT,
        sortable = true,
        aggregate = LgGroupedTable.Aggregate.AVG
    )
)

// Create table
val table = LgGroupedTable(
    columns = columns,
    onRowClick = { path ->
        // Handle row click
        println("Clicked: $path")
    }
)

// Set data
val data = files.map { file ->
    LgGroupedTable.RowData(
        values = mapOf(
            "path" to file.path,
            "size" to file.sizeBytes,
            "tokens" to file.tokens,
            "share" to file.share
        )
    )
}

table.setData(data)

// Add to layout
panel.add(table, BorderLayout.CENTER)
```

### Column Configuration

**ColumnConfig Properties:**

- `key` (String, required): Data key in RowData
- `label` (String, required): Column header
- `format` (ColumnFormat): Value display format
  - `TEXT` — plain text
  - `INT` — integers with separators (1,234,567)
  - `PERCENT` — percentages with one decimal (12.3%)
  - `SIZE` — byte sizes (1.5 KB, 2.3 MB)
- `align` (Align): Alignment (LEFT | RIGHT)
- `sortable` (Boolean): Whether this column is sortable
- `aggregate` (Aggregate?): Aggregation method for groups
  - `SUM` — sum of values
  - `AVG` — average value
  - `null` — no aggregation
- `aggregateFormula` ((Map<String, Any?>) -> Any?): Custom aggregation formula

### Grouping Control

Grouping control allows changing the nesting level:

- **← / →**: Decrease/increase grouping level
- **∞**: Disable grouping (show all files flat)
- **1-N**: Group up to the N-th nesting level

The maximum level is determined automatically based on the path structure in the data.

### Path Normalization

When grouping, the component automatically normalizes paths so that files are not mixed with directories at the same level:

```
Before normalization:
  apps/
    web/
      page.tsx
    mobile.tsx  <- file alongside directory

After normalization:
  apps/
    web/
      page.tsx
    self/
      mobile.tsx  <- moved to virtual 'self' directory
```

### API

**Methods:**

- `setData(data: List<RowData>)`: Update table data
- `setGroupLevel(level: Int)`: Set grouping level
- `setFilter(query: String)`: Set filter text

**Data Models:**

- `RowData(values: Map<String, Any?>)`: Data row
- `ColumnConfig`: Column configuration
- `TableRow.GroupRow`: Group row (internal)
- `TableRow.FileRow`: File row (internal)

### Performance

- Debounced filtering (300ms)
- Efficient rebuild on state changes
- No blocking UI operations
- Responsive for 100+ files

### Integration

Currently integrated in:
- `LgStatsDialog` — Files table with statistics

## LgTaskTextField

Auto-expanding text field for task description (similar to chat input in VS Code).

### Usage

```kotlin
import lg.intellij.ui.components.LgTaskTextField
import lg.intellij.ui.components.LgTaskTextField.addChangeListener

val wrapper = LgTaskTextField.create(
    project = project,
    initialText = "",
    placeholder = "Describe current task",
    rows = 3,
    columns = 60
)

// Listen to changes
wrapper.editorField.addChangeListener { newText ->
    onTaskChanged(newText)
}

// Add to layout
panel.add(wrapper)
```

### Features

- Multi-line editing with soft wrap
- Placeholder support
- Proper theme integration (dark/light)
- DPI-aware sizing
- Border styling similar to VCS commit message field

---

## Development Notes

### Adding New Components

1. Create component class in `ui/components/`
2. Inherit from appropriate Swing/Platform base class
3. Document usage in this README
4. Add test in `test/kotlin/lg/intellij/ui/components/`

### Theming

All components should respect IDE theme:
- Use `JBColor.namedColor()` for colors
- Use `JBUI.scale()` for DPI-aware sizing
- Use `UIUtil` for background/foreground colors

### Testing

Manual testing:
```kotlin
@Ignore("Manual visual test")
fun testComponentVisual() {
    val component = MyComponent()
    
    val frame = JFrame("Test")
    frame.contentPane.add(component)
    frame.setSize(800, 600)
    frame.isVisible = true
    
    Thread.sleep(30000)
}
```

Automated testing:
```kotlin
fun testComponentLogic() {
    val component = MyComponent()
    component.setData(testData)
    
    assertTrue(component.components.isNotEmpty())
}
```

