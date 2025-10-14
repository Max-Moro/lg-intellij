# UI Components в IntelliJ Platform

## Общие принципы UI

IntelliJ Platform использует **Swing** как базовую UI библиотеку с дополнительными обёртками и утилитами от JetBrains.

### Основные UI фреймворки

1. **Swing** — базовая библиотека Java
2. **IntelliJ Platform UI Components** — расширенные компоненты
3. **Kotlin UI DSL** — декларативный DSL для форм (рекомендуется)

### Когда что использовать

| Задача | Решение |
|--------|---------|
| Settings/Preferences UI | **Kotlin UI DSL** |
| Dialogs с формами | **Kotlin UI DSL** |
| Tool Windows | **Swing** + Platform Components |
| Simple panels | **Swing** или **Kotlin UI DSL** |
| Tables/Trees | Platform Components (`JBTable`, `Tree`) |
| Actions/Toolbars | Platform API |

## Базовые Swing Components

### JPanel — контейнер

```kotlin
import javax.swing.JPanel
import java.awt.BorderLayout

val panel = JPanel(BorderLayout()).apply {
    add(createTopPanel(), BorderLayout.NORTH)
    add(createMainPanel(), BorderLayout.CENTER)
    add(createBottomPanel(), BorderLayout.SOUTH)
}
```

Стандартные Layout Managers:
- `BorderLayout` — 5 зон (North, South, East, West, Center)
- `GridLayout` — таблица ячеек
- `FlowLayout` — горизонтальный flow
- `BoxLayout` — вертикальный или горизонтальный
- `GridBagLayout` — гибкий grid (сложный)

### JLabel — текст

```kotlin
import javax.swing.JLabel

val label = JLabel("Hello, World!")
label.text = "New text"
label.icon = AllIcons.General.Information
```

### JButton — кнопка

```kotlin
import javax.swing.JButton

val button = JButton("Click Me").apply {
    addActionListener {
        // Handle click
        println("Button clicked!")
    }
}
```

### JTextField — ввод текста

```kotlin
import javax.swing.JTextField

val textField = JTextField("Initial value", 20).apply {
    // Слушать изменения
    document.addDocumentListener(object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent) = textChanged()
        override fun removeUpdate(e: DocumentEvent) = textChanged()
        override fun changedUpdate(e: DocumentEvent) = textChanged()
        
        private fun textChanged() {
            val newValue = text
            println("Text changed: $newValue")
        }
    })
}
```

### JCheckBox и JRadioButton

```kotlin
import javax.swing.JCheckBox
import javax.swing.JRadioButton
import javax.swing.ButtonGroup

// Checkbox
val checkbox = JCheckBox("Enable feature", true).apply {
    addActionListener {
        println("Checked: $isSelected")
    }
}

// Radio buttons
val group = ButtonGroup()
val radio1 = JRadioButton("Option 1", true)
val radio2 = JRadioButton("Option 2")
group.add(radio1)
group.add(radio2)
```

### JComboBox — выпадающий список

```kotlin
import javax.swing.JComboBox

val comboBox = JComboBox(arrayOf("Option 1", "Option 2", "Option 3"))
comboBox.selectedItem = "Option 2"
comboBox.addActionListener {
    val selected = comboBox.selectedItem
    println("Selected: $selected")
}
```

## IntelliJ Platform UI Components

Платформа предоставляет улучшенные версии Swing компонентов.

### JBTextField — улучшенный text field

```kotlin
import com.intellij.ui.components.JBTextField

val textField = JBTextField("Initial text").apply {
    emptyText.text = "Enter value here" // Placeholder
    columns = 30 // Preferred width
}
```

### JBCheckBox — улучшенный checkbox

```kotlin
import com.intellij.ui.components.JBCheckBox

val checkbox = JBCheckBox("Enable feature", true)
```

### JBLabel — улучшенный label

```kotlin
import com.intellij.ui.components.JBLabel

val label = JBLabel("Text with icon", AllIcons.General.Information, SwingConstants.LEFT)
label.setCopyable(true) // Можно скопировать текст
```

### JBList — улучшенный список

```kotlin
import com.intellij.ui.components.JBList
import javax.swing.DefaultListModel

val model = DefaultListModel<String>().apply {
    addElement("Item 1")
    addElement("Item 2")
    addElement("Item 3")
}

val list = JBList(model).apply {
    selectionMode = ListSelectionModel.SINGLE_SELECTION
    
    addListSelectionListener {
        if (!it.valueIsAdjusting) {
            val selected = selectedValue
            println("Selected: $selected")
        }
    }
}
```

