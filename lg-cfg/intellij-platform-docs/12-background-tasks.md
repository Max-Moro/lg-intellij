# Background Tasks и Kotlin Coroutines

## Обзор

IntelliJ Platform — многопоточная среда. Долгие операции должны выполняться в **background threads** чтобы не блокировать UI.

**Два основных подхода:**

1. **Kotlin Coroutines** (2024.1+) — **рекомендуется**
   - Современный асинхронный подход
   - Легковесные (не привязаны к OS threads)
   - Structured concurrency
   - Автоматическая cancellation

2. **Progress API** (legacy) — **устаревает**
   - Основан на потоках
   - Task классы с ProgressIndicator
   - Более тяжеловесный

## Kotlin Coroutines (рекомендуется)

### Почему Coroutines

**Преимущества:**
- ✅ Легковесные (100,000+ coroutines vs ~тысячи threads)
- ✅ Структурированная конкурентность (structured concurrency)
- ✅ Простой код (выглядит как синхронный)
- ✅ Автоматическая cancellation при dispose
- ✅ Простое переключение между потоками

**Требования:**
- Kotlin (не работает из Java)
- IntelliJ Platform 2024.1+
- Использование bundled Kotlin Coroutines library

### Service Scope Injection

Самый простой способ — inject CoroutineScope в Service:

```kotlin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
class MyService(
    private val project: Project,
    private val scope: CoroutineScope // ← Injected!
) {
    
    fun loadDataAsync() {
        scope.launch {
            val data = loadData()
            updateUI(data)
        }
    }
}
```

**CoroutineScope автоматически:**
- Создаётся при создании service
- Отменяется при dispose service
- Использует правильные dispatchers

### Dispatchers

```kotlin
import kotlinx.coroutines.*
import com.intellij.openapi.application.EDT

scope.launch {
    // Default dispatcher (BGT, thread pool)
    val data = loadFromDisk()
    
    // IO dispatcher (для I/O операций)
    withContext(Dispatchers.IO) {
        val networkData = fetchFromNetwork()
    }
    
    // EDT dispatcher (UI thread)
    withContext(Dispatchers.EDT) {
        updateUI(data)
    }
}
```

**Когда что использовать:**
- `Dispatchers.Default` — CPU-bound операции
- `Dispatchers.IO` — I/O операции (disk, network)
- `Dispatchers.EDT` — UI updates

### Read/Write Actions в Coroutines

```kotlin
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction

scope.launch {
    // Read PSI/VFS в background
    val files = readAction {
        PsiManager.getInstance(project)
            .findDirectory(directory)
            ?.files
            ?.toList()
    }
    
    // Write в EDT
    withContext(Dispatchers.EDT) {
        writeAction {
            for (file in files) {
                file.delete()
            }
        }
    }
}
```

## Базовые паттерны с Coroutines

### 1. Load data asynchronously

```kotlin
@Service(Service.Level.PROJECT)
class DataService(
    private val project: Project,
    private val scope: CoroutineScope
) {
    
    suspend fun loadData(): Data {
        return withContext(Dispatchers.IO) {
            // I/O operation
            readFromDisk()
        }
    }
    
    fun loadDataAsync(callback: (Data) -> Unit) {
        scope.launch {
            val data = loadData()
            withContext(Dispatchers.EDT) {
                callback(data)
            }
        }
    }
}
```

### 2. Execute CLI command

```kotlin
@Service(Service.Level.PROJECT)
class CliService(
    private val project: Project,
    private val scope: CoroutineScope
) {
    
    suspend fun executeCommand(args: List<String>): String {
        return withContext(Dispatchers.IO) {
            val commandLine = GeneralCommandLine(args)
                .withWorkDirectory(project.basePath)
            
            val handler = CapturingProcessHandler(commandLine)
            val output = handler.runProcess(60_000)
            
            if (output.exitCode != 0) {
                throw CliException(output.stderr)
            }
            
            output.stdout
        }
    }
}
```

### 3. Background processing с updates

```kotlin
fun processFilesAsync(files: List<VirtualFile>) {
    scope.launch {
        for ((index, file) in files.withIndex()) {
            // Process в IO
            val result = withContext(Dispatchers.IO) {
                processFile(file)
            }
            
            // Update UI в EDT
            withContext(Dispatchers.EDT) {
                updateProgress(index + 1, files.size)
                showResult(result)
            }
        }
        
        withContext(Dispatchers.EDT) {
            showCompletionNotification()
        }
    }
}
```

