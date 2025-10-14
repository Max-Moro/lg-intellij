# Services в IntelliJ Platform

## Что такое Service

**Service** — это компонент плагина, загружаемый **on-demand** (по требованию) при первом обращении. IntelliJ Platform гарантирует создание только одного экземпляра сервиса в пределах его scope (Application, Project или Module).

Services используются для:
- Инкапсуляции логики, работающей с набором связанных классов
- Предоставления переиспользуемой функциональности
- Управления состоянием плагина
- Взаимодействия с внешними системами (CLI, API и т.д.)

## Типы Services

### 1. Application-Level Services

**Scope:** весь жизненный цикл IDE  
**Instance:** один экземпляр на всё приложение  
**Использование:** глобальная конфигурация, кэши, утилиты

```kotlin
@Service
class MyApplicationService {
    
    private val cache = mutableMapOf<String, Data>()
    
    fun getData(key: String): Data? {
        return cache[key]
    }
    
    fun putData(key: String, data: Data) {
        cache[key] = data
    }
}

// Получение
val service = service<MyApplicationService>()
service.putData("key", data)
```

### 2. Project-Level Services

**Scope:** пока проект открыт  
**Instance:** отдельный экземпляр для каждого открытого проекта  
**Использование:** проект-специфичная логика, настройки проекта

```kotlin
@Service(Service.Level.PROJECT)
class MyProjectService(private val project: Project) {
    
    fun getProjectName(): String {
        return project.name
    }
    
    fun processProjectFiles() {
        val baseDir = project.basePath ?: return
        // Работа с файлами проекта
    }
}

// Получение
val service = project.service<MyProjectService>()
```

### 3. Module-Level Services (не рекомендуется)

**Scope:** пока модуль существует  
**Instance:** отдельный экземпляр для каждого модуля  
**Проблема:** увеличение потребления памяти в проектах с множеством модулей

```kotlin
// ⚠️ Избегайте module services
@Service(Service.Level.MODULE)
class MyModuleService(private val module: Module)

// ✅ Используйте project service + Module parameter
@Service(Service.Level.PROJECT)
class MyProjectService(private val project: Project) {
    
    fun processModule(module: Module) {
        // Передавайте Module как параметр
    }
}
```

## Light Services (современный подход)

**Light Services** — сервисы без регистрации в `plugin.xml`.

### Преимущества Light Services

- ✅ Меньше boilerplate кода
- ✅ Не нужна регистрация в plugin.xml
- ✅ Автоматическое определение scope по вызывающему контексту
- ✅ Улучшенная производительность

### Требования к Light Services

1. **Класс должен быть final**
   ```kotlin
   // Kotlin: final по умолчанию ✓
   @Service
   class MyService
   
   // Java: явно final
   @Service
   public final class MyService { }
   ```

2. **Нет constructor injection** (кроме Project/Module)
   ```kotlin
   // ❌ НЕПРАВИЛЬНО
   @Service
   class MyService(
       private val otherService: OtherService // НЕ ПОДДЕРЖИВАЕТСЯ
   )
   
   // ✅ ПРАВИЛЬНО
   @Service
   class MyService {
       fun doWork() {
           val otherService = service<OtherService>() // On-demand
           otherService.help()
       }
   }
   ```

3. **Project/Module constructor parameter** (опционально)
   ```kotlin
   @Service(Service.Level.PROJECT)
   class MyProjectService(private val project: Project) { }
   
   @Service(Service.Level.MODULE)
   class MyModuleService(private val module: Module) { }
   ```

4. **PersistentStateComponent roaming disabled** (application-level only)
   ```kotlin
   @Service
   @State(
       name = "MySettings",
       storages = [Storage("mySettings.xml")],
       roamingType = RoamingType.DISABLED // Обязательно!
   )
   class MySettings : SimplePersistentStateComponent<MySettings.State>(State()) {
       class State : BaseState()
   }
   ```

### Примеры Light Services

#### Application Service

```kotlin
@Service
class CliClientService {
    
    private var cachedVersion: String? = null
    
    fun getCliVersion(): String {
        if (cachedVersion == null) {
            cachedVersion = detectVersion()
        }
        return cachedVersion!!
    }
    
    private fun detectVersion(): String {
        // Detect CLI version
        return "1.0.0"
    }
}
```