### JBTable — улучшенная таблица

```kotlin
import com.intellij.ui.table.JBTable
import javax.swing.table.DefaultTableModel

val model = DefaultTableModel(
    arrayOf(
        arrayOf("File 1", "100", "1000"),
        arrayOf("File 2", "200", "2000")
    ),
    arrayOf("Name", "Size", "Tokens")
)

val table = JBTable(model).apply {
    setShowGrid(true)
    autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
}
```

### Tree — дерево

```kotlin
import com.intellij.ui.treeStructure.Tree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

val root = DefaultMutableTreeNode("Root")
root.add(DefaultMutableTreeNode("Child 1"))
root.add(DefaultMutableTreeNode("Child 2"))

val tree = Tree(DefaultTreeModel(root)).apply {
    isRootVisible = false
    selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
    
    addTreeSelectionListener {
        val node = lastSelectedPathComponent as? DefaultMutableTreeNode
        val value = node?.userObject
        println("Selected: $value")
    }
}
```

## Scroll Panes

Для прокручиваемого содержимого:

```kotlin
import com.intellij.ui.components.JBScrollPane

val scrollPane = JBScrollPane(largeComponent).apply {
    horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
    verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
}
```

## Panels и Layouts

### SimpleToolWindowPanel

Специальный panel для Tool Windows:

```kotlin
import com.intellij.openapi.ui.SimpleToolWindowPanel

class MyToolWindowContent {
    
    fun createPanel(): JComponent {
        val panel = SimpleToolWindowPanel(true, true) // vertical, borderless
        
        // Toolbar сверху
        panel.toolbar = createToolbar()
        
        // Основной контент
        panel.setContent(createMainContent())
        
        return panel
    }
    
    private fun createToolbar(): JComponent {
        val actionGroup = DefaultActionGroup().apply {
            add(MyRefreshAction())
            add(MyConfigAction())
        }
        
        val toolbar = ActionManager.getInstance()
            .createActionToolbar("MyToolbar", actionGroup, true)
        
        toolbar.targetComponent = panel // Важно для правильного context
        
        return toolbar.component
    }
}
```

### BorderLayoutPanel (DSL)

```kotlin
import com.intellij.util.ui.components.BorderLayoutPanel

val panel = BorderLayoutPanel().apply {
    addToTop(createHeader())
    addToCenter(createContent())
    addToBottom(createFooter())
    addToLeft(createSidebar())
}
```

## Action Toolbar и Popup Menu

### Создание Toolbar

```kotlin
import com.intellij.openapi.actionSystem.*

fun createToolbar(targetComponent: JComponent): JComponent {
    // Создать группу actions
    val actionGroup = DefaultActionGroup().apply {
        add(GenerateAction())
        add(RefreshAction())
        addSeparator()
        add(SettingsAction())
    }
    
    // Создать toolbar
    val toolbar = ActionManager.getInstance().createActionToolbar(
        "MyToolbar",           // Place ID
        actionGroup,           // Actions
        true                   // horizontal
    )
    
    // Установить target component (важно!)
    toolbar.targetComponent = targetComponent
    
    return toolbar.component
}
```

**Почему targetComponent важен:**
- Actions получают context (Project, Editor и т.д.) из target component
- Без него context будет от текущего фокуса в IDE

### Создание Popup Menu

```kotlin
fun showPopupMenu(component: JComponent, x: Int, y: Int) {
    val actionGroup = DefaultActionGroup().apply {
        add(CopyAction())
        add(PasteAction())
        addSeparator()
        add(DeleteAction())
    }
    
    val popupMenu = ActionManager.getInstance()
        .createActionPopupMenu("MyPopup", actionGroup)
    
    popupMenu.component.show(component, x, y)
}

// Показать по правому клику
component.addMouseListener(object : MouseAdapter() {
    override fun mousePressed(e: MouseEvent) {
        if (e.isPopupTrigger) {
            showPopupMenu(e.component as JComponent, e.x, e.y)
        }
    }
})
```

## UI Utilities

### UIUtil

