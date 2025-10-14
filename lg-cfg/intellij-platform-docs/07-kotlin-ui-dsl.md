# Kotlin UI DSL для IntelliJ Platform

## Обзор

**Kotlin UI DSL** — декларативный DSL для создания UI форм с компонентами ввода, связанными с объектом состояния.

**Когда использовать:**
- ✅ Settings/Preferences страницы
- ✅ Dialogs с формами ввода
- ✅ Wizards (пошаговые формы)
- ❌ Tool Windows (используйте Swing напрямую)
- ❌ Panels без форм (используйте Swing напрямую)

**Преимущества:**
- Декларативный синтаксис (меньше boilerplate)
- Автоматический binding к state объекту
- Встроенная валидация
- Следование IntelliJ Platform UI Guidelines
- Type-safe

## Базовый синтаксис

### Простейший пример

```kotlin
import com.intellij.ui.dsl.builder.panel

val myPanel = panel {
    row("Enter value:") {
        textField()
    }
}
```

Результат:
```
┌─────────────────────────────┐
│ Enter value: [___________] │
└─────────────────────────────┘
```

### Структура

```
panel {
    row("Label:") {           // Row с label
        textField()            // Cell с компонентом
        button("...") {}       // Ещё cell
    }
    row {                      // Row без label
        checkBox("Enable")
    }
}
```

**Иерархия:**
- `Panel` — весь контейнер
  - `Row` — горизонтальная строка
    - `Cell` — ячейка с компонентом

## Panel

`Panel` — стартовый интерфейс для построения содержимого.

### panel.row()

Добавляет строку с опциональным label:

```kotlin
panel {
    row("Name:") {
        textField()
    }
    
    row("Email:") {
        textField()
    }
    
    row { // Без label
        checkBox("Subscribe to updates")
    }
}
```

### panel.group()

Группа с заголовком и независимым grid:

```kotlin
panel {
    group("Personal Information") {
        row("Name:") {
            textField()
        }
        row("Age:") {
            intTextField(range = 0..150)
        }
    }
    
    group("Preferences") {
        row {
            checkBox("Dark theme")
        }
    }
}
```

Результат:
```
┌─ Personal Information ────┐
│ Name: [_____________]     │
│ Age:  [____]              │
└───────────────────────────┘

┌─ Preferences ─────────────┐
│ ☑ Dark theme              │
└───────────────────────────┘
```

### panel.collapsibleGroup()

Сворачиваемая группа:

```kotlin
panel {
    collapsibleGroup("Advanced Settings") {
        row("Timeout:") {
            intTextField(range = 1..3600)
        }
        row("Retries:") {
            intTextField(range = 0..10)
        }
    }
}
```

### panel.separator()

Горизонтальный разделитель с опциональным заголовком:

```kotlin
panel {
    row { textField() }
    
    separator()
    
    row { textField() }
    
    separator("Section 2")
    
    row { textField() }
}
```

### panel.indent()

Добавляет стандартный левый отступ:

```kotlin
panel {
    row { checkBox("Enable feature") }
    
    indent {
        row("Option 1:") {
            textField()
        }
        row("Option 2:") {
            textField()
        }
    }
}
```

Результат:
```
☑ Enable feature
    Option 1: [________]
    Option 2: [________]
```

### panel.panel()

Sub-panel с собственным grid:

```kotlin
panel {
    row {
        panel {
            row("Left column:") {
                textField()
            }
        }
        
        panel {
            row("Right column:") {
                textField()
            }
        }
    }
}
```

### panel.buttonsGroup()

Группирует radio buttons или checkboxes:

```kotlin
var selectedValue = "option1"

panel {
    buttonsGroup("Choose option:") {
        row {
            radioButton("Option 1", "option1")
        }
        row {
            radioButton("Option 2", "option2")
        }
        row {
            radioButton("Option 3", "option3")
        }
    }.bind(
        getter = { selectedValue },
        setter = { selectedValue = it }
    )
}
```

## Row

`Row` — строка в панели, содержащая ячейки с компонентами.

### Row Layout

```kotlin
panel {
    // LABEL_ALIGNED (default с label)
    row("Label:") {
        textField() // Label выровнен с другими labels
    }
    
    // INDEPENDENT (собственный grid)
    row("Long label:") {
        textField()
    }.layout(RowLayout.INDEPENDENT)
    
    // PARENT_GRID (все в parent grid)
    row("Label:") {
        textField()
    }.layout(RowLayout.PARENT_GRID)
}
```

