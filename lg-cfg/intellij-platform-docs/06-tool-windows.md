# Tool Windows в IntelliJ Platform

## Что такое Tool Window

**Tool Window** — дочернее окно IDE, отображающее дополнительную информацию и инструменты.

Примеры стандартных Tool Windows:
- **Project** (дерево файлов проекта)
- **Structure** (структура файла)
- **Terminal** (встроенный терминал)
- **Git** (VCS операции)
- **Run** (вывод запущенных процессов)
- **Debug** (отладчик)

### Особенности Tool Windows

- Располагаются по краям главного окна IDE (left, right, bottom)
- Имеют **tool window button** на полосе
- Могут содержать **несколько вкладок** (tabs/contents)
- Поддерживают **toolbar** с actions
- Могут быть **floating** (отдельное окно)
- Поддерживают **auto-hide** и **pinned** режимы

### Группы Tool Windows

Каждая сторона (left, right, bottom) имеет **две группы**:
- **Primary group** (основная)
- **Secondary group** (вторичная)

Только **одно** tool window из каждой группы может быть активно одновременно.

## Два способа создания Tool Window

### 1. Declarative Setup (рекомендуется)

Tool window **всегда видим**, пользователь может активировать в любое время.

**Регистрация в plugin.xml:**

```xml
<extensions defaultExtensionNs="com.intellij">
    <toolWindow 
        id="Listing Generator"
        anchor="right"
        secondary="false"
        icon="icons.LgIcons.ToolWindow"
        factoryClass="com.example.lg.ui.LgToolWindowFactory"/>
</extensions>
```

**Атрибуты:**
- `id` (required) — уникальный ID, отображается как заголовок
- `factoryClass` (required) — фабрика для создания содержимого
- `anchor` — позиция: `left`, `right`, `bottom` (default: `left`)
- `secondary` — группа: `false` (primary), `true` (secondary)
- `icon` — иконка на tool window button

**Factory класс:**

```kotlin
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class LgToolWindowFactory : ToolWindowFactory {
    
    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow
    ) {
        val contentPanel = createMainPanel(project, toolWindow)
        
        val content = ContentFactory.getInstance()
            .createContent(contentPanel, "", false)
        
        toolWindow.contentManager.addContent(content)
    }
    
    private fun createMainPanel(
        project: Project,
        toolWindow: ToolWindow
    ): JComponent {
        return LgToolWindowPanel(project, toolWindow)
    }
}
```

### 2. Programmatic Setup

Tool window создаётся **динамически** при необходимости.

```kotlin
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.RegisterToolWindowTask

fun showResultsToolWindow(project: Project) {
    val toolWindowManager = ToolWindowManager.getInstance(project)
    
    // Проверить существование
    var toolWindow = toolWindowManager.getToolWindow("Results")
    
    if (toolWindow == null) {
        // Создать новое tool window
        toolWindow = toolWindowManager.registerToolWindow(
            RegisterToolWindowTask(
                id = "Results",
                anchor = ToolWindowAnchor.BOTTOM,
                canCloseContent = true
            )
        )
        
        // Добавить контент
        val content = ContentFactory.getInstance()
            .createContent(createResultsPanel(), "", false)
        toolWindow.contentManager.addContent(content)
    }
    
    // Показать и активировать
    toolWindow.show()
}
```

## Conditional Display (условная видимость)

Tool window показывается **только для определённых проектов**.

### Kotlin (2023.3+)

```kotlin
class LgToolWindowFactory : ToolWindowFactory {
    
    // Suspending function
    override suspend fun isApplicableAsync(project: Project): Boolean {
        return withContext(Dispatchers.IO) {
            // Проверить наличие lg-cfg/ в проекте
            val basePath = project.basePath ?: return@withContext false
            val lgCfgDir = Path(basePath, "lg-cfg")
            Files.exists(lgCfgDir)
        }
    }
    
    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow
    ) {
        // Create content
    }
}
```

### Java / Earlier versions