### 4. Cancellable operation

```kotlin
fun startBackgroundTask(): Job {
    return scope.launch {
        try {
            while (true) {
                // Автоматическая проверка cancellation
                delay(1000) // Вместо Thread.sleep
                
                val data = fetchData()
                updateUI(data)
            }
        } catch (e: CancellationException) {
            // Coroutine cancelled
            cleanup()
            throw e // Re-throw
        }
    }
}

// Отмена
val job = startBackgroundTask()

// Позже
job.cancel() // Отменит coroutine
```

## Progress API (legacy)

Для плагинов, поддерживающих pre-2024.1 или Java.

### Task.Backgroundable

```kotlin
import com.intellij.openapi.progress.*

class MyAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        object : Task.Backgroundable(
            project,
            "Processing...",     // Title
            true                 // Cancellable
        ) {
            override fun run(indicator: ProgressIndicator) {
                // Background thread
                indicator.text = "Loading..."
                indicator.fraction = 0.1
                
                val data = loadData()
                
                indicator.checkCanceled() // Проверка отмены
                
                indicator.text = "Processing..."
                indicator.fraction = 0.5
                
                val result = processData(data)
                
                indicator.fraction = 1.0
                
                // Update UI
                ApplicationManager.getApplication().invokeLater {
                    showResult(project, result)
                }
            }
            
            override fun onSuccess() {
                // Вызывается в EDT после успешного завершения
                println("Task completed")
            }
            
            override fun onThrowable(error: Throwable) {
                // Вызывается при exception
                Messages.showErrorDialog(
                    project,
                    error.message,
                    "Error"
                )
            }
            
            override fun onCancel() {
                // Вызывается при отмене
                println("Task cancelled")
            }
        }.setCancelText("Stop Processing")
          .queue()
    }
    
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
```

### Task Types

```kotlin
// Backgroundable — фоновая задача с прогрессом
object : Task.Backgroundable(project, "Title", true) {
    override fun run(indicator: ProgressIndicator) { }
}.queue()

// Modal — модальный диалог прогресса (блокирует UI)
object : Task.Modal(project, "Title", true) {
    override fun run(indicator: ProgressIndicator) { }
}.queue()

// ConditionalModal — модальный если долго
object : Task.ConditionalModal(
    project,
    "Title",
    true,
    1000 // Модальный если > 1 sec
) {
    override fun run(indicator: ProgressIndicator) { }
}.queue()

// WithResult — с возвратом результата
val result = object : Task.WithResult<String, Exception>(
    project,
    "Loading",
    true
) {
    override fun compute(indicator: ProgressIndicator): String {
        return loadData()
    }
}.queue()
```

### ProgressIndicator API

```kotlin
override fun run(indicator: ProgressIndicator) {
    // Текст над progress bar
    indicator.text = "Loading configuration..."
    
    // Текст под progress bar (детали)
    indicator.text2 = "Processing file 5 of 10"
    
    // Прогресс (0.0 - 1.0)
    indicator.fraction = 0.5
    
    // Indeterminate (бесконечный spinner)
    indicator.isIndeterminate = true
    
    // Проверка отмены
    indicator.checkCanceled() // Throws ProcessCanceledException
    
    // Проверка без exception
    if (indicator.isCanceled) {
        cleanup()
        return
    }
}
```

### ProgressManager

Для запуска без Task классов:

```kotlin
import com.intellij.openapi.progress.ProgressManager

// Synchronously с модальным диалогом
val result = ProgressManager.getInstance()
    .runProcessWithProgressSynchronously(
        {
            val indicator = ProgressManager.getInstance().progressIndicator
            indicator.text = "Processing..."
            processData()
        },
        "Please Wait",
        true, // cancellable
        project
    )

// Asynchronously с background прогрессом
ProgressManager.getInstance()
    .run(backgroundableTask)
```

## Coroutines Advanced Patterns

### StateFlow для reactive state

