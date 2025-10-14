# Примеры из реальных плагинов

## Обзор

Этот документ содержит извлечённые паттерны и примеры из официальных плагинов JetBrains.

## Serial Monitor Plugin

**Репозиторий:** [intellij-plugins/serial-monitor](https://github.com/JetBrains/intellij-plugins/tree/master/serial-monitor)

**Функциональность:** Tool Window для мониторинга serial port соединений.

### Структура проекта

```
serial-monitor/
├── src/main/
│   ├── java/com/intellij/plugins/serialmonitor/
│   │   ├── SerialMonitorToolWindowFactory.java  # Tool Window
│   │   ├── SerialProfileService.kt              # Project Service
│   │   ├── service/
│   │   │   ├── SerialPortService.kt
│   │   │   └── SerialPortProviderService.kt
│   │   └── ui/
│   │       ├── SerialMonitor.kt                 # Main panel
│   │       ├── ConnectPanel.kt
│   │       ├── actions/
│   │       │   ├── ConnectDisconnectAction.java
│   │       │   └── EditSettingsAction.kt
│   │       └── console/
│   │           └── JeditermConsoleView.kt
│   └── resources/META-INF/
│       └── plugin.xml
```

### Tool Window Factory

```java
public class SerialMonitorToolWindowFactory 
    implements ToolWindowFactory, DumbAware {
    
    @Override
    public void init(@NotNull ToolWindow toolWindow) {
        toolWindow.setToHideOnEmptyContent(false);
        toolWindow.setStripeTitle(
            SerialMonitorBundle.message("toolwindow.stripe.title")
        );
        toolWindow.setAvailable(true);
    }
    
    @Override
    public void createToolWindowContent(
        @NotNull Project project,
        @NotNull ToolWindow toolWindow
    ) {
        ContentManager manager = toolWindow.getContentManager();
        
        JPanel portPanel = new ConnectPanel(toolWindow);
        
        Content content = manager.getFactory()
            .createContent(
                portPanel,
                SerialMonitorBundle.message("toolwindow.port.tab.title"),
                true
            );
        
        content.setCloseable(false);
        manager.addContent(content);
    }
}
```

### Key Lessons

✅ **DumbAware** — tool window доступен при индексации  
✅ **init()** — настройка свойств до создания контента  
✅ **Localization** — через message bundle  
✅ **Service separation** — UI отдельно от логики

## Tool Window Code Sample

**Репозиторий:** [intellij-sdk-code-samples/tool_window](https://github.com/JetBrains/intellij-sdk-code-samples/tree/master/tool_window)

**Функциональность:** Простой Tool Window с календарём.

### Factory Implementation

```java
final class CalendarToolWindowFactory 
    implements ToolWindowFactory, DumbAware {
    
    @Override
    public void createToolWindowContent(
        @NotNull Project project,
        @NotNull ToolWindow toolWindow
    ) {
        CalendarToolWindowContent content = 
            new CalendarToolWindowContent(toolWindow);
        
        Content contentTab = ContentFactory.getInstance()
            .createContent(content.getContentPanel(), "", false);
        
        toolWindow.getContentManager().addContent(contentTab);
    }
    
    private static class CalendarToolWindowContent {
        private final JPanel contentPanel = new JPanel();
        
        public CalendarToolWindowContent(ToolWindow toolWindow) {
            contentPanel.setLayout(new BorderLayout(0, 20));
            contentPanel.add(createControlsPanel(toolWindow), BorderLayout.CENTER);
        }
        
        private JPanel createControlsPanel(ToolWindow toolWindow) {
            JPanel panel = new JPanel();
            
            JButton refreshButton = new JButton("Refresh");
            refreshButton.addActionListener(e -> updateData());
            panel.add(refreshButton);
            
            JButton hideButton = new JButton("Hide");
            hideButton.addActionListener(e -> toolWindow.hide(null));
            panel.add(hideButton);
            
            return panel;
        }
        
        public JPanel getContentPanel() {
            return contentPanel;
        }
    }
}
```

### Key Lessons

✅ **Inner class** для содержимого  
✅ **BorderLayout** для structure  
✅ **Action listeners** для кнопок  
✅ **toolWindow.hide()** для программного скрытия

## Action Basics Sample

**Репозиторий:** [intellij-sdk-code-samples/action_basics](https://github.com/JetBrains/intellij-sdk-code-samples/tree/master/action_basics)

### PopupDialogAction

```java
public class PopupDialogAction extends AnAction {
    
    @Override
    public void update(@NotNull AnActionEvent e) {
        // Доступно только когда проект открыт
        Project project = e.getProject();
        e.getPresentation().setEnabledAndVisible(project != null);
    }
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getRequiredData(CommonDataKeys.PROJECT);
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        
        String dialogMessage = "Context info:\n";
        
        if (editor != null) {
            PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
            if (psiFile != null) {
                dialogMessage += "File: " + psiFile.getName() + "\n";
                
                // Получить текущий элемент
                PsiElement element = psiFile.findElementAt(
                    editor.getCaretModel().getOffset()
                );
                
                if (element != null) {
                    dialogMessage += "Element: " + element.getText();
                }
            }
        }
        
        Messages.showMessageDialog(
            project,
            dialogMessage,
            "Context Information",
            Messages.getInformationIcon()
        );
    }
    
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
```

### Key Lessons

✅ **update()** проверяет наличие project  
✅ **getRequiredData()** vs **getData()** — fail-fast vs nullable  
✅ **getActionUpdateThread()** — обязателен с 2022.3  
✅ **PsiFile.findElementAt()** — получить элемент по offset

## Settings Tutorial

**Репозиторий:** [intellij-sdk-code-samples/settings](https://github.com/JetBrains/intellij-sdk-code-samples/tree/master/settings)

### Settings Service (Kotlin)

```kotlin
@Service
@State(
    name = "AppSettingsState",
    storages = [Storage("SdkSettingsPlugin.xml")]
)
class AppSettingsState : SimplePersistentStateComponent<State>(State()) {
    
    class State : BaseState() {
        var userId by string("John Smith")
        var ideaStatus by property(false)
    }
    
    companion object {
        fun getInstance(): AppSettingsState = service()
    }
}
```

### Configurable (Kotlin)

```kotlin
class AppSettingsConfigurable : BoundConfigurable("SDK: Application Settings Example") {
    
    private val settings = AppSettingsState.getInstance()
    
    override fun createPanel() = panel {
        row("User ID:") {
            textField()
                .bindText(settings.state::userId)
                .columns(20)
        }
        
        row {
            checkBox("Enable status")
                .bindSelected(settings.state::ideaStatus)
        }
    }
}
```

### Key Lessons

✅ **BoundConfigurable** — упрощает Settings UI  
✅ **SimplePersistentStateComponent** — меньше boilerplate  
✅ **Kotlin UI DSL** — декларативный UI  
✅ **bindText/bindSelected** — автоматический binding

## Max Opened Projects Sample

**Репозиторий:** [intellij-sdk-code-samples/max_opened_projects](https://github.com/JetBrains/intellij-sdk-code-samples/tree/master/max_opened_projects)

### Application Service с Listener

```java
@Service
public final class ProjectCountingService 
    implements ProjectManagerListener {
    
    private static final int MAX_OPEN_PRO_LIMIT = 3;
    private final AtomicInteger projectCount = new AtomicInteger(0);
    
    public static ProjectCountingService getInstance() {
        return ApplicationManager.getApplication()
            .getService(ProjectCountingService.class);
    }
    
    @Override
    public void projectOpened(@NotNull Project project) {
        int count = projectCount.incrementAndGet();
        
        if (count > MAX_OPEN_PRO_LIMIT) {
            ApplicationManager.getApplication().invokeLater(() -> {
                Messages.showInfoMessage(
                    project,
                    "Too many projects opened",
                    "Warning"
                );
            });
        }
    }
    
    @Override
    public void projectClosed(@NotNull Project project) {
        projectCount.decrementAndGet();
    }
}
```

Регистрация:

```xml
<applicationListeners>
    <listener 
        topic="com.intellij.openapi.project.ProjectManagerListener"
        class="org.intellij.sdk.maxOpenProjects.ProjectCountingService"/>
</applicationListeners>
```

### Key Lessons

✅ **@Service** Light Service  
✅ **ProjectManagerListener** — слушать открытие/закрытие проектов  
✅ **invokeLater()** для UI updates  
✅ **AtomicInteger** для thread-safety

## Patterns из серийных плагинов

### Pattern 1: Service Factory

Из **Terraform plugin**:

```kotlin
@Service(Service.Level.PROJECT)
class TerraformConfigurationService(private val project: Project) {
    
    companion object {
        fun getInstance(project: Project): TerraformConfigurationService {
            return project.service()
        }
    }
    
    fun getConfiguration(): TerraformConfiguration {
        // Load configuration
    }
}
```

### Pattern 2: Disposable Service

Из **Serial Monitor plugin**:

```kotlin
@Service(Service.Level.PROJECT)
class SerialPortService(
    private val project: Project
) : Disposable {
    
    private val connections = mutableListOf<SerialConnection>()
    
    fun openPort(name: String): SerialConnection {
        val connection = SerialConnection(name)
        connections.add(connection)
        return connection
    }
    
    override fun dispose() {
        connections.forEach { it.close() }
        connections.clear()
    }
}
```

### Pattern 3: Tool Window с Tab Management

Из **Serial Monitor plugin**:

```kotlin
class ConnectPanel(private val toolWindow: ToolWindow) : JPanel() {
    
    fun createNewTab(portName: String) {
        val console = createConsoleView()
        
        val content = toolWindow.contentManager.factory
            .createContent(console, portName, true)
        
        content.isCloseable = true
        content.setDisposer(Disposable {
            console.dispose()
        })
        
        toolWindow.contentManager.addContent(content)
        toolWindow.contentManager.setSelectedContent(content)
    }
    
    private fun createConsoleView(): JComponent {
        // Create console
    }
}
```

### Pattern 4: Action с DataContext

Из **Action Basics sample**:

```kotlin
class DynamicAction : AnAction() {
    
    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        val hasSelection = editor?.selectionModel?.hasSelection() == true
        
        e.presentation.isEnabledAndVisible = 
            psiFile != null && hasSelection
        
        // Динамический текст
        e.presentation.text = if (hasSelection) {
            "Process Selection"
        } else {
            "Process File"
        }
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getRequiredData(CommonDataKeys.EDITOR)
        val selectedText = editor.selectionModel.selectedText
        
        processText(selectedText)
    }
    
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
```

### Pattern 5: Configuration Dialog

Из **Prettier JS plugin**:

```kotlin
class PrettierConfigurable(private val project: Project) 
    : BoundConfigurable("Prettier") {
    
    private val config = PrettierConfiguration.getInstance(project)
    
    override fun createPanel() = panel {
        
        group("Prettier Package") {
            row("Path:") {
                textFieldWithBrowseButton(
                    "Select Prettier Package",
                    project,
                    FileChooserDescriptorFactory.createSingleFolderDescriptor()
                ).bindText(config::prettierPath)
                    .align(AlignX.FILL)
            }
            
            row {
                checkBox("Run on save")
                    .bindSelected(config::runOnSave)
            }
        }
        
        group("Configuration") {
            row("Config path:") {
                textFieldWithBrowseButton(
                    "Select .prettierrc",
                    project,
                    FileChooserDescriptorFactory.createSingleFileDescriptor()
                ).bindText(config::configPath)
                    .align(AlignX.FILL)
            }
        }
    }
}
```

## Real-World Паттерны

### Pattern: CLI Wrapper Service

Обобщение из нескольких плагинов (ESLint, Prettier, etc.):

```kotlin
@Service(Service.Level.PROJECT)
class ToolCliService(
    private val project: Project,
    private val scope: CoroutineScope
) {
    
    private var cachedVersion: String? = null
    
    suspend fun getVersion(): String {
        if (cachedVersion != null) {
            return cachedVersion!!
        }
        
        val output = execute(listOf("--version"))
        cachedVersion = parseVersion(output)
        return cachedVersion!!
    }
    
    suspend fun execute(args: List<String>): String {
        return withContext(Dispatchers.IO) {
            val executable = resolveExecutable()
                ?: throw ToolNotFoundException()
            
            val commandLine = GeneralCommandLine(executable)
                .withParameters(args)
                .withWorkDirectory(project.basePath)
            
            val handler = CapturingProcessHandler(commandLine)
            val output = handler.runProcess(60_000)
            
            if (output.exitCode != 0) {
                throw ToolExecutionException(output.stderr)
            }
            
            output.stdout
        }
    }
    
    private fun resolveExecutable(): String? {
        // 1. Check settings
        val settings = project.service<ToolSettings>()
        if (settings.executablePath.isNotBlank()) {
            return settings.executablePath
        }
        
        // 2. Check node_modules
        val localPath = "${project.basePath}/node_modules/.bin/tool"
        if (File(localPath).exists()) {
            return localPath
        }
        
        // 3. Check PATH
        return findInPath("tool")
    }
    
    fun invalidateCache() {
        cachedVersion = null
    }
}
```

### Pattern: File Watcher

Из Git и других VCS plugins:

```kotlin
@Service(Service.Level.PROJECT)
class ConfigWatcher(
    private val project: Project,
    private val scope: CoroutineScope
) : Disposable {
    
    private val reloadSubject = MutableSharedFlow<Unit>()
    
    init {
        // VFS listener
        project.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    if (hasConfigChanges(events)) {
                        scope.launch {
                            reloadSubject.emit(Unit)
                        }
                    }
                }
            }
        )
        
        // Debounced reload
        scope.launch {
            reloadSubject
                .debounce(500)
                .collect {
                    reloadConfiguration()
                }
        }
    }
    
    private fun hasConfigChanges(events: List<VFileEvent>): Boolean {
        return events.any { event ->
            val file = event.file ?: return@any false
            file.name == "config.yaml"
        }
    }
    
    private suspend fun reloadConfiguration() {
        // Reload
    }
    
    override fun dispose() { }
}
```

### Pattern: State Machine Service

Из Run/Debug infrastructure:

```kotlin
@Service(Service.Level.PROJECT)
class TaskExecutorService(
    private val project: Project,
    private val scope: CoroutineScope
) {
    
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()
    
    sealed class State {
        object Idle : State()
        data class Running(
            val taskName: String,
            val progress: Int
        ) : State()
        data class Success(val result: String) : State()
        data class Failed(val error: String) : State()
    }
    
    fun execute(task: Task) {
        if (_state.value !is State.Idle) {
            throw IllegalStateException("Task already running")
        }
        
        scope.launch {
            _state.value = State.Running(task.name, 0)
            
            try {
                for (step in 1..10) {
                    processStep(task, step)
                    _state.value = State.Running(task.name, step * 10)
                    delay(100)
                }
                
                val result = finalizeTask(task)
                _state.value = State.Success(result)
                
            } catch (e: Exception) {
                _state.value = State.Failed(e.message ?: "Unknown")
            } finally {
                delay(3000)
                _state.value = State.Idle
            }
        }
    }
}
```

### Pattern: Tree View Provider

Из Project View:

```kotlin
class MyTreeStructure(
    private val project: Project
) : AbstractTreeStructure() {
    
    private val root = MyRootNode(project)
    
    override fun getRootElement(): Any = root
    
    override fun getChildElements(element: Any): Array<Any> {
        return when (element) {
            is MyRootNode -> element.getChildren()
            is MyDirectoryNode -> element.getChildren()
            is MyFileNode -> emptyArray()
            else -> emptyArray()
        }
    }
    
    override fun getParentElement(element: Any): Any? {
        return when (element) {
            is MyNode -> element.parent
            else -> null
        }
    }
    
    override fun commit() { }
    
    override fun hasSomethingToCommit() = false
}

abstract class MyNode(val parent: MyNode?) {
    abstract fun getChildren(): Array<Any>
}

class MyRootNode(private val project: Project) : MyNode(null) {
    override fun getChildren(): Array<Any> {
        val baseDir = project.baseDir ?: return emptyArray()
        return baseDir.children.map { MyFileNode(it, this) }.toTypedArray()
    }
}
```

## Kotlin Specific Patterns

### Extension Functions для DSL

```kotlin
// Из Kotlin UI DSL
fun Panel.createGeneralSettings() {
    row("Name:") {
        textField()
    }
    row("Email:") {
        textField()
    }
}

// Использование
override fun createPanel() = panel {
    group("General") {
        createGeneralSettings()
    }
    group("Advanced") {
        createAdvancedSettings()
    }
}
```

### Sealed Classes для Results

```kotlin
sealed class LoadResult<out T> {
    data class Success<T>(val data: T) : LoadResult<T>()
    data class Error(val message: String) : LoadResult<Nothing>()
    object Loading : LoadResult<Nothing>()
}

suspend fun loadSections(): LoadResult<List<String>> {
    return try {
        val sections = cliService.execute(listOf("list", "sections"))
        LoadResult.Success(parseSections(sections))
    } catch (e: Exception) {
        LoadResult.Error(e.message ?: "Unknown error")
    }
}

// UI
when (val result = loadSections()) {
    is LoadResult.Success -> updateUI(result.data)
    is LoadResult.Error -> showError(result.message)
    is LoadResult.Loading -> showLoading()
}
```

### Data Classes для State

```kotlin
data class ToolWindowState(
    val selectedSection: String = "all",
    val selectedTemplate: String = "",
    val viewMode: ViewMode = ViewMode.TREE,
    val recentItems: List<String> = emptyList()
)

enum class ViewMode { FLAT, TREE }
```

## Useful Code Snippets

### Auto-refresh on config change

```kotlin
@Service(Service.Level.PROJECT)
class AutoRefreshService(
    private val project: Project,
    private val scope: CoroutineScope
) : Disposable {
    
    init {
        watchConfigFile()
    }
    
    private fun watchConfigFile() {
        project.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    val hasConfigChange = events.any { 
                        it.file?.name == "sections.yaml" 
                    }
                    
                    if (hasConfigChange) {
                        scope.launch {
                            delay(500) // Debounce
                            reloadConfig()
                        }
                    }
                }
            }
        )
    }
    
    private suspend fun reloadConfig() {
        // Reload
    }
    
    override fun dispose() { }
}
```

### Lazy initialization с caching

```kotlin
@Service
class ResourceLoader {
    
    private var cache: Map<String, Resource>? = null
    
    fun getResources(): Map<String, Resource> {
        if (cache == null) {
            cache = loadResourcesFromDisk()
        }
        return cache!!
    }
    
    fun invalidate() {
        cache = null
    }
}
```

### Typed DataKeys

```kotlin
object MyDataKeys {
    val SELECTED_ITEMS = DataKey.create<List<Item>>("myPlugin.selectedItems")
    val CURRENT_VIEW = DataKey.create<MyView>("myPlugin.currentView")
}

// DataProvider
class MyPanel : JPanel(), DataProvider {
    override fun getData(dataId: String): Any? {
        return when {
            MyDataKeys.SELECTED_ITEMS.`is`(dataId) -> getSelectedItems()
            MyDataKeys.CURRENT_VIEW.`is`(dataId) -> this
            else -> null
        }
    }
}

// Action
class MyAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val items = e.getData(MyDataKeys.SELECTED_ITEMS)
        val view = e.getData(MyDataKeys.CURRENT_VIEW)
    }
}
```

## Anti-Patterns (избегайте)

### ❌ Static mutable state

```kotlin
// ❌ ПЛОХО
object GlobalState {
    var currentProject: Project? = null // Утечка!
}

// ✅ ХОРОШО
@Service(Service.Level.PROJECT)
class ProjectState(private val project: Project)
```

### ❌ Blocking EDT

```kotlin
// ❌ ПЛОХО
override fun actionPerformed(e: AnActionEvent) {
    val data = loadFromNetwork() // Блокирует UI!
    updateUI(data)
}

// ✅ ХОРОШО
override fun actionPerformed(e: AnActionEvent) {
    scope.launch {
        val data = withContext(Dispatchers.IO) {
            loadFromNetwork()
        }
        withContext(Dispatchers.EDT) {
            updateUI(data)
        }
    }
}
```

### ❌ Catching ProcessCanceledException

```kotlin
// ❌ ПЛОХО
try {
    processData()
} catch (e: ProcessCanceledException) {
    LOG.error("Cancelled", e) // НЕ ДЕЛАЙТЕ!
}

// ✅ ХОРОШО
try {
    processData()
} catch (e: ProcessCanceledException) {
    throw e // Re-throw
} catch (e: Exception) {
    LOG.error("Failed", e)
}
```

### ❌ Сохранение Project в static field

```kotlin
// ❌ ПЛОХО
object MyUtil {
    var project: Project? = null
    
    fun doWork() {
        project?.let { /* work */ }
    }
}

// ✅ ХОРОШО
object MyUtil {
    fun doWork(project: Project) {
        // Передавать как параметр
    }
}
```