```kotlin
import com.intellij.util.ui.UIUtil

// Цвета темы
val bgColor = UIUtil.getPanelBackground()
val textColor = UIUtil.getLabelForeground()

// EDT check
if (UIUtil.isClientPropertyTrue(component, "myProperty")) {
    // ...
}

// Invoke on EDT
UIUtil.invokeLaterIfNeeded {
    updateUI()
}
```

### JBUI (отступы, размеры)

```kotlin
import com.intellij.util.ui.JBUI

// Отступы
panel.border = JBUI.Borders.empty(10) // All sides
panel.border = JBUI.Borders.empty(10, 20) // Vertical, Horizontal
panel.border = JBUI.Borders.emptyTop(10)
panel.border = JBUI.Borders.emptyLeft(10)

// Размеры (DPI-aware)
val size = JBUI.size(100, 50)
val dimension = JBUI.size(200)

// Insets
val insets = JBUI.insets(10, 15, 10, 15)

// Scale (для Retina)
val scaled = JBUI.scale(16) // 16px → 32px на Retina
```

### JBColor — цвета для dark/light themes

```kotlin
import com.intellij.ui.JBColor

// Разные цвета для light/dark theme
val color = JBColor(
    Color(200, 200, 200),  // Light theme
    Color(60, 60, 60)      // Dark theme
)

// Из named colors
val textColor = JBColor.namedColor("Label.foreground", JBColor.BLACK)
```

## Специализированные компоненты

### ComboBox с поиском

```kotlin
import com.intellij.openapi.ui.ComboBox

val items = listOf("Item 1", "Item 2", "Item 3")
val comboBox = ComboBox(items.toTypedArray())

// С кастомным renderer
comboBox.renderer = object : SimpleListCellRenderer<String>() {
    override fun customize(
        list: JList<out String>,
        value: String?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean
    ) {
        text = value ?: ""
        icon = AllIcons.FileTypes.Text
    }
}
```

### TextFieldWithBrowseButton

```kotlin
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory

val textField = TextFieldWithBrowseButton().apply {
    addBrowseFolderListener(
        "Select Directory",
        "Choose lg-cfg directory",
        project,
        FileChooserDescriptorFactory.createSingleFolderDescriptor()
    )
}
```

### EditorTextField — редактор с syntax highlighting

```kotlin
import com.intellij.ui.EditorTextField

val editorField = EditorTextField(
    "code here",
    project,
    FileTypeManager.getInstance().getFileTypeByExtension("py")
).apply {
    setOneLineMode(false)
    setPlaceholder("Enter Python code")
}
```

### SearchTextField

```kotlin
import com.intellij.ui.SearchTextField

val searchField = SearchTextField(true).apply { // true = history support
    addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
            val query = text
            performSearch(query)
        }
    })
}
```

## Validating Input

### ComponentValidator

```kotlin
import com.intellij.openapi.ui.validation.DialogValidation

val textField = JBTextField()

ComponentValidator(disposable)
    .withValidator {
        val text = textField.text
        when {
            text.isBlank() -> 
                ValidationInfo("Field cannot be empty", textField)
            !text.matches(Regex("[0-9]+")) -> 
                ValidationInfo("Only numbers allowed", textField)
            else -> null
        }
    }
    .installOn(textField)
    .andRegisterOnDocumentListener(textField)
```

Валидация в реальном времени:
- ❌ Красная рамка при ошибке
- ⚠️ Жёлтая рамка при warning
- ✓ Зелёная при корректном значении

## Icons и Images

### Загрузка иконок

```kotlin
import com.intellij.openapi.util.IconLoader

object MyIcons {
    @JvmField
    val ToolWindow = IconLoader.getIcon(
        "/icons/toolWindow.svg",
        MyIcons::class.java
    )
    
    @JvmField
    val Generate = load("/icons/generate.svg")
    
    private fun load(path: String) = IconLoader.getIcon(path, javaClass)
}
```

### Встроенные иконки (AllIcons)

```kotlin
import com.intellij.icons.AllIcons

// Часто используемые
AllIcons.Actions.Execute
AllIcons.Actions.Refresh
AllIcons.Actions.Cancel
AllIcons.General.Settings
AllIcons.General.Information
AllIcons.General.Warning
AllIcons.General.Error
AllIcons.Toolwindows.Documentation
AllIcons.FileTypes.Text
AllIcons.FileTypes.Json
```