#### Project Service

```kotlin
@Service(Service.Level.PROJECT)
class LgConfigService(private val project: Project) {
    
    fun getConfigDirectory(): VirtualFile? {
        val basePath = project.basePath ?: return null
        return LocalFileSystem.getInstance()
            .findFileByPath("$basePath/lg-cfg")
    }
    
    fun listSections(): List<String> {
        val configDir = getConfigDirectory() ?: return emptyList()
        // Parse sections.yaml
        return emptyList()
    }
}
```

#### Service with Disposable

```kotlin
@Service(Service.Level.PROJECT)
class ConnectionService(
    private val project: Project
) : Disposable {
    
    private val connection = createConnection()
    
    init {
        // Setup connection
        connection.connect()
    }
    
    fun sendRequest(request: Request): Response {
        return connection.send(request)
    }
    
    override fun dispose() {
        connection.close()
    }
}
```

## Non-Light Services (legacy)

Если требуется:
- Переопределение реализации другим плагином
- Exposing API для других плагинов
- OS-specific реализации
- Test/headless реализации

Тогда регистрируйте в `plugin.xml`:

```xml
<extensions defaultExtensionNs="com.intellij">
    
    <!-- С интерфейсом (для API) -->
    <applicationService 
        serviceInterface="com.example.MyService"
        serviceImplementation="com.example.MyServiceImpl"/>
    
    <!-- Только реализация (без API) -->
    <projectService 
        serviceImplementation="com.example.MyProjectService"/>
    
    <!-- С test реализацией -->
    <applicationService 
        serviceInterface="com.example.CacheService"
        serviceImplementation="com.example.CacheServiceImpl"
        testServiceImplementation="com.example.TestCacheService"/>
    
    <!-- OS-specific -->
    <applicationService 
        serviceImplementation="com.example.WindowsService"
        os="windows"/>
</extensions>
```

## Получение Services

### Правильный способ (Kotlin)

```kotlin
import com.intellij.openapi.components.service

// Application service
val appService = service<MyApplicationService>()

// Project service
val projectService = project.service<MyProjectService>()

// Module service (не рекомендуется)
val moduleService = module.service<MyModuleService>()
```

### Правильный способ (Java)

```java
import com.intellij.openapi.application.ApplicationManager;

// Application service
MyApplicationService appService = ApplicationManager.getApplication()
    .getService(MyApplicationService.class);

// Project service
MyProjectService projectService = project.getService(MyProjectService.class);
```

### getInstance() Helper (опционально)

Многие сервисы предоставляют статический helper:

```kotlin
@Service
class MyService {
    
    companion object {
        fun getInstance(): MyService = service()
    }
    
    fun doWork() { }
}

// Использование
MyService.getInstance().doWork()
```

Для Project service:

```kotlin
@Service(Service.Level.PROJECT)
class MyProjectService(private val project: Project) {
    
    companion object {
        fun getInstance(project: Project): MyProjectService {
            return project.service()
        }
    }
    
    fun doWork() { }
}

// Использование
MyProjectService.getInstance(project).doWork()
```

## Правила работы с Services

### ❌ НЕ сохраняйте service в полях

```kotlin
// ❌ ПЛОХО
class MyAction : AnAction() {
    private val service = service<MyService>() // УТЕЧКА ПАМЯТИ!
    
    override fun actionPerformed(e: AnActionEvent) {
        service.doWork()
    }
}

// ✅ ХОРОШО
class MyAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val service = service<MyService>() // Получить здесь
        service.doWork()
    }
}
```

**Почему:** Actions живут весь жизненный цикл приложения. Сохранение ссылки на Project service приведёт к утечке памяти после закрытия проекта.

### ❌ НЕ создавайте service в конструкторе

```kotlin
// ❌ ПЛОХО
class MyService {
    init {
        val other = service<OtherService>()
        // Цепочка инициализации
    }
}

// ✅ ХОРОШО
class MyService {
    
    private val otherService by lazy {
        service<OtherService>() // Ленивая инициализация
    }
    
    fun doWork() {
        otherService.help()
    }
}
```