### Row visibility и enabled

```kotlin
lateinit var enabledCheckbox: Cell<JBCheckBox>

panel {
    row {
        enabledCheckbox = checkBox("Enable advanced options")
    }
    
    row("Advanced option 1:") {
        textField()
    }.visibleIf(enabledCheckbox.selected)
    
    row("Advanced option 2:") {
        intTextField()
    }.enabledIf(enabledCheckbox.selected)
}
```

**Разница:**
- `visible` — полностью скрывает row
- `enabled` — показывает но делает неактивным (серым)

### Row comments

```kotlin
panel {
    row("API Key:") {
        textField()
            .comment("Get your key at https://example.com/api")
    }.rowComment("This will be stored securely")
}
```

### Row gaps

```kotlin
panel {
    row { textField() }
        .topGap(TopGap.SMALL)
    
    row { textField() }
        .bottomGap(BottomGap.MEDIUM)
}
```

Gaps:
- `NONE` — нет gap
- `SMALL` — между связанными элементами
- `MEDIUM` — между группами (default для `group()`)

### Resizable Row

```kotlin
panel {
    row {
        scrollCell(JTextArea(10, 40))
            .align(Align.FILL)
    }.resizableRow() // Занимает всё доступное место по вертикали
}
```

## Cell

`Cell` — обёртка вокруг компонента в UI DSL.

### Компоненты

```kotlin
panel {
    row {
        // Text field
        textField()
        
        // With columns width
        textField()
            .columns(20)
        
        // With placeholder
        textField()
            .applyToComponent {
                emptyText.text = "Enter value"
            }
    }
}
```

### cell.align()

Выравнивание внутри ячейки:

```kotlin
panel {
    row("Path:") {
        textField()
            .align(AlignX.FILL) // Растянуть на всю ширину
    }
    
    row {
        button("Top Left") {}
            .align(AlignX.LEFT + AlignY.TOP)
        
        button("Center") {}
            .align(Align.CENTER)
        
        button("Bottom Right") {}
            .align(AlignX.RIGHT + AlignY.BOTTOM)
    }
}
```

Константы:
- `AlignX.LEFT`, `AlignX.CENTER`, `AlignX.RIGHT`, `AlignX.FILL`
- `AlignY.TOP`, `AlignY.CENTER`, `AlignY.BOTTOM`, `AlignY.FILL`
- `Align.FILL` = `AlignX.FILL + AlignY.FILL`

### cell.resizableColumn()

Колонка занимает всё доступное пространство:

```kotlin
panel {
    row("File:") {
        textField()
            .resizableColumn() // Растянется
        button("Browse") {}     // Фиксированная ширина
    }
}
```

### cell.gap()

Отступ справа от ячейки:

```kotlin
panel {
    row {
        checkBox("Use custom:")
            .gap(RightGap.SMALL)
        textField()
    }
    
    row("Width:") {
        intTextField()
            .gap(RightGap.SMALL)
        label("pixels")
    }
}
```

### cell.comment()

Комментарий под компонентом:

```kotlin
panel {
    row("API Key:") {
        passwordField()
            .comment("Will be stored in secure storage")
    }
    
    row("Timeout:") {
        intTextField()
            .comment("In seconds. Default: 30")
    }
}
```

**HTML поддержка:**
```kotlin
textField()
    .comment("Visit <a href='https://example.com'>documentation</a>")
```

### cell.label()

Label для компонента (сверху или сбоку):

```kotlin
panel {
    row {
        textField()
            .label("Enter name:", LabelPosition.TOP)
    }
    
    row {
        textArea()
            .label("Description:", LabelPosition.TOP)
    }
}
```

### cell.visible() / enabled()

```kotlin
lateinit var advancedCheckbox: Cell<JBCheckBox>

panel {
    row {
        advancedCheckbox = checkBox("Show advanced")
    }
    
    row("Advanced:") {
        textField()
    }.visibleIf(advancedCheckbox.selected)
    
    row("Another:") {
        textField()
    }.enabledIf(advancedCheckbox.selected)
}
```

## Components (компоненты)