```kotlin
class LgToolWindowFactory : ToolWindowFactory {
    
    override fun isApplicable(project: Project): Boolean {
        // Синхронная проверка (должна быть быстрой!)
        val basePath = project.basePath ?: return false
        return File(basePath, "lg-cfg").exists()
    }
    
    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow
    ) {
        // Create content
    }
}
```

**Важно:** условие проверяется **один раз** при загрузке проекта.

Для динамического show/hide используйте [Programmatic Setup](#2-programmatic-setup).

## Contents (Tabs/Вкладки)

Tool Window может содержать **несколько вкладок** (tabs).

### Создание вкладок

```kotlin
class MyToolWindowFactory : ToolWindowFactory {
    
    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow
    ) {
        val contentManager = toolWindow.contentManager
        
        // Вкладка 1: Control Panel
        val controlPanel = createControlPanel(project)
        val controlContent = ContentFactory.getInstance()
            .createContent(controlPanel, "Control", false)
        controlContent.isCloseable = false // Нельзя закрыть
        contentManager.addContent(controlContent)
        
        // Вкладка 2: Results
        val resultsPanel = createResultsPanel()
        val resultsContent = ContentFactory.getInstance()
            .createContent(resultsPanel, "Results", false)
        resultsContent.isCloseable = true // Можно закрыть
        contentManager.addContent(resultsContent)
        
        // Выбрать первую вкладку
        contentManager.setSelectedContent(controlContent)
    }
}
```

### Управление вкладками

```kotlin
val contentManager = toolWindow.contentManager

// Добавить вкладку
val content = ContentFactory.getInstance()
    .createContent(panel, "Tab Title", true)
contentManager.addContent(content)

// Удалить вкладку
contentManager.removeContent(content, true) // true = dispose

// Выбрать вкладку
contentManager.setSelectedContent(content)

// Получить текущую вкладку
val currentContent = contentManager.selectedContent

// Закрыть все вкладки
contentManager.removeAllContents(true)
```

### Disposer для вкладок

```kotlin
val content = ContentFactory.getInstance()
    .createContent(panel, "Tab", false)

// Зарегистрировать disposer
content.setDisposer(Disposable {
    // Cleanup при закрытии вкладки
    panel.cleanup()
})

contentManager.addContent(content)
```

### Closeable вкладки

```kotlin
// Глобально разрешить закрытие вкладок
toolWindow.contentManager.canCloseContents = true

// Или в plugin.xml
<toolWindow 
    id="My Tool"
    canCloseContents="true"
    factoryClass="..."/>

// Для конкретной вкладки
content.isCloseable = true
```

## SimpleToolWindowPanel

**Рекомендуемая** базовая панель для Tool Window содержимого.

```kotlin
import com.intellij.openapi.ui.SimpleToolWindowPanel

class LgToolWindowPanel(
    private val project: Project,
    private val toolWindow: ToolWindow
) : SimpleToolWindowPanel(
    true,  // vertical = true (toolbar сверху)
    true   // borderless = true
) {
    
    init {
        // Toolbar
        toolbar = createToolbar()
        
        // Основной контент
        setContent(createMainContent())
    }
    
    private fun createToolbar(): JComponent {
        val actionGroup = DefaultActionGroup().apply {
            add(RefreshAction())
            add(SettingsAction())
            addSeparator()
            add(HelpAction())
        }
        
        val actionToolbar = ActionManager.getInstance()
            .createActionToolbar(
                "LgToolbar",
                actionGroup,
                true // horizontal
            )
        
        actionToolbar.targetComponent = this
        
        return actionToolbar.component
    }
    
    private fun createMainContent(): JComponent {
        return JPanel(BorderLayout()).apply {
            add(JLabel("Main content here"), BorderLayout.CENTER)
        }
    }
}
```

**Преимущества SimpleToolWindowPanel:**
- Встроенная поддержка toolbar
- Правильные отступы и border
- Поддержка вертикального/горизонтального layout

## Tool Window с Tree View

Пример для Included Files панели (как в VS Code Extension):

```kotlin
import com.intellij.ui.treeStructure.Tree
import javax.swing.tree.*

class IncludedFilesPanel(
    private val project: Project
) : SimpleToolWindowPanel(true, true) {
    
    private val treeModel = DefaultTreeModel(DefaultMutableTreeNode("Root"))
    private val tree = Tree(treeModel).apply {
        isRootVisible = false
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        
        // Double-click открывает файл
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val node = lastSelectedPathComponent as? DefaultMutableTreeNode
                    val file = node?.userObject as? VirtualFile
                    if (file != null) {
                        FileEditorManager.getInstance(project)
                            .openFile(file, true)
                    }
                }
            }
        })
    }
    
    init {
        setContent(JBScrollPane(tree))
        toolbar = createToolbar()
    }
    
    fun setPaths(paths: List<String>) {
        val root = DefaultMutableTreeNode("Root")
        
        for (path in paths) {
            val file = LocalFileSystem.getInstance()
                .findFileByPath("${project.basePath}/$path")
            
            if (file != null) {
                root.add(DefaultMutableTreeNode(file))
            }
        }
        
        ApplicationManager.getApplication().invokeLater {
            treeModel.setRoot(root)
            tree.expandRow(0)
        }
    }
    
    private fun createToolbar(): JComponent {
        val group = DefaultActionGroup().apply {
            add(RefreshAction())
            add(ToggleViewModeAction()) // Flat vs Tree
        }
        
        val toolbar = ActionManager.getInstance()
            .createActionToolbar("IncludedFiles", group, true)
        
        toolbar.targetComponent = this
        return toolbar.component
    }
}
```

## Tool Window API

### Получение Tool Window

```kotlin
import com.intellij.openapi.wm.ToolWindowManager

val toolWindow = ToolWindowManager.getInstance(project)
    .getToolWindow("Listing Generator")

if (toolWindow != null) {
    toolWindow.show()
}
```

### Управление видимостью

```kotlin
// Показать
toolWindow.show()

// Показать и активировать (focus)
toolWindow.activate(null)

// Скрыть
toolWindow.hide()

// Проверка видимости
if (toolWindow.isVisible) {
    // Visible
}

// Проверка активности
if (toolWindow.isActive) {
    // Active (has focus)
}
```

### Свойства Tool Window

```kotlin
// Изменить позицию
toolWindow.setAnchor(ToolWindowAnchor.RIGHT, null)

// Изменить тип (docked, floating, etc.)
toolWindow.setType(ToolWindowType.DOCKED, null)

// Установить auto-hide
toolWindow.isAutoHide = true

// Скрыть при пустом содержимом
toolWindow.setToHideOnEmptyContent(true)

// Установить заголовок на полосе
toolWindow.stripeTitle = "LG"

// Установить доступность
toolWindow.isAvailable = true
```

## Dumb Awareness

Tool Windows доступны во время индексации **только если** factory помечен как `DumbAware`.

```kotlin
import com.intellij.openapi.project.DumbAware

class LgToolWindowFactory : ToolWindowFactory, DumbAware {
    
    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow
    ) {
        // Tool window доступен даже при индексации
    }
}
```

**Без DumbAware:**
- Tool window **disabled** во время индексации
- Кнопка серая и неактивная

**С DumbAware:**
- Tool window **доступен** во время индексации
- Но нельзя использовать indexes (PSI Stubs, Find Usages и т.д.)

## Notifications в Tool Window

### Balloon уведомления

```kotlin
import com.intellij.openapi.wm.ToolWindowManager

ToolWindowManager.getInstance(project)
    .notifyByBalloon(
        "Listing Generator",           // Tool window ID
        MessageType.INFO,                // Type
        "Operation completed",           // Message
        AllIcons.General.Information,    // Icon
        null                             // Hyperlink listener
    )
```

## Events (события Tool Window)

### ToolWindowManagerListener

```xml
<projectListeners>
    <listener 
        topic="com.intellij.openapi.wm.ex.ToolWindowManagerListener"
        class="com.example.MyToolWindowListener"/>
</projectListeners>
```

```kotlin
import com.intellij.openapi.wm.ex.ToolWindowManagerListener

class MyToolWindowListener : ToolWindowManagerListener {
    
    override fun toolWindowShown(toolWindow: ToolWindow) {
        if (toolWindow.id == "Listing Generator") {
            // Tool window показан
            println("LG Tool Window shown")
        }
    }
    
    override fun stateChanged(
        toolWindowManager: ToolWindowManager
    ) {
        // Tool window state изменился
    }
}
```

## Полный пример Tool Window

```kotlin
// ========== Factory ==========
class LgToolWindowFactory : ToolWindowFactory, DumbAware {
    
    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow
    ) {
        val service = project.service<LgToolWindowService>()
        val panel = service.createContent(toolWindow)
        
        val content = ContentFactory.getInstance()
            .createContent(panel, "", false)
        
        content.preferredFocusableComponent = panel.getPreferredFocusedComponent()
        
        toolWindow.contentManager.addContent(content)
    }
    
    override fun init(toolWindow: ToolWindow) {
        // Инициализация ДО создания контента
        toolWindow.setToHideOnEmptyContent(false)
        toolWindow.isAvailable = true
    }
}

// ========== Service ==========
@Service(Service.Level.PROJECT)
class LgToolWindowService(private val project: Project) {
    
    fun createContent(toolWindow: ToolWindow): LgToolWindowPanel {
        return LgToolWindowPanel(project, toolWindow)
    }
}

// ========== Main Panel ==========
class LgToolWindowPanel(
    private val project: Project,
    private val toolWindow: ToolWindow
) : SimpleToolWindowPanel(true, true), Disposable {
    
    private val contextComboBox = ComboBox<String>()
    private val sectionComboBox = ComboBox<String>()
    private val generateButton = JButton("Generate")
    private val statsButton = JButton("Show Stats")
    
    init {
        setContent(createMainContent())
        toolbar = createToolbar()
        
        loadData()
    }
    
    private fun createMainContent(): JComponent {
        return panel {
            // Kotlin UI DSL
            group("AI Contexts") {
                row("Template:") {
                    cell(contextComboBox)
                        .align(AlignX.FILL)
                }
                row {
                    cell(generateButton)
                    cell(statsButton)
                }
            }
            
            group("Inspect") {
                row("Section:") {
                    cell(sectionComboBox)
                        .align(AlignX.FILL)
                }
            }
        }
    }
    
    private fun createToolbar(): JComponent {
        val group = DefaultActionGroup().apply {
            add(RefreshAction(this@LgToolWindowPanel))
            add(SettingsAction())
        }
        
        val toolbar = ActionManager.getInstance()
            .createActionToolbar("LgToolbar", group, true)
        
        toolbar.targetComponent = this
        return toolbar.component
    }
    
    private fun loadData() {
        val service = project.service<LgCatalogService>()
        
        service.loadContextsAsync { contexts ->
            ApplicationManager.getApplication().invokeLater {
                updateContexts(contexts)
            }
        }
    }
    
    private fun updateContexts(contexts: List<String>) {
        contextComboBox.removeAllItems()
        contexts.forEach { contextComboBox.addItem(it) }
    }
    
    fun getPreferredFocusedComponent(): JComponent {
        return contextComboBox
    }
    
    fun refresh() {
        loadData()
    }
    
    override fun dispose() {
        // Cleanup
    }
}

// ========== Actions ==========
class RefreshAction(
    private val panel: LgToolWindowPanel
) : AnAction("Refresh", "Reload data", AllIcons.Actions.Refresh) {
    
    override fun actionPerformed(e: AnActionEvent) {
        panel.refresh()
    }
    
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
```

## Tool Window с несколькими вкладками

```kotlin
class MultiTabToolWindowFactory : ToolWindowFactory {
    
    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow
    ) {
        val contentManager = toolWindow.contentManager
        
        // Вкладка: Control Panel
        addTab(
            contentManager,
            ControlPanelView(project),
            "Control",
            false // not closeable
        )
        
        // Вкладка: Included Files (dynamic)
        val includedView = IncludedFilesView(project)
        addTab(
            contentManager,
            includedView,
            "Included Files",
            false
        )
        
        // Сохранить ссылку на view в service
        project.service<LgToolWindowService>()
            .setIncludedFilesView(includedView)
    }
    
    private fun addTab(
        manager: ContentManager,
        component: JComponent,
        title: String,
        closeable: Boolean
    ) {
        val content = ContentFactory.getInstance()
            .createContent(component, title, false)
        content.isCloseable = closeable
        manager.addContent(content)
    }
}
```

## Взаимодействие между вкладками

### Через Service

```kotlin
@Service(Service.Level.PROJECT)
class LgToolWindowService(private val project: Project) {
    
    private var includedFilesView: IncludedFilesView? = null
    
    fun setIncludedFilesView(view: IncludedFilesView) {
        includedFilesView = view
    }
    
    fun updateIncludedFiles(files: List<String>) {
        includedFilesView?.updateFiles(files)
    }
}

// В Control Panel action
class ShowIncludedAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = project.service<LgToolWindowService>()
        
        // Load files
        val files = loadIncludedFiles()
        
        // Update другую вкладку
        service.updateIncludedFiles(files)
        
        // Switch to tab
        switchToIncludedFilesTab(project)
    }
}

fun switchToIncludedFilesTab(project: Project) {
    val toolWindow = ToolWindowManager.getInstance(project)
        .getToolWindow("Listing Generator") ?: return
    
    val content = toolWindow.contentManager.contents
        .find { it.displayName == "Included Files" }
    
    if (content != null) {
        toolWindow.contentManager.setSelectedContent(content)
        toolWindow.show()
    }
}
```

## Сохранение состояния Tool Window

Tool Window автоматически сохраняет:
- Позицию (anchor)
- Размер
- Видимость
- Active tab

Для custom state используйте Service с PersistentStateComponent:

```kotlin
@Service(Service.Level.PROJECT)
@State(
    name = "LgToolWindowState",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)]
)
class LgToolWindowState : SimplePersistentStateComponent<State>(State()) {
    
    class State : BaseState() {
        var selectedSection by string("all")
        var selectedContext by string("")
        var viewMode by enum(ViewMode.TREE)
    }
    
    enum class ViewMode { FLAT, TREE }
}

// Использование
val state = project.service<LgToolWindowState>()
sectionComboBox.selectedItem = state.state.selectedSection

// Сохранение при изменении
sectionComboBox.addActionListener {
    state.state.selectedSection = sectionComboBox.selectedItem as String
}
```

## Stripe Title (заголовок на полосе)

```kotlin
override fun init(toolWindow: ToolWindow) {
    toolWindow.stripeTitle = "LG" // Короткое название на полосе
}
```

Или локализованный:

```properties
# messages/LgBundle.properties
toolwindow.stripe.Listing_Generator=LG
```

```kotlin
override fun init(toolWindow: ToolWindow) {
    toolWindow.stripeTitle = LgBundle.message("toolwindow.stripe.Listing_Generator")
}
```

## Tool Window Decorations

### Title Actions (справа от заголовка вкладки)

```kotlin
val content = ContentFactory.getInstance()
    .createContent(panel, "Results", false)

// Добавить action в заголовок вкладки
content.setActions(
    DefaultActionGroup(
        CloseAction(),
        RefreshAction()
    ),
    ActionPlaces.TOOLWINDOW_TITLE,
    ActionManager.getInstance().createActionToolbar(
        ActionPlaces.TOOLWINDOW_TITLE,
        group,
        true
    ).component
)
```

### Preferred Focus Component

```kotlin
override fun createToolWindowContent(
    project: Project,
    toolWindow: ToolWindow
) {
    val panel = MyPanel()
    val content = ContentFactory.getInstance()
        .createContent(panel, "", false)
    
    // Компонент для фокуса при активации
    content.preferredFocusableComponent = panel.getSearchField()
    
    toolWindow.contentManager.addContent(content)
}
```

## Asynchronous Content Loading

```kotlin
class LazyToolWindowFactory : ToolWindowFactory {
    
    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow
    ) {
        // Показать loading state
        val loadingPanel = createLoadingPanel()
        val content = ContentFactory.getInstance()
            .createContent(loadingPanel, "", false)
        toolWindow.contentManager.addContent(content)
        
        // Загрузить асинхронно
        project.service<MyService>().loadDataAsync { data ->
            ApplicationManager.getApplication().invokeLater {
                // Заменить loading на реальный контент
                val mainPanel = createMainPanel(project, data)
                val newContent = ContentFactory.getInstance()
                    .createContent(mainPanel, "", false)
                
                toolWindow.contentManager.removeAllContents(true)
                toolWindow.contentManager.addContent(newContent)
            }
        }
    }
    
    private fun createLoadingPanel(): JComponent {
        return JPanel(BorderLayout()).apply {
            add(
                JLabel(
                    "Loading...",
                    AllIcons.Process.Step_1,
                    SwingConstants.CENTER
                ),
                BorderLayout.CENTER
            )
        }
    }
}
```

## Tool Window Context Menu

```kotlin
val tree = Tree(model).apply {
    // Добавить контекстное меню
    addMouseListener(object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent) {
            if (e.isPopupTrigger) {
                showContextMenu(e)
            }
        }
        
        override fun mouseReleased(e: MouseEvent) {
            if (e.isPopupTrigger) {
                showContextMenu(e)
            }
        }
        
        private fun showContextMenu(e: MouseEvent) {
            val path = getPathForLocation(e.x, e.y)
            selectionPath = path
            
            val group = DefaultActionGroup().apply {
                add(OpenFileAction())
                add(CopyPathAction())
                addSeparator()
                add(RefreshAction())
            }
            
            val popup = ActionManager.getInstance()
                .createActionPopupMenu("TreeContext", group)
            
            popup.component.show(e.component, e.x, e.y)
        }
    })
}
```

## Programmatic Tool Window Management

### Динамическое создание

```kotlin
fun showResultsWindow(project: Project, results: Results) {
    val manager = ToolWindowManager.getInstance(project)
    
    var toolWindow = manager.getToolWindow("LG Results")
    
    if (toolWindow == null) {
        // Создать новое tool window
        toolWindow = manager.registerToolWindow(
            RegisterToolWindowTask(
                id = "LG Results",
                anchor = ToolWindowAnchor.BOTTOM,
                canCloseContent = true,
                canWorkInDumbMode = true
            )
        )
    }
    
    // Добавить вкладку с результатами
    val panel = createResultsPanel(results)
    val content = ContentFactory.getInstance()
        .createContent(panel, "Results ${results.id}", true)
    
    toolWindow.contentManager.addContent(content)
    toolWindow.contentManager.setSelectedContent(content)
    toolWindow.show()
}

// Закрыть tool window
fun closeResultsWindow(project: Project) {
    val manager = ToolWindowManager.getInstance(project)
    manager.getToolWindow("LG Results")?.remove()
}
```

### ToolWindowManager.invokeLater

Для EDT операций **всегда** используйте `ToolWindowManager.invokeLater`:

```kotlin
// ❌ НЕПРАВИЛЬНО
ApplicationManager.getApplication().invokeLater {
    toolWindow.show()
}

// ✅ ПРАВИЛЬНО
ToolWindowManager.getInstance(project).invokeLater {
    toolWindow.show()
}
```

## Best Practices

### 1. Используйте SimpleToolWindowPanel

```kotlin
// ✅ Recommended
class MyPanel : SimpleToolWindowPanel(true, true) {
    init {
        toolbar = createToolbar()
        setContent(createContent())
    }
}

// ❌ Не рекомендуется (больше boilerplate)
class MyPanel : JPanel(BorderLayout()) {
    init {
        add(createToolbar(), BorderLayout.NORTH)
        add(createContent(), BorderLayout.CENTER)
    }
}
```

### 2. Lazy loading контента

```kotlin
// ✅ Контент создаётся только при открытии
override fun createToolWindowContent(
    project: Project,
    toolWindow: ToolWindow
) {
    // Минимальная инициализация
    val panel = createPanel()
    
    // Тяжёлая загрузка асинхронно
    loadDataAsync()
}

// ❌ Тяжёлая работа в createToolWindowContent
override fun createToolWindowContent(...) {
    val data = loadDataFromDisk() // Блокирует startup!
}
```

### 3. Disposable для cleanup

```kotlin
class MyToolWindowPanel : SimpleToolWindowPanel(...), Disposable {
    
    private val updateTimer = Timer(5000) {
        updateData()
    }
    
    init {
        updateTimer.start()
    }
    
    override fun dispose() {
        updateTimer.stop()
    }
}

// Регистрация
content.setDisposer(panel) // panel implements Disposable
```

### 4. Target component для toolbar

```kotlin
val toolbar = ActionManager.getInstance()
    .createActionToolbar("MyToolbar", actionGroup, true)

toolbar.targetComponent = panel // ← ОБЯЗАТЕЛЬНО!
```

### 5. Dumb-aware когда возможно

```kotlin
// ✅ Если не нужны indexes
class MyToolWindowFactory : ToolWindowFactory, DumbAware

// ❌ Без DumbAware (disabled при индексации)
class MyToolWindowFactory : ToolWindowFactory
```

## Integration с Control Panel (как в VS Code Extension)

Пример реализации Control Panel похожего на VS Code Extension:

```kotlin
class LgControlPanel(
    private val project: Project
) : SimpleToolWindowPanel(true, true), Disposable {
    
    // UI Components
    private val templateComboBox = ComboBox<String>()
    private val sectionComboBox = ComboBox<String>()
    private val tokenizerComboBox = ComboBox<String>()
    private val encoderTextField = JBTextField()
    private val contextLimitField = JBTextField()
    
    // State
    private val state = project.service<LgControlPanelState>()
    
    init {
        setContent(createContent())
        setupListeners()
        loadInitialData()
    }
    
    private fun createContent(): JComponent {
        return panel {
            group("AI Contexts") {
                row("Template:") {
                    cell(templateComboBox)
                        .align(AlignX.FILL)
                }
                row {
                    button("Send to AI") { sendToAI() }
                    button("Generate") { generate() }
                    button("Show Stats") { showStats() }
                }
            }
            
            group("Tokenization") {
                row("Library:") {
                    cell(tokenizerComboBox)
                        .align(AlignX.FILL)
                }
                row("Encoder:") {
                    cell(encoderTextField)
                        .align(AlignX.FILL)
                }
                row("Context Limit:") {
                    cell(contextLimitField)
                        .columns(10)
                }
            }
        }
    }
    
    private fun setupListeners() {
        templateComboBox.addActionListener {
            state.state.selectedTemplate = templateComboBox.selectedItem as? String ?: ""
        }
        
        tokenizerComboBox.addActionListener {
            val lib = tokenizerComboBox.selectedItem as? String ?: return@addActionListener
            loadEncoders(lib)
        }
    }
    
    private fun loadInitialData() {
        val service = project.service<LgCatalogService>()
        
        service.loadContextsAsync { contexts ->
            updateTemplates(contexts)
        }
        
        service.loadSectionsAsync { sections ->
            updateSections(sections)
        }
    }
    
    private fun sendToAI() {
        val template = templateComboBox.selectedItem as? String ?: return
        
        project.service<LgGeneratorService>().generateContextAsync(template) { text ->
            // Отправить в AI provider
            copyToClipboard(text)
            showNotification("Context copied to clipboard")
        }
    }
    
    override fun dispose() {
        // Cleanup
    }
}
```

## Testing Tool Windows

### UI Test

```kotlin
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ToolWindowTest : BasePlatformTestCase() {
    
    fun testToolWindowExists() {
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow("Listing Generator")
        
        assertNotNull(toolWindow)
        assertEquals(ToolWindowAnchor.RIGHT, toolWindow.anchor)
    }
    
    fun testToolWindowContent() {
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow("Listing Generator")!!
        
        val content = toolWindow.contentManager.contents.firstOrNull()
        assertNotNull(content)
        
        val panel = content?.component
        assertInstanceOf(panel, LgToolWindowPanel::class.java)
    }
}
```