### ✅ Получайте service только когда нужен

```kotlin
class MyService {
    
    fun processData(data: Data) {
        // Получить service в момент использования
        val validator = service<ValidationService>()
        
        if (validator.isValid(data)) {
            process(data)
        }
    }
}
```

## Service Lifecycle

### Инициализация

Service создаётся при первом вызове `getService()` / `service<>()`:

```
User code
  ↓
getService()
  ↓
Is created? ──No──→ Create instance
  │                    ↓
  │              Call constructor
  │                    ↓
  │              Register Disposable (if needed)
  │                    ↓
  │              Load State (PersistentStateComponent)
  │                    ↓
  Yes ←──────────── Return instance
  ↓
Return instance
```

### Disposal

Service уничтожается при dispose родительского Disposable:
- **Application service:** при закрытии IDE
- **Project service:** при закрытии проекта
- **Module service:** при unload модуля

```kotlin
@Service(Service.Level.PROJECT)
class MyService(private val project: Project) : Disposable {
    
    private val connection = createConnection()
    
    init {
        LOG.info("Service created for project: ${project.name}")
    }
    
    override fun dispose() {
        LOG.info("Service disposing for project: ${project.name}")
        connection.close()
    }
    
    companion object {
        private val LOG = logger<MyService>()
    }
}
```

## Kotlin Coroutines в Services

С 2024.1+ рекомендуется использовать Kotlin Coroutines для асинхронных операций.

### Service Scope Injection

```kotlin
@Service(Service.Level.PROJECT)
class MyService(
    private val project: Project,
    private val scope: CoroutineScope // Injected!
) {
    
    fun loadDataAsync() {
        scope.launch {
            val data = withContext(Dispatchers.IO) {
                loadFromNetwork()
            }
            withContext(Dispatchers.EDT) {
                updateUI(data)
            }
        }
    }
}
```

**CoroutineScope автоматически:**
- Создаётся при инициализации service
- Отменяется при dispose service
- Использует правильный dispatcher

### Application Service Scope

```kotlin
@Service
class MyAppService(
    private val scope: CoroutineScope
) {
    
    fun startBackgroundTask() {
        scope.launch {
            // Coroutine cancelled automatically when IDE closes
            while (true) {
                delay(1000)
                checkSomething()
            }
        }
    }
}
```

## PersistentStateComponent (сохранение состояния)

Services могут сохранять состояние между перезапусками IDE.

### SimplePersistentStateComponent (Kotlin)

```kotlin
@Service
@State(
    name = "MySettings",
    storages = [Storage("myPlugin.xml")]
)
class MySettings : SimplePersistentStateComponent<MySettings.State>(State()) {
    
    class State : BaseState() {
        var apiKey by string("")
        var maxResults by property(100)
        var enableFeature by property(false)
        var recentItems by list<String>()
    }
}

// Использование
val settings = service<MySettings>()
settings.state.apiKey = "new-key"
settings.state.enableFeature = true
settings.state.recentItems.add("item")
```

**BaseState property delegates:**
- `by string(default)` — String property
- `by property(default)` — любой тип
- `by list<T>()` — mutable list
- `by map<K, V>()` — mutable map
- `by enum(default)` — enum

### SerializablePersistentStateComponent (Kotlin 2022.2+)

Immutable data class подход:

```kotlin
@Service
@State(
    name = "MyConfig",
    storages = [Storage("myPlugin.xml")]
)
class MyConfig : SerializablePersistentStateComponent<MyConfig.State>(State()) {
    
    var apiUrl: String
        get() = state.apiUrl
        set(value) {
            updateState { it.copy(apiUrl = value) }
        }
    
    var timeout: Int
        get() = state.timeout
        set(value) {
            updateState { it.copy(timeout = value) }
        }
    
    data class State(
        @JvmField val apiUrl: String = "https://api.example.com",
        @JvmField val timeout: Int = 30
    )
}
```

**Преимущества:**
- Атомарные обновления (thread-safe)
- Автоматический tracking изменений
- Immutable state