```kotlin
@Service(Service.Level.PROJECT)
class TaskExecutor(
    private val project: Project,
    private val scope: CoroutineScope
) {
    
    private val _state = MutableStateFlow<TaskState>(TaskState.Idle)
    val state: StateFlow<TaskState> = _state.asStateFlow()
    
    sealed class TaskState {
        object Idle : TaskState()
        data class Running(val progress: Int) : TaskState()
        data class Completed(val result: String) : TaskState()
        data class Failed(val error: String) : TaskState()
    }
    
    fun executeTask(task: Task) {
        scope.launch {
            _state.value = TaskState.Running(0)
            
            try {
                for (i in 1..10) {
                    delay(100)
                    _state.value = TaskState.Running(i * 10)
                    processStep(task, i)
                }
                
                val result = finalizeTask(task)
                _state.value = TaskState.Completed(result)
                
            } catch (e: CancellationException) {
                _state.value = TaskState.Idle
                throw e
            } catch (e: Exception) {
                _state.value = TaskState.Failed(e.message ?: "Unknown error")
            }
        }
    }
}

// UI подписывается на state
scope.launch {
    taskExecutor.state.collect { state ->
        withContext(Dispatchers.EDT) {
            when (state) {
                is TaskState.Idle -> hideProgress()
                is TaskState.Running -> showProgress(state.progress)
                is TaskState.Completed -> showResult(state.result)
                is TaskState.Failed -> showError(state.error)
            }
        }
    }
}
```

### Parallel execution

```kotlin
suspend fun loadAllData(): AllData {
    return coroutineScope {
        // Параллельное выполнение
        val sections = async { loadSections() }
        val contexts = async { loadContexts() }
        val encoders = async { loadEncoders() }
        
        // Дождаться всех
        AllData(
            sections = sections.await(),
            contexts = contexts.await(),
            encoders = encoders.await()
        )
    }
}
```

### Timeout

```kotlin
suspend fun loadWithTimeout(timeout: Long): Data? {
    return withTimeoutOrNull(timeout) {
        loadData()
    }
}

// Использование
val data = loadWithTimeout(5000) // 5 sec
if (data == null) {
    // Timeout
}
```

### Retry logic

```kotlin
suspend fun <T> retryIO(
    times: Int = 3,
    delayMs: Long = 1000,
    block: suspend () -> T
): T {
    repeat(times - 1) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            LOG.warn("Attempt ${attempt + 1} failed", e)
            delay(delayMs)
        }
    }
    
    return block() // Последняя попытка (throws)
}

// Использование
val data = retryIO(times = 3) {
    fetchFromNetwork()
}
```

## CoroutineScope Management

### В Services (автоматический)

```kotlin
@Service(Service.Level.PROJECT)
class MyService(
    private val project: Project,
    private val scope: CoroutineScope // Auto-managed
) {
    // scope.cancel() вызовется при dispose автоматически
}
```

### В UI Components (ручной)

```kotlin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class MyPanel(
    private val project: Project
) : JPanel(), Disposable {
    
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default
    )
    
    init {
        loadDataAsync()
    }
    
    private fun loadDataAsync() {
        scope.launch {
            val data = loadData()
            withContext(Dispatchers.EDT) {
                updateUI(data)
            }
        }
    }
    
    override fun dispose() {
        scope.cancel() // Отменить все coroutines
    }
}
```

**Важно:** всегда регистрируйте Disposable:

```kotlin
val panel = MyPanel(project)
Disposer.register(parentDisposable, panel)
```

## Switching Threads

### Background → EDT

```kotlin
scope.launch {
    // Background thread
    val data = loadFromDisk()
    
    // Switch to EDT
    withContext(Dispatchers.EDT) {
        label.text = data // UI update
    }
}
```

### EDT → Background

```kotlin
// On EDT (например, в actionPerformed)
scope.launch(Dispatchers.IO) {
    // Switch to background
    val result = heavyComputation()
    
    withContext(Dispatchers.EDT) {
        showResult(result)
    }
}
```

### Read/Write Actions

```kotlin
scope.launch {
    // Read в background
    val psiFile = readAction {
        PsiManager.getInstance(project).findFile(virtualFile)
    }
    
    // Write в EDT
    withContext(Dispatchers.EDT) {
        writeAction {
            psiFile?.delete()
        }
    }
}
```

## Progress Reporting (Coroutines)

### WithBackgroundProgress

```kotlin
import com.intellij.platform.util.progress.withBackgroundProgress

scope.launch {
    withBackgroundProgress(
        project,
        "Generating Listing...",
        cancellable = true
    ) {
        // Coroutine с автоматическим progress indicator
        
        reportProgress { reporter ->
            reporter.text("Loading configuration...")
            val config = loadConfig()
            
            reporter.text("Processing files...")
            reporter.fraction(0.5)
            val files = processFiles(config)
            
            reporter.text("Rendering...")
            reporter.fraction(0.9)
            val result = render(files)
            
            withContext(Dispatchers.EDT) {
                showResult(result)
            }
        }
    }
}
```