### Text Input

```kotlin
panel {
    // Simple text field
    row("Name:") {
        textField()
            .columns(30)
    }
    
    // Password field
    row("Password:") {
        cell(JPasswordField())
            .columns(20)
    }
    
    // Text area
    row("Description:") {
        textArea()
            .rows(5)
            .columns(40)
    }
    
    // Editor text field (с syntax highlighting)
    row("Code:") {
        val editorField = EditorTextField("", project, FileType.INSTANCE)
        cell(editorField)
            .align(AlignX.FILL)
    }
}
```

### Numbers

```kotlin
panel {
    // Int field
    row("Port:") {
        intTextField(range = 1..65535)
    }
    
    // Int field со step
    row("Timeout:") {
        intTextField(range = 0..3600, step = 10)
    }
    
    // Spinner
    row("Count:") {
        spinner(0..100, step = 5)
    }
}
```

### Checkboxes

```kotlin
panel {
    row {
        checkBox("Enable feature")
    }
    
    row {
        checkBox("Use custom path")
            .comment("Override default path")
    }
    
    // Checkbox с действием
    row {
        checkBox("Auto-save")
            .onChanged { checkbox ->
                if (checkbox.isSelected) {
                    startAutoSave()
                } else {
                    stopAutoSave()
                }
            }
    }
}
```

### Radio Buttons

```kotlin
var color = Color.RED

panel {
    buttonsGroup("Select color:") {
        row {
            radioButton("Red", Color.RED)
        }
        row {
            radioButton("Green", Color.GREEN)
        }
        row {
            radioButton("Blue", Color.BLUE)
        }
    }.bind(
        getter = { color },
        setter = { color = it }
    )
}
```

### ComboBox

```kotlin
panel {
    row("Framework:") {
        comboBox(listOf("Spring", "Quarkus", "Micronaut"))
    }
    
    // С кастомным renderer
    row("File:") {
        comboBox(files, SimpleListCellRenderer.create { label, file, _ ->
            label.text = file.name
            label.icon = file.fileType.icon
        })
    }
}
```

### Buttons

```kotlin
panel {
    row {
        button("Execute") {
            // Button click handler
            executeAction()
        }
        
        button("Cancel") {
            closeDialog()
        }
    }
    
    // Link button
    row {
        link("Open documentation") {
            BrowserUtil.browse("https://example.com/docs")
        }
    }
}
```

### Sliders

```kotlin
panel {
    row("Volume:") {
        slider(0, 100, 10, 50) // min, max, minorTick, majorTick
    }
}
```

### Browsable File/Directory Pickers

```kotlin
panel {
    row("File:") {
        textFieldWithBrowseButton(
            "Select File",
            project,
            FileChooserDescriptorFactory.createSingleFileDescriptor()
        )
    }
    
    row("Directory:") {
        textFieldWithBrowseButton(
            "Select Directory",
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )
    }
}
```

### Cell для custom компонента

```kotlin
panel {
    row("Custom:") {
        val myComponent = JPanel()
        cell(myComponent)
            .align(AlignX.FILL)
    }
}
```

### scrollCell() — компонент в scroll pane

```kotlin
panel {
    row {
        val textArea = JTextArea(20, 60)
        scrollCell(textArea)
            .align(Align.FILL)
    }.resizableRow()
}
```

## Data Binding

### bindText() — String properties

```kotlin
class MySettings {
    var username: String = ""
    var apiUrl: String = "https://api.example.com"
}

val settings = MySettings()

panel {
    row("Username:") {
        textField()
            .bindText(settings::username)
    }
    
    row("API URL:") {
        textField()
            .bindText(settings::apiUrl)
    }
}
```

**Работает автоматически:**
- `DialogPanel.apply()` → запись в `settings`
- `DialogPanel.reset()` → чтение из `settings`
- `DialogPanel.isModified()` → проверка изменений

### bindSelected() — Boolean properties

```kotlin
class MySettings {
    var enableFeature: Boolean = false
    var autoSave: Boolean = true
}

val settings = MySettings()

panel {
    row {
        checkBox("Enable feature")
            .bindSelected(settings::enableFeature)
    }
    
    row {
        checkBox("Auto-save")
            .bindSelected(settings::autoSave)
    }
}
```

### bindIntText() — Int properties