### PersistentStateComponent (Java)

```java
@Service
@State(
    name = "MySettings",
    storages = @Storage("myPlugin.xml")
)
public final class MySettings 
    implements PersistentStateComponent<MySettings.State> {
    
    public static class State {
        public String apiKey = "";
        public int maxResults = 100;
        public boolean enabled = false;
    }
    
    private State myState = new State();
    
    @Override
    public State getState() {
        return myState;
    }
    
    @Override
    public void loadState(State state) {
        myState = state;
    }
    
    public static MySettings getInstance() {
        return ApplicationManager.getApplication()
            .getService(MySettings.class);
    }
}
```

### Storage Locations

```kotlin
// Application-level storage
@State(
    name = "MyAppSettings",
    storages = [Storage("myPlugin.xml")] // Свой файл
)

// Project-level storage (workspace file)
@State(
    name = "MyProjectSettings",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)]
)

// Project-level storage (project file)
@State(
    name = "MyProjectConfig",
    storages = [Storage("myPlugin.xml")] // В .idea/
)

// Cache storage (не sync между машинами)
@State(
    name = "MyCache",
    storages = [Storage(StoragePathMacros.CACHE_FILE)]
)
```

**Рекомендации:**
- Application-level: **свой XML файл** (`myPlugin.xml`)
- Project-level: **workspace file** для UI state, свой файл для настроек
- Кэши: **CACHE_FILE**

### Roaming Settings

Настройки могут синхронизироваться между установками IDE:

```kotlin
@State(
    name = "MySettings",
    storages = [
        Storage(
            value = "myPlugin.xml",
            roamingType = RoamingType.DEFAULT // Sync enabled
        )
    ],
    category = SettingsCategory.TOOLS // Категория для Backup & Sync
)
class MySettings : SimplePersistentStateComponent<State>(State())
```

**RoamingType:**
- `DEFAULT` — синхронизируются между машинами
- `PER_OS` — отдельно для каждой ОС
- `DISABLED` — не синхронизируются

**SettingsCategory:**
- `TOOLS` — инструменты
- `CODE` — код и форматирование
- `APPEARANCE` — внешний вид
- `SYSTEM` — системные настройки
- `OTHER` — не синхронизируется

## Service с внешними зависимостями

### HTTP Client Service

```kotlin
@Service
class ApiClientService : Disposable {
    
    private val client = HttpClient.newHttpClient()
    
    suspend fun fetchData(url: String): String {
        return withContext(Dispatchers.IO) {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build()
            
            val response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            )
            
            response.body()
        }
    }
    
    override fun dispose() {
        // HttpClient не требует явного close в Java 11+
    }
}
```

### CLI Wrapper Service

```kotlin
@Service(Service.Level.PROJECT)
class CliService(
    private val project: Project,
    private val scope: CoroutineScope
) {
    
    suspend fun runCommand(args: List<String>): String {
        return withContext(Dispatchers.IO) {
            val command = GeneralCommandLine(args)
            command.withWorkDirectory(project.basePath)
            
            val output = CapturingProcessHandler(
                command.createProcess(),
                Charset.defaultCharset(),
                command.commandLineString
            ).runProcess()
            
            if (output.exitCode != 0) {
                throw RuntimeException(output.stderr)
            }
            
            output.stdout
        }
    }
    
    fun runCommandAsync(
        args: List<String>,
        callback: (String) -> Unit
    ) {
        scope.launch {
            try {
                val result = runCommand(args)
                withContext(Dispatchers.EDT) {
                    callback(result)
                }
            } catch (e: Exception) {
                LOG.error("Command failed", e)
            }
        }
    }
    
    companion object {
        private val LOG = logger<CliService>()
    }
}
```

## Service Patterns

### 1. Facade Service

Упрощённый интерфейс к сложной подсистеме:

```kotlin
@Service(Service.Level.PROJECT)
class LgFacadeService(private val project: Project) {
    
    private val cliService: CliService
        get() = project.service()
    
    private val configService: LgConfigService
        get() = project.service()
    
    suspend fun generateListing(section: String): String {
        val sections = configService.listSections()
        
        if (section !in sections) {
            throw IllegalArgumentException("Unknown section: $section")
        }
        
        return cliService.runCommand(
            listOf("listing-generator", "render", "sec:$section")
        )
    }
}
```

