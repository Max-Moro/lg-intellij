# External Processes и CLI Integration

## Обзор

IntelliJ Platform предоставляет API для выполнения внешних процессов:
- CLI утилиты
- Build tools (Gradle, Maven и т.д.)
- Linters, formatters
- Компиляторы
- Scripts (Python, Shell и т.д.)

## GeneralCommandLine

[`GeneralCommandLine`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-util-io/src/com/intellij/execution/configurations/GeneralCommandLine.java) — основной класс для построения команды.

### Базовое использование

```kotlin
import com.intellij.execution.configurations.GeneralCommandLine

val commandLine = GeneralCommandLine("listing-generator", "render", "sec:all")
    .withWorkDirectory(project.basePath)
    .withEnvironment("PYTHONUNBUFFERED", "1")
    .withCharset(Charsets.UTF_8)

val process = commandLine.createProcess()
```

### Builder Pattern

```kotlin
val commandLine = GeneralCommandLine()
    .withExePath("/path/to/listing-generator")
    .withParameters("render", "sec:all")
    .withParameters(listOf("--lib", "tiktoken"))
    .withWorkDirectory(project.basePath)
    .withEnvironment(mapOf(
        "PYTHONIOENCODING" to "utf-8",
        "PYTHONUTF8" to "1"
    ))
    .withParentEnvironmentType(
        GeneralCommandLine.ParentEnvironmentType.CONSOLE
    )
```

### Python/Java invocation

```kotlin
// Python module
val commandLine = GeneralCommandLine("python", "-m", "lg.cli", "render", "sec:all")

// Java -jar
val commandLine = GeneralCommandLine("java", "-jar", "tool.jar", "command")
```

## ProcessHandler