```kotlin
class MySettings {
    var port: Int = 8080
    var timeout: Int = 30
}

val settings = MySettings()

panel {
    row("Port:") {
        intTextField(range = 1..65535)
            .bindIntText(settings::port)
    }
    
    row("Timeout (sec):") {
        intTextField(range = 0..300)
            .bindIntText(settings::timeout)
    }
}
```

### bindItem() — ComboBox binding

```kotlin
enum class LogLevel { DEBUG, INFO, WARN, ERROR }

class MySettings {
    var logLevel: LogLevel = LogLevel.INFO
}

val settings = MySettings()

panel {
    row("Log Level:") {
        comboBox(LogLevel.values().toList())
            .bindItem(settings::logLevel)
    }
}
```

### bindValue() — Slider binding

```kotlin
class MySettings {
    var volume: Int = 50
}

val settings = MySettings()

panel {
    row("Volume:") {
        slider(0, 100, 10, 50)
            .bindValue(settings::volume)
    }
}
```

### bind() — Radio buttons binding

```kotlin
enum class Theme { LIGHT, DARK, SYSTEM }

class MySettings {
    var theme: Theme = Theme.SYSTEM
}

val settings = MySettings()

panel {
    buttonsGroup("Theme:") {
        row { radioButton("Light", Theme.LIGHT) }
        row { radioButton("Dark", Theme.DARK) }
        row { radioButton("System", Theme.SYSTEM) }
    }.bind(settings::theme)
}
```

## Validation

### Cell Validation

```kotlin
panel {
    row("Port:") {
        intTextField(range = 1..65535)
            .validationOnInput {
                val value = it.text.toIntOrNull()
                when {
                    value == null -> 
                        error("Must be a number")
                    value !in 1..65535 -> 
                        error("Port must be 1-65535")
                    value < 1024 ->
                        warning("Ports below 1024 require admin rights")
                    else -> null
                }
            }
            .validationOnApply {
                val value = it.text.toIntOrNull() ?: return@validationOnApply error("Invalid")
                if (isPortInUse(value)) {
                    error("Port $value already in use")
                } else null
            }
    }
}
```

**Validation triggers:**
- `validationOnInput` — при каждом изменении (typing)
- `validationOnApply` — при нажатии OK/Apply

**Validation results:**
- `null` — OK
- `error("message")` — ошибка (❌ красная рамка)
- `warning("message")` — предупреждение (⚠️ жёлтая рамка)

### Row Validation

```kotlin
panel {
    row("Email:") {
        textField()
    }.validation {
        val email = component.text
        if (!email.matches(Regex(".+@.+\\..+"))) {
            error("Invalid email format")
        } else null
    }
}
```

## Conditional Visibility

### visibleIf() / enabledIf()

```kotlin
lateinit var useCustomCheckbox: Cell<JBCheckBox>
lateinit var customPathField: Cell<JBTextField>

panel {
    row {
        useCustomCheckbox = checkBox("Use custom path")
    }
    
    row("Path:") {
        customPathField = textField()
            .align(AlignX.FILL)
    }.visibleIf(useCustomCheckbox.selected)
    
    row {
        button("Browse") {
            // ...
        }
    }.enabledIf(useCustomCheckbox.selected)
}
```

### Predicates

```kotlin
val predicate = object : ComponentPredicate() {
    override fun invoke(): Boolean {
        return useCustomCheckbox.component.isSelected &&
               customPathField.component.text.isNotBlank()
    }
    
    override fun addListener(listener: (Boolean) -> Unit) {
        useCustomCheckbox.component.addActionListener {
            listener(invoke())
        }
        customPathField.component.document.addDocumentListener(
            object : DocumentAdapter() {
                override fun textChanged(e: DocumentEvent) {
                    listener(invoke())
                }
            }
        )
    }
}

row {
    button("Process") { }
}.enabledIf(predicate)
```

## Callbacks

### onApply / onReset / onIsModified

```kotlin
panel {
    row("Value:") {
        textField()
    }
    
    onApply {
        println("Panel applied")
        saveToFile()
    }
    
    onReset {
        println("Panel reset")
        loadFromFile()
    }
    
    onIsModified {
        println("Checking if modified")
    }
}
```

### Cell callbacks