### Indeterminate Progress

```kotlin
withBackgroundProgress(project, "Loading...", cancellable = true) {
    // Без reportProgress = indeterminate spinner
    loadData()
}
```

## Cancellation

### Автоматическая cancellation

```kotlin
@Service(Service.Level.PROJECT)
class MyService(
    private val project: Project,
    private val scope: CoroutineScope
) {
    
    fun startLongTask() {
        scope.launch {
            // Эта coroutine отменится автоматически когда:
            // - Project закроется
            // - Service dispose вызовется
            
            while (true) {
                delay(1000)
                doWork()
            }
        }
    }
}
```

### Ручная cancellation

```kotlin
class TaskManager {
    
    private var currentJob: Job? = null
    
    fun startTask() {
        // Отменить предыдущую задачу
        currentJob?.cancel()
        
        // Запустить новую
        currentJob = scope.launch {
            processTask()
        }
    }
    
    fun cancelTask() {
        currentJob?.cancel()
        currentJob = null
    }
}
```

### Проверка cancellation

```kotlin
suspend fun processLargeDataset(items: List<Item>) {
    for (item in items) {
        // Автоматическая проверка при delay/yield
        delay(1) // Если cancelled → CancellationException
        
        // Или явная проверка
        ensureActive() // Throws CancellationException if cancelled
        
        processItem(item)
    }
}
```

### Cleanup при cancellation

```kotlin
scope.launch {
    val resource = openResource()
    
    try {
        processWithResource(resource)
    } finally {
        // Выполнится даже при cancellation
        resource.close()
    }
}
```

## Error Handling

### Try-Catch в Coroutines

```kotlin
scope.launch {
    try {
        val data = loadData()
        processData(data)
        
    } catch (e: CancellationException) {
        // НЕ ловите! Re-throw
        throw e
        
    } catch (e: IOException) {
        withContext(Dispatchers.EDT) {
            Messages.showErrorDialog(
                project,
                "Failed to load data: ${e.message}",
                "Error"
            )
        }
    }
}
```

### CoroutineExceptionHandler

```kotlin
val handler = CoroutineExceptionHandler { _, exception ->
    LOG.error("Coroutine failed", exception)
    
    ApplicationManager.getApplication().invokeLater {
        showErrorNotification(exception.message)
    }
}

val scope = CoroutineScope(
    SupervisorJob() + Dispatchers.Default + handler
)

scope.launch {
    throw RuntimeException("Oops") // Поймается handler
}
```

## Patterns для LG Plugin

### CLI Execution Service

```kotlin
@Service(Service.Level.PROJECT)
class LgCliService(
    private val project: Project,
    private val scope: CoroutineScope
) {
    
    suspend fun renderContext(template: String): String {
        return withContext(Dispatchers.IO) {
            val args = listOf(
                "listing-generator",
                "render",
                "ctx:$template",
                "--lib", "tiktoken",
                "--encoder", "cl100k_base",
                "--ctx-limit", "128000"
            )
            
            val commandLine = GeneralCommandLine(args)
                .withWorkDirectory(project.basePath)
            
            val handler = CapturingProcessHandler(commandLine)
            val output = handler.runProcess(120_000)
            
            if (output.exitCode != 0) {
                throw CliException(output.stderr)
            }
            
            output.stdout
        }
    }
    
    fun renderContextAsync(
        template: String,
        callback: (String) -> Unit
    ) {
        scope.launch {
            try {
                val result = withBackgroundProgress(
                    project,
                    "Generating context '$template'...",
                    cancellable = true
                ) {
                    renderContext(template)
                }
                
                withContext(Dispatchers.EDT) {
                    callback(result)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.EDT) {
                    showError(e)
                }
            }
        }
    }
    
    private fun showError(e: Exception) {
        Messages.showErrorDialog(
            project,
            e.message ?: "Unknown error",
            "Generation Failed"
        )
    }
}
```

### Data Loading Service

