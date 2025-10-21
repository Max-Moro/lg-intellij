## LgGroupedTable

Продвинутая таблица с иерархической группировкой, фильтрацией и сортировкой.

### Features

- **Hierarchical Grouping**: Группировка файлов по уровням вложенности директорий (1..N или ∞ для flat)
- **Filtering**: Быстрая фильтрация по path или extension (debounced 300ms)
- **Sorting**: Сортировка по любой колонке с визуальными индикаторами (▲/▼)
- **Aggregation**: Автоматическое суммирование/усреднение числовых колонок для групп
- **Path Normalization**: Автоматическое выравнивание путей (файлы рядом с директориями → виртуальная `self/`)

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

- `key` (String, required): Ключ данных в RowData
- `label` (String, required): Заголовок колонки
- `format` (ColumnFormat): Формат отображения значений
  - `TEXT` — plain text
  - `INT` — целые числа с разделителями (1,234,567)
  - `PERCENT` — проценты с одним знаком (12.3%)
  - `SIZE` — размеры в байтах (1.5 KB, 2.3 MB)
- `align` (Align): Выравнивание (LEFT | RIGHT)
- `sortable` (Boolean): Можно ли сортировать по этой колонке
- `aggregate` (Aggregate?): Метод агрегации для групп
  - `SUM` — сумма значений
  - `AVG` — среднее значение
  - `null` — не агрегировать
- `aggregateFormula` ((Map<String, Any?>) -> Any?): Произвольная формула для агрегации

### Grouping Control

Контрол группировки позволяет изменять уровень вложенности:

- **← / →**: Уменьшить/увеличить уровень группировки
- **∞**: Отключить группировку (показывать все файлы flat)
- **1-N**: Группировать до N-го уровня вложенности

Максимальный уровень определяется автоматически на основе структуры путей в данных.

### Path Normalization

При группировке компонент автоматически нормализует пути так, чтобы файлы не смешивались с директориями на одном уровне:

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

- `setData(data: List<RowData>)`: Обновить данные таблицы
- `setGroupLevel(level: Int)`: Установить уровень группировки
- `setFilter(query: String)`: Установить текст фильтра

**Data Models:**

- `RowData(values: Map<String, Any?>)`: Строка данных
- `ColumnConfig`: Конфигурация колонки
- `TableRow.GroupRow`: Строка группы (internal)
- `TableRow.FileRow`: Строка файла (internal)

### Performance

- Debounced filtering (300ms)
- Efficient rebuild on state changes
- No blocking UI operations
- Responsive for 100+ files

### Integration

Currently integrated in:
- `LgStatsDialog` — Files table with statistics

## LgTaskTextField

Auto-expanding text field для task description (аналог chat input в VS Code).

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

- Multi-line editing с soft wrap
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