```kotlin
panel {
    row("Value:") {
        textField()
            .onApply {
                val value = component.text
                applyValue(value)
            }
            .onReset {
                component.text = loadValue()
            }
            .onIsModified {
                component.text != loadValue()
            }
    }
}
```

### onChange callbacks

```kotlin
panel {
    row("Filter:") {
        textField()
            .onChanged { textField ->
                val query = textField.text
                performSearch(query)
            }
    }
    
    row {
        checkBox("Enable logging")
            .onChanged { checkbox ->
                updateLoggingState(checkbox.isSelected)
            }
    }
}
```

## Placeholder (динамическая замена)

`Placeholder` — резервная ячейка для динамического контента.

```kotlin
lateinit var contentPlaceholder: Placeholder

panel {
    row {
        contentPlaceholder = placeholder()
    }
}

// Позже заменить контент
fun updateContent(data: Data) {
    contentPlaceholder.component = createContentPanel(data)
}

// Или очистить
fun clearContent() {
    contentPlaceholder.component = null
}
```

## Использование в Dialogs

### DialogWrapper с UI DSL

```kotlin
import com.intellij.openapi.ui.DialogWrapper

class MyDialog(
    private val project: Project?
) : DialogWrapper(project) {
    
    private var name: String = ""
    private var email: String = ""
    private var subscribe: Boolean = false
    
    init {
        title = "User Information"
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        return panel {
            row("Name:") {
                textField()
                    .bindText(::name)
                    .validationOnInput {
                        if (it.text.isBlank()) {
                            error("Name cannot be empty")
                        } else null
                    }
            }
            
            row("Email:") {
                textField()
                    .bindText(::email)
                    .validationOnApply {
                        val email = it.text
                        if (!email.matches(Regex(".+@.+\\..+"))) {
                            error("Invalid email format")
                        } else null
                    }
            }
            
            row {
                checkBox("Subscribe to newsletter")
                    .bindSelected(::subscribe)
            }
        }
    }
    
    // Data доступна после showAndGet()
    fun getData(): UserData {
        return UserData(name, email, subscribe)
    }
}

// Использование
val dialog = MyDialog(project)
if (dialog.showAndGet()) {
    val data = dialog.getData()
    processUserData(data)
}
```

## Использование в Settings

### BoundConfigurable

```kotlin
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText

class MySettingsConfigurable(
    private val project: Project
) : BoundConfigurable("My Plugin Settings") {
    
    private val settings = project.service<MySettings>()
    
    override fun createPanel() = panel {
        group("General") {
            row("API Key:") {
                textField()
                    .bindText(settings.state::apiKey)
                    .columns(40)
            }
            
            row {
                checkBox("Enable feature")
                    .bindSelected(settings.state::enableFeature)
            }
        }
        
        group("Advanced") {
            row("Timeout:") {
                intTextField(range = 1..300)
                    .bindIntText(settings.state::timeout)
            }
            
            row("Max results:") {
                intTextField(range = 1..1000)
                    .bindIntText(settings.state::maxResults)
            }
        }
    }
}
```

**BoundConfigurable** автоматически:
- Вызывает `apply()` при нажатии OK/Apply
- Вызывает `reset()` при открытии или Cancel
- Проверяет `isModified()` для активации Apply button

## Advanced Techniques

### Динамические списки

```kotlin
class DynamicListPanel : DialogPanel() {
    
    private val items = mutableListOf<Item>()
    private lateinit var itemsPanel: Panel
    
    init {
        panel {
            itemsPanel = panel {
                updateItemsUI()
            }
            
            row {
                button("Add Item") {
                    items.add(Item())
                    updateItemsUI()
                }
            }
        }
    }
    
    private fun Panel.updateItemsUI() {
        // Очистить существующие rows
        // (UI DSL не поддерживает динамическое удаление, 
        // нужно пересоздать panel)
        
        for ((index, item) in items.withIndex()) {
            row("Item $index:") {
                textField()
                    .bindText(item::value)
                button("Remove") {
                    items.removeAt(index)
                    updateItemsUI()
                }
            }
        }
    }
}
```

**Примечание:** UI DSL не поддерживает динамическое добавление/удаление rows. Для таких случаев лучше использовать Table или List.

### Collapsible Sections