```kotlin
@Service(Service.Level.PROJECT)
class CatalogService(
    private val project: Project,
    private val scope: CoroutineScope
) {
    
    private val _sections = MutableStateFlow<List<String>>(emptyList())
    val sections: StateFlow<List<String>> = _sections.asStateFlow()
    
    private val _contexts = MutableStateFlow<List<String>>(emptyList())
    val contexts: StateFlow<List<String>> = _contexts.asStateFlow()
    
    init {
        // Автоматическая загрузка при создании
        scope.launch {
            loadData()
        }
    }
    
    suspend fun loadData() {
        coroutineScope {
            // Параллельная загрузка
            launch { loadSections() }
            launch { loadContexts() }
        }
    }
    
    private suspend fun loadSections() {
        val output = withContext(Dispatchers.IO) {
            project.service<LgCliService>().execute(
                listOf("listing-generator", "list", "sections")
            )
        }
        
        val data = Json.decodeFromString<SectionsResponse>(output)
        _sections.value = data.sections
    }
    
    private suspend fun loadContexts() {
        val output = withContext(Dispatchers.IO) {
            project.service<LgCliService>().execute(
                listOf("listing-generator", "list", "contexts")
            )
        }
        
        val data = Json.decodeFromString<ContextsResponse>(output)
        _contexts.value = data.contexts
    }
    
    fun reloadAsync() {
        scope.launch {
            loadData()
        }
    }
}
```

### UI Updates через Flow

```kotlin
class ControlPanelView(private val project: Project) : JPanel() {
    
    private val catalogService = project.service<CatalogService>()
    private val sectionsComboBox = ComboBox<String>()
    private val scope = CoroutineScope(SupervisorJob())
    
    init {
        // Подписаться на изменения
        scope.launch {
            catalogService.sections.collect { sections ->
                withContext(Dispatchers.EDT) {
                    updateSectionsUI(sections)
                }
            }
        }
        
        scope.launch {
            catalogService.contexts.collect { contexts ->
                withContext(Dispatchers.EDT) {
                    updateContextsUI(contexts)
                }
            }
        }
    }
    
    private fun updateSectionsUI(sections: List<String>) {
        sectionsComboBox.removeAllItems()
        sections.forEach { sectionsComboBox.addItem(it) }
    }
}
```

## Debouncing и Throttling

### Debounce (отложить выполнение)

```kotlin
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*

class SearchPanel {
    
    private val searchQuery = MutableStateFlow("")
    
    init {
        scope.launch {
            searchQuery
                .debounce(300) // 300ms задержка
                .collect { query ->
                    performSearch(query)
                }
        }
    }
    
    fun onSearchTextChanged(text: String) {
        searchQuery.value = text
    }
}
```

### Throttle (ограничить частоту)

```kotlin
import kotlinx.coroutines.flow.sample

class AutoSaveService {
    
    private val saveRequests = MutableSharedFlow<Unit>()
    
    init {
        scope.launch {
            saveRequests
                .sample(5000) // Максимум раз в 5 сек
                .collect {
                    performSave()
                }
        }
    }
    
    fun requestSave() {
        scope.launch {
            saveRequests.emit(Unit)
        }
    }
}
```

## ProcessCanceledException

**Специальное исключение** для отмены операций.

### Правила

```kotlin
import com.intellij.openapi.progress.ProcessCanceledException

try {
    riskyOperation()
    
} catch (e: ProcessCanceledException) {
    // ❌ НИКОГДА не ловите и не логируйте PCE!
    // ❌ НЕ: LOG.error("Cancelled", e)
    
    // ✅ Всегда re-throw
    throw e
    
} catch (e: Exception) {
    // Другие exceptions можно обрабатывать
    LOG.error("Operation failed", e)
}
```

**Почему:** PCE используется инфраструктурой для отмены. Если поймать и не пробросить, cancellation не сработает.

### В Coroutines

В coroutines вместо PCE используется `CancellationException`:

```kotlin
scope.launch {
    try {
        processData()
    } catch (e: CancellationException) {
        cleanup()
        throw e // Re-throw
    }
}
```

## Modality State (EDT operations)

При вызове EDT из background учитывайте модальные диалоги:

```kotlin
import com.intellij.openapi.application.ModalityState

// Background thread
Thread {
    val data = loadData()
    
    // Выполнить когда нет модальных диалогов
    ApplicationManager.getApplication().invokeLater(
        { updateUI(data) },
        ModalityState.nonModal()
    )
}.start()
```