Полный список: [`AllIcons`](https://github.com/JetBrains/intellij-community/blob/master/platform/util/ui/src/com/intellij/icons/AllIcons.java)

### Адаптивные иконки для темы

```kotlin
import com.intellij.ui.LayeredIcon
import com.intellij.util.IconUtil

// Иконка меняется в зависимости от темы
val icon = IconUtil.colorize(
    AllIcons.Actions.Execute,
    JBColor.namedColor("Button.foreground")
)
```

## Progress Indicators

### Background Task с Progress

```kotlin
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task

object : Task.Backgroundable(
    project,
    "Generating Listing...",
    true // cancellable
) {
    override fun run(indicator: ProgressIndicator) {
        indicator.text = "Loading configuration..."
        indicator.fraction = 0.1
        
        val config = loadConfig()
        
        indicator.text = "Processing files..."
        indicator.fraction = 0.5
        
        val files = processFiles(config)
        
        indicator.text = "Rendering output..."
        indicator.fraction = 0.9
        
        renderOutput(files)
    }
}.queue()
```

### Indeterminate Progress

```kotlin
object : Task.Backgroundable(project, "Processing...", true) {
    override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = true // Infinite spinner
        
        processIndefinite()
    }
}.queue()
```

### Modal Progress

```kotlin
import com.intellij.openapi.progress.ProgressManager

ProgressManager.getInstance().runProcessWithProgressSynchronously(
    {
        val indicator = ProgressManager.getInstance().progressIndicator
        indicator.text = "Loading..."
        
        // Long operation
        loadData()
    },
    "Please Wait",
    true, // cancellable
    project
)
```

## Notifications

### Simple Notification

```kotlin
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType

NotificationGroupManager.getInstance()
    .getNotificationGroup("My Notification Group")
    .createNotification(
        "Operation completed successfully",
        NotificationType.INFORMATION
    )
    .notify(project)
```

### Notification с actions

```kotlin
import com.intellij.notification.*

val notification = NotificationGroupManager.getInstance()
    .getNotificationGroup("My Group")
    .createNotification(
        "Files processed",
        "10 files were processed successfully",
        NotificationType.INFORMATION
    )

// Добавить action
notification.addAction(
    NotificationAction.createSimple("View Results") {
        // Show results
    }
)

notification.addAction(
    NotificationAction.createSimple("Open Settings") {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, "My Settings")
    }
)

notification.notify(project)
```

### Notification Types

- `INFORMATION` — информационное (синее)
- `WARNING` — предупреждение (жёлтое)
- `ERROR` — ошибка (красное)

### Регистрация Notification Group

В `plugin.xml`:

```xml
<extensions defaultExtensionNs="com.intellij">
    <notificationGroup 
        id="LG Notifications"
        displayType="BALLOON"
        key="notification.group.name"
        bundle="messages.LgBundle"/>
</extensions>
```

## Dialogs

### DialogWrapper — базовый класс

```kotlin
import com.intellij.openapi.ui.DialogWrapper
import javax.swing.JComponent

class MyDialog(
    private val project: Project?
) : DialogWrapper(project) {
    
    private val messageField = JBTextField()
    
    init {
        title = "My Dialog"
        init() // Обязательно!
    }
    
    override fun createCenterPanel(): JComponent {
        return panel {
            row("Message:") {
                cell(messageField)
                    .align(AlignX.FILL)
            }
        }
    }
    
    override fun doOKAction() {
        val message = messageField.text
        if (message.isBlank()) {
            setErrorText("Message cannot be empty")
            return
        }
        
        // Process and close
        super.doOKAction()
    }
    
    fun getMessage(): String = messageField.text
}

// Использование
val dialog = MyDialog(project)
if (dialog.showAndGet()) {
    val message = dialog.getMessage()
    println("User entered: $message")
}
```

### Messages Dialogs

```kotlin
import com.intellij.openapi.ui.Messages

// Information
Messages.showInfoMessage(
    project,
    "Operation completed",
    "Success"
)

// Warning
Messages.showWarningDialog(
    project,
    "Are you sure?",
    "Confirmation"
)

// Error
Messages.showErrorDialog(
    project,
    "Failed to process file",
    "Error"
)

// Yes/No dialog
val result = Messages.showYesNoDialog(
    project,
    "Delete file?",
    "Confirm",
    Messages.getQuestionIcon()
)

if (result == Messages.YES) {
    deleteFile()
}

// Input dialog
val input = Messages.showInputDialog(
    project,
    "Enter name:",
    "Input",
    Messages.getQuestionIcon()
)

if (input != null) {
    processInput(input)
}
```

## Hints (всплывающие подсказки)

### Editor Hints

```kotlin
import com.intellij.codeInsight.hint.HintManager

// Error hint
HintManager.getInstance().showErrorHint(
    editor,
    "Cannot perform action at current position"
)

// Information hint
HintManager.getInstance().showInformationHint(
    editor,
    "Code generated successfully"
)
```

### Balloon Hints

```kotlin
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint

val balloon = JBPopupFactory.getInstance()
    .createHtmlTextBalloonBuilder(
        "This is a <b>balloon</b> hint",
        MessageType.INFO,
        null
    )
    .setFadeoutTime(3000)
    .createBalloon()

balloon.show(
    RelativePoint.getNorthEastOf(component),
    Balloon.Position.above
)
```

## Popups

### List Popup

```kotlin
import com.intellij.openapi.ui.popup.JBPopupFactory

val items = listOf("Option 1", "Option 2", "Option 3")

JBPopupFactory.getInstance()
    .createPopupChooserBuilder(items)
    .setTitle("Select Option")
    .setItemChosenCallback { selected ->
        println("Selected: $selected")
    }
    .createPopup()
    .showInBestPositionFor(editor)
```

### Action Popup

```kotlin
val actionGroup = DefaultActionGroup().apply {
    add(Action1())
    add(Action2())
    add(Action3())
}

val popup = JBPopupFactory.getInstance()
    .createActionGroupPopup(
        "Actions",
        actionGroup,
        dataContext,
        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
        true
    )

popup.showInBestPositionFor(editor)
```

## Separators и Spacers

### TitledSeparator

```kotlin
import com.intellij.ui.TitledSeparator

val separator = TitledSeparator("Section Title")
panel.add(separator)
```

### Vertical/Horizontal Spacers

```kotlin
import com.intellij.ui.components.panels.VerticalLayout

val panel = JPanel(VerticalLayout(5)).apply { // 5px gap
    add(component1)
    add(Box.createVerticalStrut(10)) // 10px spacer
    add(component2)
}
```

## Fonts

### Editor Font

```kotlin
import com.intellij.openapi.editor.colors.EditorColorsManager

val scheme = EditorColorsManager.getInstance().globalScheme
val editorFont = scheme.getFont(EditorFontType.PLAIN)
val fontSize = scheme.editorFontSize

component.font = editorFont
```

### UI Font

```kotlin
import com.intellij.util.ui.JBUI

val uiFont = JBUI.Fonts.label()
val boldFont = JBUI.Fonts.label().deriveFont(Font.BOLD)
val smallFont = JBUI.Fonts.smallFont()
```

## Links (кликабельные ссылки)

### ActionLink

```kotlin
import com.intellij.ui.components.ActionLink

val link = ActionLink("Click here") {
    // Handle click
    openUrl("https://example.com")
}
```

### HyperlinkLabel

```kotlin
import com.intellij.ui.HyperlinkLabel

val link = HyperlinkLabel("Open documentation").apply {
    addHyperlinkListener {
        BrowserUtil.browse("https://example.com/docs")
    }
}
```

## Status Bar

### Добавить widget в Status Bar

```kotlin
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

class MyStatusBarWidgetFactory : StatusBarWidgetFactory {
    
    override fun getId() = "MyStatusBarWidget"
    
    override fun getDisplayName() = "My Widget"
    
    override fun isAvailable(project: Project) = true
    
    override fun createWidget(project: Project): StatusBarWidget {
        return MyStatusBarWidget(project)
    }
}

class MyStatusBarWidget(private val project: Project) : StatusBarWidget {
    
    override fun ID() = "MyStatusBarWidget"
    
    override fun getPresentation() = object : StatusBarWidget.TextPresentation {
        override fun getText() = "Status: OK"
        
        override fun getTooltipText() = "Click to configure"
        
        override fun getClickConsumer() = Consumer<MouseEvent> {
            // Handle click
        }
    }
}
```

Регистрация:
```xml
<extensions defaultExtensionNs="com.intellij">
    <statusBarWidgetFactory 
        implementation="com.example.MyStatusBarWidgetFactory"/>
</extensions>
```

## Threading и UI Updates

### Обновление UI из background thread

```kotlin
import com.intellij.openapi.application.ApplicationManager

// Background thread
Thread {
    val data = loadDataFromNetwork() // Долго
    
    // Switch to EDT для UI update
    ApplicationManager.getApplication().invokeLater {
        updateUI(data)
    }
}.start()

// Или с Kotlin Coroutines (2024.1+)
scope.launch(Dispatchers.IO) {
    val data = loadDataFromNetwork()
    
    withContext(Dispatchers.EDT) {
        updateUI(data)
    }
}
```

### Read action для доступа к PSI/VFS

```kotlin
// Background thread
scope.launch(Dispatchers.IO) {
    val files = readAction {
        // Read PSI/VFS
        PsiManager.getInstance(project)
            .findDirectory(virtualFile)
            ?.files
            ?.toList()
    }
    
    withContext(Dispatchers.EDT) {
        displayFiles(files)
    }
}
```

## UI Testing

### UI Test с Robot

```kotlin
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.CommonContainerFixture

class MyUiTest {
    
    @Test
    fun testButtonClick() {
        val robot = RemoteRobot("http://127.0.0.1:8082")
        
        robot.find<CommonContainerFixture>(
            byXpath("//div[@class='MyDialog']")
        ).apply {
            button("OK").click()
        }
    }
}
```

## Best Practices

### 1. Используйте Platform компоненты

```kotlin
// ❌ Swing компоненты
val textField = JTextField()

// ✅ Platform компоненты (лучше для IDE)
val textField = JBTextField()
```

### 2. DPI-aware размеры

```kotlin
// ❌ Hardcoded размеры
component.preferredSize = Dimension(200, 100)

// ✅ DPI-aware (работает на Retina)
component.preferredSize = JBUI.size(200, 100)
```

### 3. Используйте named colors

```kotlin
// ❌ Hardcoded цвета
component.background = Color(240, 240, 240)

// ✅ Theme-aware цвета
component.background = JBColor.namedColor(
    "Panel.background",
    JBColor.PanelBackground
)
```

### 4. EDT для UI операций

```kotlin
// ❌ UI операция в background thread
Thread {
    label.text = "New text" // CRASH!
}.start()

// ✅ EDT для UI
Thread {
    val data = loadData()
    
    ApplicationManager.getApplication().invokeLater {
        label.text = data // Безопасно
    }
}.start()
```

### 5. Disposable для cleanup

```kotlin
class MyPanel(
    private val project: Project,
    private val disposable: Disposable
) : JPanel() {
    
    private val timer = Timer(1000) {
        updateData()
    }
    
    init {
        Disposer.register(disposable, Disposable {
            timer.stop()
        })
        
        timer.start()
    }
}
```

## Responsive UI

### EmptyText для пустых компонентов

```kotlin
import com.intellij.ui.components.JBList

val list = JBList<String>().apply {
    emptyText.text = "No items to display"
}
```

### Loading state

```kotlin
val loadingPanel = JPanel(BorderLayout()).apply {
    val loadingLabel = JLabel("Loading...", 
        AllIcons.Process.Step_1, 
        SwingConstants.CENTER
    )
    add(loadingLabel, BorderLayout.CENTER)
}

// Показать loading
panel.removeAll()
panel.add(loadingPanel)
panel.revalidate()
panel.repaint()

// Заменить на контент
scope.launch {
    val data = loadData()
    withContext(Dispatchers.EDT) {
        panel.removeAll()
        panel.add(createContentPanel(data))
        panel.revalidate()
        panel.repaint()
    }
}
```

## UI DSL Preview

Для сложных форм рекомендуется **Kotlin UI DSL** (подробнее в следующей главе):

```kotlin
import com.intellij.ui.dsl.builder.panel

val myPanel = panel {
    row("Name:") {
        textField()
            .bindText(model::name)
    }
    row("Email:") {
        textField()
            .bindText(model::email)
    }
    row {
        checkBox("Subscribe to newsletter")
            .bindSelected(model::subscribe)
    }
}
```

## Дополнительные ресурсы

- [UI Guidelines](https://jetbrains.design/intellij/) — официальный design guide
- [AllIcons справочник](https://github.com/JetBrains/intellij-community/blob/master/platform/util/ui/src/com/intellij/icons/AllIcons.java)
- [Platform UI Components](https://github.com/JetBrains/intellij-community/tree/master/platform/platform-api/src/com/intellij/ui/components)