```kotlin
panel {
    collapsibleGroup("Database Settings", indent = false) {
        row("Host:") {
            textField()
        }
        row("Port:") {
            intTextField()
        }
    }
    
    collapsibleGroup("Cache Settings", indent = false) {
        row("Size (MB):") {
            intTextField()
        }
        row("TTL (sec):") {
            intTextField()
        }
    }
}
```

### Conditional Rows

```kotlin
enum class Mode { SIMPLE, ADVANCED }

var mode = Mode.SIMPLE

panel {
    buttonsGroup("Mode:") {
        row { radioButton("Simple", Mode.SIMPLE) }
        row { radioButton("Advanced", Mode.ADVANCED) }
    }.bind(::mode)
    
    separator()
    
    // Simple mode fields
    row("Quick setup:") {
        textField()
    }.visibleIf(object : ComponentPredicate() {
        override fun invoke() = mode == Mode.SIMPLE
        override fun addListener(listener: (Boolean) -> Unit) {
            // Listen to mode changes
        }
    })
    
    // Advanced mode fields
    group("Advanced Configuration") {
        row("Option 1:") { textField() }
        row("Option 2:") { textField() }
    }.visibleIf(object : ComponentPredicate() {
        override fun invoke() = mode == Mode.ADVANCED
        override fun addListener(listener: (Boolean) -> Unit) { }
    })
}
```

## Sizing и Layout

### Width Hints

```kotlin
panel {
    row("Short:") {
        textField()
            .columns(10) // ~10 символов
    }
    
    row("Long:") {
        textField()
            .columns(60) // ~60 символов
    }
    
    row("Fill:") {
        textField()
            .align(AlignX.FILL) // Вся доступная ширина
    }
}
```

### Height Hints

```kotlin
panel {
    row {
        textArea()
            .rows(10) // Высота в строках
            .align(Align.FILL)
    }.resizableRow()
}
```

### Component Constraints

```kotlin
panel {
    row("Path:") {
        textField()
            .align(AlignX.FILL)
            .resizableColumn()
            .columns(40)
            .applyToComponent {
                minimumSize = JBUI.size(200, -1)
            }
    }
}
```

## Tips и Examples

### Две колонки

```kotlin
panel {
    row {
        panel {
            row("Left column:") {
                textField()
            }
            row("Field 2:") {
                textField()
            }
        }.align(AlignX.FILL)
        
        panel {
            row("Right column:") {
                textField()
            }
            row("Field 2:") {
                textField()
            }
        }.align(AlignX.FILL)
    }
}
```

### Label + Component + Button

```kotlin
panel {
    row("Path:") {
        textField()
            .align(AlignX.FILL)
            .resizableColumn()
        button("Browse") {
            selectPath()
        }
    }
}
```

### Checkbox + Dependent Field

```kotlin
lateinit var enabledCheckbox: Cell<JBCheckBox>

panel {
    row {
        enabledCheckbox = checkBox("Use custom server:")
            .gap(RightGap.SMALL)
        textField()
            .align(AlignX.FILL)
            .enabledIf(enabledCheckbox.selected)
    }
}
```

### Группа с условными полями

```kotlin
lateinit var authTypeCombo: Cell<ComboBox<String>>

panel {
    group("Authentication") {
        row("Type:") {
            authTypeCombo = comboBox(listOf("None", "Basic", "OAuth"))
        }
        
        // Basic auth fields
        row("Username:") {
            textField()
        }.visibleIf(object : ComponentPredicate() {
            override fun invoke() = authTypeCombo.component.selectedItem == "Basic"
            override fun addListener(listener: (Boolean) -> Unit) {
                authTypeCombo.component.addActionListener { listener(invoke()) }
            }
        })
        
        row("Password:") {
            cell(JPasswordField())
        }.visibleIf(/* same predicate */)
        
        // OAuth fields
        row("Token:") {
            textField()
        }.visibleIf(object : ComponentPredicate() {
            override fun invoke() = authTypeCombo.component.selectedItem == "OAuth"
            override fun addListener(listener: (Boolean) -> Unit) {
                authTypeCombo.component.addActionListener { listener(invoke()) }
            }
        })
    }
}
```

## DialogPanel Methods

После создания `panel { }` получаете `DialogPanel`:

```kotlin
val dialogPanel = panel {
    row("Name:") {
        textField().bindText(::name)
    }
}

// Применить изменения к bound properties
dialogPanel.apply()

// Сбросить к исходным значениям
dialogPanel.reset()

// Проверить наличие изменений
val modified = dialogPanel.isModified()

// Валидация всех полей
val errors = dialogPanel.validateAll()
```

## UI DSL Showcase (примеры в IDE)

Для изучения всех возможностей UI DSL:

1. Активируйте [Internal Mode](01-getting-started.md#internal-mode)
2. **Tools → Internal Actions → UI → UI DSL Showcase**
3. Изучите вкладки:
   - **Basics** — базовый синтаксис
   - **Components** — все доступные компоненты
   - **Gaps** — отступы и spacing
   - **Groups** — группировка
   - **Row Layout** — layouts
   - **Comments** — комментарии
   - **Enabled/Visible** — conditional visibility
   - **Binding** — data binding
   - И многое другое...

Каждая вкладка содержит:
- Визуальный пример
- Исходный код (ссылка "View source")
- Пояснения

## Миграция с UI DSL Version 1

Если у вас legacy код с UI DSL Version 1 (`com.intellij.ui.layout`):

1. Замените импорты:
   ```kotlin
   // ❌ Old
   import com.intellij.ui.layout.*
   
   // ✅ New
   import com.intellij.ui.dsl.builder.*
   ```

2. Обновите API:
   ```kotlin
   // ❌ Old
   row("Label:") { textField(::property) }
   
   // ✅ New
   row("Label:") {
       textField().bindText(::property)
   }
   ```

3. Используйте align() вместо deprecated APIs:
   ```kotlin
   // ❌ Old (deprecated 2022.3)
   textField().horizontalAlign(HorizontalAlign.FILL)
   
   // ✅ New
   textField().align(AlignX.FILL)
   ```

## Best Practices

### 1. Используйте data classes для state

```kotlin
// ✅ Clean
data class MySettings(
    var apiKey: String = "",
    var timeout: Int = 30,
    var enabled: Boolean = false
)

val settings = MySettings()
panel {
    row("Key:") {
        textField().bindText(settings::apiKey)
    }
}
```

### 2. Извлекайте сложные panels в методы

```kotlin
// ❌ Плохо — всё в одном месте
override fun createPanel() = panel {
    // 500 lines of UI DSL...
}

// ✅ Хорошо — модульно
override fun createPanel() = panel {
    group("General") {
        createGeneralSettings()
    }
    group("Advanced") {
        createAdvancedSettings()
    }
}

private fun Panel.createGeneralSettings() {
    row("Name:") { textField() }
    row("Email:") { textField() }
}

private fun Panel.createAdvancedSettings() {
    row("Timeout:") { intTextField() }
    row("Retries:") { intTextField() }
}
```

### 3. Используйте lateinit для conditional visibility

```kotlin
// ✅ Правильно
lateinit var enabledCheckbox: Cell<JBCheckBox>

panel {
    row {
        enabledCheckbox = checkBox("Enable")
    }
    
    row("Option:") {
        textField()
    }.visibleIf(enabledCheckbox.selected)
}
```

### 4. Избегайте сложной логики в UI DSL

```kotlin
// ❌ Плохо
panel {
    for (item in loadItemsFromDatabase()) { // I/O в UI build!
        row(item.name) {
            textField().bindText(item::value)
        }
    }
}

// ✅ Хорошо
val items = loadedItems // Загружено заранее

panel {
    for (item in items) {
        row(item.name) {
            textField().bindText(item::value)
        }
    }
}
```

## Limitations (ограничения)

UI DSL **не подходит** для:

1. **Tool Windows** без форм ввода
   - Используйте Swing/Platform components

2. **Динамические списки** с add/remove
   - Используйте `JBList` или `JBTable`

3. **Tree views**
   - Используйте `Tree` напрямую

4. **Сложный custom layout**
   - Используйте Swing Layout Managers

## Полезные ссылки

- [Kotlin UI DSL Docs](https://plugins.jetbrains.com/docs/intellij/kotlin-ui-dsl-version-2.html)
- [UI DSL Builder API](https://github.com/JetBrains/intellij-community/tree/master/platform/platform-impl/src/com/intellij/ui/dsl/builder)
- [UI Guidelines](https://jetbrains.design/intellij/)