### 2. Registry Service (cached data)

```kotlin
@Service
class IconRegistryService {
    
    private val iconCache = ConcurrentHashMap<String, Icon>()
    
    fun getIcon(path: String): Icon {
        return iconCache.computeIfAbsent(path) { p ->
            IconLoader.getIcon(p, javaClass)
        }
    }
}
```

### 3. State Machine Service

```kotlin
@Service(Service.Level.PROJECT)
class TaskExecutorService(
    private val project: Project,
    private val scope: CoroutineScope
) {
    
    private val _state = MutableStateFlow<TaskState>(TaskState.Idle)
    val state: StateFlow<TaskState> = _state.asStateFlow()
    
    fun executeTask(task: Task) {
        if (_state.value !is TaskState.Idle) {
            throw IllegalStateException("Task already running")
        }
        
        scope.launch {
            _state.value = TaskState.Running(task)
            try {
                val result = task.execute()
                _state.value = TaskState.Completed(result)
            } catch (e: Exception) {
                _state.value = TaskState.Failed(e)
            } finally {
                delay(3000) // Show result
                _state.value = TaskState.Idle
            }
        }
    }
    
    sealed class TaskState {
        object Idle : TaskState()
        data class Running(val task: Task) : TaskState()
        data class Completed(val result: Any) : TaskState()
        data class Failed(val error: Exception) : TaskState()
    }
}
```

## Testing Services

### Test Service

```kotlin
class MyServiceTest : BasePlatformTestCase() {
    
    private lateinit var service: MyProjectService
    
    override fun setUp() {
        super.setUp()
        service = project.service()
    }
    
    fun testServiceMethod() {
        val result = service.processData("test")
        assertNotNull(result)
    }
}
```

### Mock Service для тестов

Зарегистрируйте test implementation:

```xml
<applicationService 
    serviceInterface="com.example.ApiService"
    serviceImplementation="com.example.ApiServiceImpl"
    testServiceImplementation="com.example.MockApiService"/>
```

```kotlin
// Production
class ApiServiceImpl : ApiService {
    override fun fetchData() = realHttpCall()
}

// Test
class MockApiService : ApiService {
    override fun fetchData() = "mock data"
}
```

## PropertiesComponent (простое key-value хранилище)

Для простых значений без нужды в PersistentStateComponent:

```kotlin
import com.intellij.ide.util.PropertiesComponent

// Application-level
val props = PropertiesComponent.getInstance()
props.setValue("com.example.myplugin.lastValue", "value")
val value = props.getValue("com.example.myplugin.lastValue")

// Project-level
val projectProps = PropertiesComponent.getInstance(project)
projectProps.setValue("com.example.myplugin.projectSetting", "value")
```

**Важно:**
- Всегда используйте **префикс** с plugin ID
- Roaming **отключён** (не синхронизируется)
- Для временных значений, не для настроек

## Inspection подсказки

Plugin DevKit предоставляет инспекции для Services:

### Доступные inspections

1. **Light service must be final**
   - Проверяет что light service классы final

2. **Mismatch between light service level and its constructor**
   - Проверяет соответствие @Service level и constructor параметров

3. **A service can be converted to a light one**
   - Предлагает конвертацию в light service

4. **Non-default constructors for service**
   - Предупреждает о constructor injection

5. **Incorrect service retrieving**
   - Находит неправильное использование getService()

6. **Application service assigned to a static field**
   - Находит хранение в статических полях

Включите все: **Settings → Editor → Inspections → Plugin DevKit → Code**

## Best Practices

### 1. Предпочитайте Light Services

```kotlin
// ✅ Современный подход
@Service
class MyService

// ❌ Legacy (только если нужны special features)
<applicationService serviceImplementation="..."/>
```

### 2. Используйте правильный scope