**С Coroutines (автоматически):**

```kotlin
scope.launch {
    val data = loadData()
    
    withContext(Dispatchers.EDT) {
        // Modality учтён автоматически
        updateUI(data)
    }
}
```

## Background Tasks Best Practices

### 1. Используйте Coroutines для 2024.1+

```kotlin
// ✅ 2024.1+
scope.launch {
    val data = loadData()
}

// ❌ Legacy
object : Task.Backgroundable(...) {
    override fun run(indicator: ProgressIndicator) { }
}.queue()
```

### 2. Inject CoroutineScope в Services

```kotlin
// ✅ Автоматический lifecycle
@Service(Service.Level.PROJECT)
class MyService(
    private val scope: CoroutineScope
)

// ❌ Ручное управление
@Service(Service.Level.PROJECT)
class MyService {
    private val scope = CoroutineScope(SupervisorJob())
}
```

### 3. Используйте structured concurrency

```kotlin
// ✅ Child coroutines отменятся с parent
scope.launch {
    launch { task1() }
    launch { task2() }
    launch { task3() }
} // Если parent отменён → все дети отменятся

// ❌ Независимые coroutines (сложно управлять)
scope.launch { task1() }
scope.launch { task2() }
scope.launch { task3() }
```

### 4. Обрабатывайте cancellation

```kotlin
// ✅ Cleanup в finally
scope.launch {
    val resource = acquire()
    try {
        process(resource)
    } finally {
        resource.release() // Даже при отмене
    }
}

// ❌ Утечка ресурсов
scope.launch {
    val resource = acquire()
    process(resource)
    resource.release() // Не вызовется при отмене!
}
```

### 5. НЕ блокируйте coroutines

```kotlin
// ❌ Блокирует поток
scope.launch {
    Thread.sleep(1000) // Плохо!
    val data = loadData()
}

// ✅ Suspend function
scope.launch {
    delay(1000) // Не блокирует поток
    val data = loadData()
}
```

## Testing Background Tasks

### Test с Coroutines

```kotlin
import kotlinx.coroutines.test.*

class MyServiceTest : BasePlatformTestCase() {
    
    @Test
    fun testAsyncLoading() = runTest {
        val service = project.service<MyService>()
        
        val result = service.loadData()
        
        assertNotNull(result)
    }
}
```

### Test с Progress API

```kotlin
fun testBackgroundTask() {
    val task = object : Task.Backgroundable(
        project,
        "Test Task",
        false
    ) {
        var result: String? = null
        
        override fun run(indicator: ProgressIndicator) {
            result = processData()
        }
    }
    
    ProgressManager.getInstance()
        .run(task)
    
    assertNotNull(task.result)
}
```

## Common Patterns

### Periodic Background Task

```kotlin
@Service(Service.Level.PROJECT)
class PeriodicUpdateService(
    private val project: Project,
    private val scope: CoroutineScope
) {
    
    init {
        // Запустить периодическую задачу
        scope.launch {
            while (true) {
                delay(60_000) // 1 минута
                
                try {
                    updateData()
                } catch (e: Exception) {
                    LOG.error("Periodic update failed", e)
                }
            }
        }
    }
    
    private suspend fun updateData() {
        val data = fetchLatestData()
        
        withContext(Dispatchers.EDT) {
            notifyDataUpdated(data)
        }
    }
    
    companion object {
        private val LOG = logger<PeriodicUpdateService>()
    }
}
```

### Batch Processing с Progress

```kotlin
suspend fun processBatch(items: List<Item>) {
    withBackgroundProgress(
        project,
        "Processing ${items.size} items...",
        cancellable = true
    ) {
        reportProgress { reporter ->
            for ((index, item) in items.withIndex()) {
                reporter.text("Processing ${item.name}...")
                reporter.fraction((index + 1).toDouble() / items.size)
                
                processItem(item)
            }
        }
    }
}
```

### Cache с async loading

```kotlin
@Service
class CacheService(private val scope: CoroutineScope) {
    
    private val cache = ConcurrentHashMap<String, Deferred<Data>>()
    
    suspend fun getData(key: String): Data {
        return cache.getOrPut(key) {
            scope.async(Dispatchers.IO) {
                loadData(key)
            }
        }.await()
    }
    
    fun invalidate(key: String) {
        cache.remove(key)
    }
    
    fun invalidateAll() {
        cache.clear()
    }
}
```