[`ProcessHandler`](https://github.com/JetBrains/intellij-community/blob/master/platform/util/src/com/intellij/execution/process/ProcessHandler.java) — обёртка над Process для управления и захвата вывода.

### OSProcessHandler

Стандартная реализация:

```kotlin
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessAdapter

val commandLine = GeneralCommandLine("listing-generator", "list", "sections")
val process = commandLine.createProcess()

val handler = OSProcessHandler(
    process,
    commandLine.commandLineString,
    Charsets.UTF_8
)

// Слушать события
handler.addProcessListener(object : ProcessAdapter() {
    override fun processTerminated(event: ProcessEvent) {
        val exitCode = event.exitCode
        println("Process finished with code: $exitCode")
    }
})

// Запустить захват вывода
handler.startNotify()

// Дождаться завершения
handler.waitFor()
```

### ColoredProcessHandler

Для вывода с ANSI escape codes (цвета в консоли):

```kotlin
import com.intellij.execution.process.ColoredProcessHandler

val handler = ColoredProcessHandler(commandLine)
handler.startNotify()
```

## Capturing Output

### CapturingProcessHandler

Для захвата stdout/stderr:

```kotlin
import com.intellij.execution.process.CapturingProcessHandler

val commandLine = GeneralCommandLine("listing-generator", "render", "sec:all")
val handler = CapturingProcessHandler(commandLine)

// Выполнить с timeout
val output = handler.runProcess(30_000) // 30 sec timeout

if (output.exitCode == 0) {
    val stdout = output.stdout
    val stderr = output.stderr
    
    println("Success!")
    println("Output: $stdout")
} else {
    println("Failed with code: ${output.exitCode}")
    println("Error: ${output.stderr}")
}
```

**ProcessOutput properties:**
- `exitCode: Int` — код возврата
- `stdout: String` — стандартный вывод
- `stderr: String` — вывод ошибок
- `isTimeout: Boolean` — был ли timeout
- `isCancelled: Boolean` — была ли отмена

### ScriptRunnerUtil (simplified)

```kotlin
import com.intellij.execution.process.ScriptRunnerUtil

val output = ScriptRunnerUtil.getProcessOutput(
    GeneralCommandLine("ls", "-la"),
    project.basePath,
    30_000 // timeout ms
)

val stdout = output.stdout
```

## Running with Progress

### Background Task с Process

```kotlin
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task

class GenerateListingAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        object : Task.Backgroundable(
            project,
            "Generating Listing...",
            true // cancellable
        ) {
            override fun run(indicator: ProgressIndicator) {
                val commandLine = GeneralCommandLine(
                    "listing-generator",
                    "render",
                    "sec:all"
                ).withWorkDirectory(project.basePath)
                
                val handler = CapturingProcessHandler(commandLine)
                
                // Отменяемость
                indicator.checkCanceled()
                
                val output = handler.runProcess()
                
                if (output.exitCode == 0) {
                    ApplicationManager.getApplication().invokeLater {
                        showResult(project, output.stdout)
                    }
                } else {
                    throw RuntimeException(output.stderr)
                }
            }
            
            override fun onThrowable(error: Throwable) {
                // Показать ошибку
                Messages.showErrorDialog(
                    project,
                    error.message,
                    "Generation Failed"
                )
            }
        }.queue()
    }
    
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
```

## Kotlin Coroutines для Processes

### Suspend wrapper

```kotlin
@Service(Service.Level.PROJECT)
class CliService(private val project: Project) {
    
    suspend fun executeCommand(args: List<String>): String {
        return withContext(Dispatchers.IO) {
            val commandLine = GeneralCommandLine(args)
                .withWorkDirectory(project.basePath)
                .withEnvironment("PYTHONIOENCODING", "utf-8")
            
            val handler = CapturingProcessHandler(commandLine)
            val output = handler.runProcess(60_000)
            
            if (output.isTimeout) {
                throw TimeoutException("Command timeout")
            }
            
            if (output.exitCode != 0) {
                throw CliException(
                    "Command failed with code ${output.exitCode}",
                    output.stderr
                )
            }
            
            output.stdout
        }
    }
}

// Использование
scope.launch {
    try {
        val result = cliService.executeCommand(
            listOf("listing-generator", "list", "sections")
        )
        
        val sections = parseJson(result)
        
        withContext(Dispatchers.EDT) {
            updateUI(sections)
        }
    } catch (e: CliException) {
        withContext(Dispatchers.EDT) {
            showError(e.message)
        }
    }
}
```

### Cancellable execution

```kotlin
suspend fun executeCommandCancellable(
    args: List<String>
): String = coroutineScope {
    withContext(Dispatchers.IO) {
        val commandLine = GeneralCommandLine(args)
        val handler = OSProcessHandler(commandLine)
        
        // Слушать cancellation
        val job = coroutineContext.job
        job.invokeOnCompletion {
            if (job.isCancelled) {
                handler.destroyProcess() // Kill process
            }
        }
        
        val output = StringBuilder()
        val errors = StringBuilder()
        
        handler.addProcessListener(object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                when (outputType) {
                    ProcessOutputTypes.STDOUT -> output.append(event.text)
                    ProcessOutputTypes.STDERR -> errors.append(event.text)
                }
            }
        })
        
        handler.startNotify()
        handler.waitFor()
        
        if (handler.exitCode != 0) {
            throw CliException("Failed", errors.toString())
        }
        
        output.toString()
    }
}
```

## Process with Real-time Output

### Console View для вывода

```kotlin
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView

fun runWithConsole(project: Project) {
    // Создать console view
    val consoleView = TextConsoleBuilderFactory.getInstance()
        .createBuilder(project)
        .console
    
    // Создать process
    val commandLine = GeneralCommandLine("listing-generator", "render", "sec:all")
    val handler = OSProcessHandler(commandLine)
    
    // Подключить console к process
    consoleView.attachToProcess(handler)
    
    // Показать в tool window
    val toolWindow = ToolWindowManager.getInstance(project)
        .getToolWindow("LG Output")
    
    val content = ContentFactory.getInstance()
        .createContent(consoleView.component, "Generation", true)
    
    toolWindow?.contentManager?.addContent(content)
    toolWindow?.show()
    
    // Запустить process
    handler.startNotify()
}
```

### ProcessListener для real-time updates

```kotlin
val handler = OSProcessHandler(commandLine)

handler.addProcessListener(object : ProcessAdapter() {
    
    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        val text = event.text
        
        when (outputType) {
            ProcessOutputTypes.STDOUT -> {
                println("OUT: $text")
                updateProgressText(text)
            }
            ProcessOutputTypes.STDERR -> {
                println("ERR: $text")
            }
            ProcessOutputTypes.SYSTEM -> {
                println("SYS: $text")
            }
        }
    }
    
    override fun processTerminated(event: ProcessEvent) {
        val exitCode = event.exitCode
        
        ApplicationManager.getApplication().invokeLater {
            if (exitCode == 0) {
                showSuccess()
            } else {
                showError("Process failed with code: $exitCode")
            }
        }
    }
    
    override fun processWillTerminate(
        event: ProcessEvent,
        willBeDestroyed: Boolean
    ) {
        println("Process terminating...")
    }
})

handler.startNotify()
```

## Process Termination

### Graceful termination

```kotlin
val handler = OSProcessHandler(commandLine)
handler.startNotify()

// Мягкая остановка
handler.destroyProcess()

// Или отправить interrupt
if (!handler.isProcessTerminated) {
    val process = handler.process
    process.destroy() // SIGTERM
}

// Жёсткая остановка (kill)
if (!handler.isProcessTerminated) {
    val process = handler.process
    process.destroyForcibly() // SIGKILL
}
```

### Timeout с автоматической остановкой

```kotlin
val handler = OSProcessHandler(commandLine)
handler.startNotify()

// Дождаться с timeout
val finished = handler.waitFor(30_000) // 30 sec

if (!finished) {
    // Timeout — убить process
    handler.destroyProcess()
    throw TimeoutException("Process timeout")
}
```

## stdin/stdout/stderr

### Запись в stdin

```kotlin
val commandLine = GeneralCommandLine("listing-generator", "render", "--task", "-")
val handler = OSProcessHandler(commandLine)

handler.addProcessListener(object : ProcessAdapter() {
    override fun startNotified(event: ProcessEvent) {
        // Process запущен — можно писать в stdin
        val stdin = handler.processInput
        
        stdin.write("Task description here\n".toByteArray())
        stdin.flush()
        stdin.close() // Закрыть stdin
    }
})

handler.startNotify()
```

### Обработка stderr отдельно

```kotlin
val handler = OSProcessHandler(commandLine)

val stdoutBuilder = StringBuilder()
val stderrBuilder = StringBuilder()

handler.addProcessListener(object : ProcessAdapter() {
    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        when (outputType) {
            ProcessOutputTypes.STDOUT -> stdoutBuilder.append(event.text)
            ProcessOutputTypes.STDERR -> stderrBuilder.append(event.text)
        }
    }
    
    override fun processTerminated(event: ProcessEvent) {
        if (event.exitCode != 0) {
            showError("Error output:\n${stderrBuilder}")
        }
    }
})

handler.startNotify()
```

## Environment Variables

### Установка переменных окружения

```kotlin
val commandLine = GeneralCommandLine("listing-generator")
    .withEnvironment(mapOf(
        "PYTHONUNBUFFERED" to "1",
        "PYTHONIOENCODING" to "utf-8",
        "LG_DEBUG" to "true"
    ))

// Или добавлять по одной
commandLine.environment["PATH"] = "/custom/path:${System.getenv("PATH")}"
```

### Parent Environment

```kotlin
// Наследовать от родительского процесса (default)
commandLine.withParentEnvironmentType(
    GeneralCommandLine.ParentEnvironmentType.CONSOLE
)

// Не наследовать
commandLine.withParentEnvironmentType(
    GeneralCommandLine.ParentEnvironmentType.NONE
)

// Наследовать системные
commandLine.withParentEnvironmentType(
    GeneralCommandLine.ParentEnvironmentType.SYSTEM
)
```

## Working Directory

```kotlin
// Установить work directory
commandLine.withWorkDirectory(project.basePath)

// Или через File
commandLine.workDirectory = File(project.basePath)

// Или через Path
commandLine.withWorkDirectory(Path.of(project.basePath!!))
```

## CLI Wrapper Service Pattern

### Полный пример для LG CLI

```kotlin
@Service(Service.Level.PROJECT)
class LgCliService(
    private val project: Project,
    private val scope: CoroutineScope
) {
    
    /**
     * Выполняет CLI команду и возвращает stdout.
     */
    suspend fun execute(args: List<String>): String {
        return withContext(Dispatchers.IO) {
            val commandLine = buildCommandLine(args)
            val handler = CapturingProcessHandler(commandLine)
            
            val output = handler.runProcess(120_000) // 2 min timeout
            
            if (output.isTimeout) {
                throw CliTimeoutException("Command timeout after 2 minutes")
            }
            
            if (output.exitCode != 0) {
                throw CliException(
                    "Command failed with exit code ${output.exitCode}",
                    output.stderr
                )
            }
            
            output.stdout
        }
    }
    
    /**
     * Выполняет команду с stdin данными.
     */
    suspend fun executeWithStdin(
        args: List<String>,
        stdinData: String
    ): String {
        return withContext(Dispatchers.IO) {
            val commandLine = buildCommandLine(args)
            val process = commandLine.createProcess()
            
            // Записать в stdin
            process.outputStream.use { stdin ->
                stdin.write(stdinData.toByteArray(Charsets.UTF_8))
            }
            
            // Захватить вывод
            val handler = OSProcessHandler(
                process,
                commandLine.commandLineString,
                Charsets.UTF_8
            )
            
            val output = StringBuilder()
            val errors = StringBuilder()
            
            handler.addProcessListener(object : ProcessAdapter() {
                override fun onTextAvailable(
                    event: ProcessEvent,
                    outputType: Key<*>
                ) {
                    when (outputType) {
                        ProcessOutputTypes.STDOUT -> output.append(event.text)
                        ProcessOutputTypes.STDERR -> errors.append(event.text)
                    }
                }
            })
            
            handler.startNotify()
            handler.waitFor(120_000)
            
            if (handler.exitCode != 0) {
                throw CliException(
                    "Command failed",
                    errors.toString()
                )
            }
            
            output.toString()
        }
    }
    
    private fun buildCommandLine(args: List<String>): GeneralCommandLine {
        val cliPath = resolveCliPath()
        
        return GeneralCommandLine(cliPath)
            .withParameters(args)
            .withWorkDirectory(project.basePath)
            .withEnvironment(mapOf(
                "PYTHONUNBUFFERED" to "1",
                "PYTHONIOENCODING" to "utf-8"
            ))
            .withCharset(Charsets.UTF_8)
    }
    
    private fun resolveCliPath(): String {
        val settings = service<LgSettingsService>()
        
        // 1. Explicit path from settings
        val explicitPath = settings.state.cliPath
        if (explicitPath.isNotBlank()) {
            return explicitPath
        }
        
        // 2. Try to find in PATH
        return findInPath("listing-generator") 
            ?: throw CliNotFoundException("listing-generator not found in PATH")
    }
    
    private fun findInPath(command: String): String? {
        val pathVar = System.getenv("PATH") ?: return null
        val separator = if (SystemInfo.isWindows) ";" else ":"
        
        for (dir in pathVar.split(separator)) {
            val executable = File(dir, command).let {
                if (SystemInfo.isWindows) File(dir, "$command.exe") else it
            }
            
            if (executable.exists() && executable.canExecute()) {
                return executable.absolutePath
            }
        }
        
        return null
    }
}

// Exceptions
class CliException(
    message: String,
    val stderr: String = ""
) : Exception(message)

class CliTimeoutException(message: String) : Exception(message)
class CliNotFoundException(message: String) : Exception(message)
```

## Execution with Progress Indicator

### Reporting progress

```kotlin
object : Task.Backgroundable(project, "Executing CLI...", true) {
    
    override fun run(indicator: ProgressIndicator) {
        indicator.text = "Preparing command..."
        indicator.fraction = 0.1
        
        val commandLine = buildCommand()
        
        indicator.text = "Executing..."
        indicator.isIndeterminate = true // Неизвестная длительность
        
        val handler = OSProcessHandler(commandLine)
        
        // Парсить вывод для прогресса
        handler.addProcessListener(object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                // Если CLI выводит прогресс в stdout
                val text = event.text
                if (text.startsWith("Progress:")) {
                    val percent = parseProgress(text)
                    indicator.fraction = percent / 100.0
                    indicator.text = text
                }
            }
        })
        
        handler.startNotify()
        
        while (!handler.isProcessTerminated) {
            indicator.checkCanceled() // Проверка отмены
            Thread.sleep(100)
        }
        
        indicator.fraction = 1.0
    }
}.queue()
```

## Process Cancellation

### Через ProgressIndicator

```kotlin
object : Task.Backgroundable(project, "Running...", true) {
    
    private var processHandler: OSProcessHandler? = null
    
    override fun run(indicator: ProgressIndicator) {
        val commandLine = GeneralCommandLine("long-running-command")
        processHandler = OSProcessHandler(commandLine)
        
        processHandler?.startNotify()
        
        // Ждать с проверкой cancellation
        while (!processHandler!!.isProcessTerminated) {
            if (indicator.isCanceled) {
                processHandler?.destroyProcess() // Убить процесс
                throw ProcessCanceledException()
            }
            Thread.sleep(100)
        }
    }
    
    override fun onCancel() {
        // Вызывается при отмене через UI
        processHandler?.destroyProcess()
    }
}.queue()
```

## Terminal Integration

### Запуск команды в Terminal

```kotlin
import com.intellij.terminal.TerminalExecutionConsole
import org.jetbrains.plugins.terminal.ShellTerminalWidget

fun runInTerminal(project: Project, command: String) {
    val terminalView = TerminalView.getInstance(project)
    
    // Создать новую вкладку terminal
    val widget = terminalView.createLocalShellWidget(
        project.basePath,
        "LG CLI"
    )
    
    // Выполнить команду
    widget.executeCommand(command)
}

// Использование
runInTerminal(project, "listing-generator render sec:all")
```

## Console Filters

Для кликабельных ссылок в выводе процесса.

### File Path Filter

```kotlin
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo

class FilePathFilter(private val project: Project) : Filter {
    
    override fun applyFilter(
        line: String,
        entireLength: Int
    ): Filter.Result? {
        // Найти паттерн файлового пути
        val regex = Regex("""(\S+\.py):(\d+)""")
        val match = regex.find(line) ?: return null
        
        val path = match.groupValues[1]
        val lineNumber = match.groupValues[2].toInt()
        
        val file = LocalFileSystem.getInstance()
            .findFileByPath("${project.basePath}/$path") ?: return null
        
        val hyperlinkInfo = object : HyperlinkInfo {
            override fun navigate(project: Project) {
                OpenFileDescriptor(
                    project,
                    file,
                    lineNumber - 1,
                    0
                ).navigate(true)
            }
        }
        
        val start = entireLength - line.length + match.range.first
        val end = start + match.value.length
        
        return Filter.Result(start, end, hyperlinkInfo)
    }
}

// Использование
val consoleView = TextConsoleBuilderFactory.getInstance()
    .createBuilder(project)
    .addFilter(FilePathFilter(project))
    .console
```

## Python Script Execution

```kotlin
fun executePythonScript(
    project: Project,
    scriptPath: String,
    args: List<String>
): String {
    // Найти Python interpreter
    val pythonPath = findPythonInterpreter() ?: "python3"
    
    val commandLine = GeneralCommandLine()
        .withExePath(pythonPath)
        .withParameters("-u") // Unbuffered
        .withParameters(scriptPath)
        .withParameters(args)
        .withWorkDirectory(project.basePath)
        .withEnvironment(mapOf(
            "PYTHONUNBUFFERED" to "1",
            "PYTHONIOENCODING" to "utf-8"
        ))
    
    val handler = CapturingProcessHandler(commandLine)
    val output = handler.runProcess(60_000)
    
    if (output.exitCode != 0) {
        throw RuntimeException(output.stderr)
    }
    
    return output.stdout
}

private fun findPythonInterpreter(): String? {
    // Проверить популярные locations
    val candidates = listOf("python3", "python", "py")
    
    for (cmd in candidates) {
        try {
            val testCmd = GeneralCommandLine(cmd, "--version")
            val output = CapturingProcessHandler(testCmd).runProcess(5_000)
            
            if (output.exitCode == 0) {
                return cmd
            }
        } catch (e: Exception) {
            continue
        }
    }
    
    return null
}
```

## CLI Detection Pattern

```kotlin
@Service
class CliDetectionService {
    
    private var cachedCliPath: String? = null
    
    fun detectCli(): String? {
        if (cachedCliPath != null) {
            return cachedCliPath
        }
        
        // 1. Check settings
        val settings = service<LgSettingsService>()
        if (settings.state.cliPath.isNotBlank()) {
            val path = settings.state.cliPath
            if (isExecutable(path)) {
                cachedCliPath = path
                return path
            }
        }
        
        // 2. Check PATH
        val inPath = findInPath("listing-generator")
        if (inPath != null) {
            cachedCliPath = inPath
            return inPath
        }
        
        // 3. Check common locations
        val commonPaths = listOf(
            System.getProperty("user.home") + "/.local/bin/listing-generator",
            "/usr/local/bin/listing-generator"
        )
        
        for (path in commonPaths) {
            if (isExecutable(path)) {
                cachedCliPath = path
                return path
            }
        }
        
        return null
    }
    
    private fun isExecutable(path: String): Boolean {
        val file = File(path)
        return file.exists() && file.canExecute()
    }
    
    private fun findInPath(command: String): String? {
        // См. пример выше
    }
    
    fun clearCache() {
        cachedCliPath = null
    }
}
```

## Error Handling

### Structured error reporting

```kotlin
sealed class CliResult<out T> {
    data class Success<T>(val value: T) : CliResult<T>()
    data class Failure(
        val exitCode: Int,
        val stderr: String,
        val stdout: String
    ) : CliResult<Nothing>()
    data class Timeout(val duration: Long) : CliResult<Nothing>()
}

suspend fun executeCommandSafe(args: List<String>): CliResult<String> {
    return withContext(Dispatchers.IO) {
        try {
            val commandLine = GeneralCommandLine(args)
            val handler = CapturingProcessHandler(commandLine)
            val output = handler.runProcess(60_000)
            
            when {
                output.isTimeout -> CliResult.Timeout(60_000)
                output.exitCode != 0 -> CliResult.Failure(
                    output.exitCode,
                    output.stderr,
                    output.stdout
                )
                else -> CliResult.Success(output.stdout)
            }
        } catch (e: Exception) {
            CliResult.Failure(-1, e.message ?: "Unknown error", "")
        }
    }
}

// Использование
when (val result = executeCommandSafe(args)) {
    is CliResult.Success -> {
        processOutput(result.value)
    }
    is CliResult.Failure -> {
        showError("CLI failed (code ${result.exitCode}): ${result.stderr}")
    }
    is CliResult.Timeout -> {
        showError("CLI timeout after ${result.duration}ms")
    }
}
```

## JSON Parsing from CLI

```kotlin
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

@Serializable
data class CliListResponse(
    val sections: List<String>
)

suspend fun listSections(): List<String> {
    val output = cliService.execute(listOf(
        "listing-generator",
        "list",
        "sections"
    ))
    
    val response = Json.decodeFromString<CliListResponse>(output)
    return response.sections
}
```

## Best Practices

### 1. Всегда используйте timeout

```kotlin
// ✅ С timeout
val output = handler.runProcess(60_000)

// ❌ Без timeout (может зависнуть навсегда)
handler.waitFor()
```

### 2. Обрабатывайте cancellation

```kotlin
// ✅ Cancellable
object : Task.Backgroundable(project, "Running...", true) {
    override fun run(indicator: ProgressIndicator) {
        while (!handler.isProcessTerminated) {
            indicator.checkCanceled()
            Thread.sleep(100)
        }
    }
}
```

### 3. UTF-8 encoding

```kotlin
// ✅ Явно указывайте encoding
val commandLine = GeneralCommandLine(...)
    .withCharset(Charsets.UTF_8)
    .withEnvironment("PYTHONIOENCODING", "utf-8")
```

### 4. Логируйте команды

```kotlin
import com.intellij.openapi.diagnostic.logger

private val LOG = logger<CliService>()

fun execute(args: List<String>): String {
    LOG.info("Executing: ${args.joinToString(" ")}")
    
    val output = runCommand(args)
    
    LOG.info("Command completed, output length: ${output.length}")
    return output
}
```

### 5. Graceful degradation

```kotlin
fun getCliVersion(): String? {
    return try {
        val output = executeCommand(listOf("listing-generator", "--version"))
        parseVersion(output)
    } catch (e: Exception) {
        LOG.warn("Failed to get CLI version", e)
        null // Gracefully handle
    }
}
```

## Testing External Processes

### Mock CLI в тестах

```kotlin
class MockCliService : CliService {
    
    override suspend fun execute(args: List<String>): String {
        // Эмулируем вывод без реального процесса
        return when {
            "list" in args && "sections" in args -> 
                """{"sections": ["all", "core", "tests"]}"""
            
            "render" in args ->
                "# Generated Listing\n\nContent here"
            
            else -> 
                throw IllegalArgumentException("Unknown command: $args")
        }
    }
}

// В тестах
class CliServiceTest : BasePlatformTestCase() {
    
    private lateinit var cliService: MockCliService
    
    override fun setUp() {
        super.setUp()
        cliService = MockCliService()
    }
    
    fun testListSections() = runBlocking {
        val output = cliService.execute(listOf("list", "sections"))
        assertTrue(output.contains("sections"))
    }
}
```

## Platform Examples

### Maven Integration

См. как Maven plugin выполняет Maven:
```kotlin
// org.jetbrains.idea.maven.execution.MavenRunnerParameters
```

### Gradle Integration

См. как Gradle plugin выполняет Gradle:
```kotlin
// org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper
```

### Git Integration

См. как Git plugin выполняет git команды:
```kotlin
// git4idea.commands.GitCommand
```