```kotlin
// ❌ Плохо — application service для project data
@Service
class ProjectDataService {
    private val projectData = mutableMapOf<Project, Data>()
}

// ✅ Хорошо — project service
@Service(Service.Level.PROJECT)
class ProjectDataService(private val project: Project) {
    private var data: Data? = null
}
```

### 3. Избегайте тяжёлой инициализации

```kotlin
// ❌ Плохо — тяжёлая работа в конструкторе
@Service
class MyService {
    private val data = loadFromDisk() // Долго!
}

// ✅ Хорошо — ленивая загрузка
@Service
class MyService {
    private val data by lazy { loadFromDisk() }
    
    fun getData(): Data = data
}
```

### 4. Делегируйте специализированным service

```kotlin
// ❌ God Object
@Service
class MegaService {
    fun doEverything() { }
    fun alsoThis() { }
    fun andThat() { }
}

// ✅ Single Responsibility
@Service
class ConfigService { }

@Service
class CliService { }

@Service
class CacheService { }
```

### 5. Используйте companion object для getInstance()

```kotlin
@Service
class MyService {
    
    fun doWork() { }
    
    companion object {
        fun getInstance(): MyService = service()
    }
}

// Чистое API
MyService.getInstance().doWork()
```

### 6. Документируйте public API

```kotlin
/**
 * Service for managing Listing Generator CLI integration.
 * 
 * This service provides methods to:
 * - Execute CLI commands
 * - Parse CLI output
 * - Manage CLI configuration
 */
@Service(Service.Level.PROJECT)
class CliIntegrationService(private val project: Project) {
    
    /**
     * Executes CLI command and returns output.
     * 
     * @param args command arguments
     * @return command output
     * @throws CliException if command fails
     */
    suspend fun executeCommand(args: List<String>): String {
        // Implementation
    }
}
```

## Примеры из реальных плагинов

### Git Integration Service

```kotlin
@Service(Service.Level.PROJECT)
class GitIntegrationService(private val project: Project) {
    
    fun getCurrentBranch(): String? {
        val gitRepository = GitUtil.getRepositoryManager(project)
            .repositories
            .firstOrNull() ?: return null
        
        return gitRepository.currentBranch?.name
    }
    
    fun getChangedFiles(): List<VirtualFile> {
        val changeListManager = ChangeListManager.getInstance(project)
        return changeListManager.affectedFiles
    }
}
```

### Settings Service

```kotlin
@Service
@State(
    name = "LgSettings",
    storages = [Storage("lg-settings.xml")],
    category = SettingsCategory.TOOLS
)
class LgSettingsService : SimplePersistentStateComponent<LgSettingsService.State>(State()) {
    
    class State : BaseState() {
        var cliPath by string("")
        var pythonInterpreter by string("")
        var defaultTokenizer by string("tiktoken")
        var defaultEncoder by string("cl100k_base")
        var contextLimit by property(128000)
    }
    
    companion object {
        fun getInstance(): LgSettingsService = service()
    }
}
```

## Common Pitfalls (частые ошибки)

### 1. Сохранение service в field

```kotlin
// ❌ УТЕЧКА ПАМЯТИ
class MyAction : AnAction() {
    private val projectService = project?.service<MyProjectService>()
}

// ✅ ПРАВИЛЬНО
class MyAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = project.service<MyProjectService>()
    }
}
```

### 2. Циклические зависимости

```kotlin
// ❌ DEADLOCK
@Service
class ServiceA {
    init {
        service<ServiceB>() // Обращение к B в конструкторе A
    }
}

@Service
class ServiceB {
    init {
        service<ServiceA>() // Обращение к A в конструкторе B
    }
}

// ✅ ПРАВИЛЬНО
@Service
class ServiceA {
    private val serviceB by lazy { service<ServiceB>() }
}

@Service
class ServiceB {
    // Нет обращения к ServiceA в init
}
```

### 3. Забыли Disposable

```kotlin
// ❌ Утечка ресурсов
@Service
class ConnectionService {
    private val connection = createConnection()
    // Нет dispose!
}

// ✅ ПРАВИЛЬНО
@Service
class ConnectionService : Disposable {
    private val connection = createConnection()
    
    override fun dispose() {
        connection.close()
    }
}
```
