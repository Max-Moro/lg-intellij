# Testing IntelliJ Platform Plugins

## Обзор

IntelliJ Platform предоставляет мощный фреймворк для тестирования плагинов:
- **Unit tests** — изолированное тестирование логики
- **Integration tests** — тестирование с платформой
- **UI tests** — автоматизация UI
- **Performance tests** — измерение производительности

## Test Framework

### Gradle Dependencies

```kotlin
dependencies {
    intellijPlatform {
        // Test framework
        testFramework(TestFrameworkType.Platform)
        
        // Для JUnit 5
        testFramework(TestFrameworkType.Platform.JUnit5)
        
        // Plugin для тестирования
        bundledPlugin("com.intellij.java")
    }
    
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}
```

### Test Source Sets

```
src/
  main/
    kotlin/
  test/
    kotlin/           # Тесты
      MyServiceTest.kt
    resources/
      testData/       # Тестовые данные (файлы, проекты)
        sample.yaml
        test-project/
```

## BasePlatformTestCase

Базовый класс для большинства тестов.

### Простой тест

```kotlin
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MyServiceTest : BasePlatformTestCase() {
    
    private lateinit var service: MyService
    
    override fun setUp() {
        super.setUp()
        service = project.service()
    }
    
    fun testServiceMethod() {
        val result = service.processData("test")
        
        assertNotNull(result)
        assertEquals("expected", result.value)
    }
}
```

### Available Properties

```kotlin
class MyTest : BasePlatformTestCase() {
    
    fun testWithFixtures() {
        // Project
        val project: Project = project
        
        // Test fixture
        val fixture: CodeInsightTestFixture = myFixture
        
        // Module
        val module: Module = module
        
        // Test data path
        val testDataPath = testDataPath
    }
    
    override fun getTestDataPath(): String {
        return "src/test/resources/testData"
    }
}
```

## Testing Services

```kotlin
class LgCliServiceTest : BasePlatformTestCase() {
    
    private lateinit var cliService: LgCliService
    
    override fun setUp() {
        super.setUp()
        cliService = project.service()
    }
    
    fun testListSections() = runBlocking {
        // Mock или real CLI
        val sections = cliService.listSections()
        
        assertNotEmpty(sections)
        assertTrue("all" in sections)
    }
    
    fun testRenderSection() = runBlocking {
        val output = cliService.renderSection("all")
        
        assertNotNull(output)
        assertTrue(output.contains("FILE:"))
    }
}
```

## Testing Actions

```kotlin
class GenerateActionTest : BasePlatformTestCase() {
    
    fun testActionEnabled() {
        val action = GenerateListingAction()
        
        // Create test event
        val event = testActionEvent()
        
        // Test update()
        action.update(event)
        
        assertTrue(event.presentation.isEnabled)
        assertTrue(event.presentation.isVisible)
    }
    
    fun testActionPerformed() {
        // Setup test file
        myFixture.configureByText(
            "test.py",
            "def hello(): pass"
        )
        
        val action = GenerateListingAction()
        val event = testActionEvent()
        
        // Perform action
        action.actionPerformed(event)
        
        // Verify result
        // ...
    }
    
    private fun testActionEvent(): AnActionEvent {
        return AnActionEvent.createFromDataContext(
            ActionPlaces.UNKNOWN,
            null,
            createDataContext()
        )
    }
    
    private fun createDataContext(): DataContext {
        return SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.EDITOR, myFixture.editor)
            .add(CommonDataKeys.PSI_FILE, myFixture.file)
            .build()
    }
}
```

## Testing UI Components

### Tool Window Factory

```kotlin
class ToolWindowFactoryTest : BasePlatformTestCase() {
    
    fun testToolWindowCreation() {
        val factory = LgToolWindowFactory()
        
        // Create mock tool window
        val toolWindow = ToolWindowManager.getInstance(project)
            .registerToolWindow(
                RegisterToolWindowTask(
                    id = "Test Tool Window"
                )
            )
        
        // Test factory
        factory.createToolWindowContent(project, toolWindow)
        
        // Verify
        val content = toolWindow.contentManager.getContent(0)
        assertNotNull(content)
        
        val component = content?.component
        assertInstanceOf(component, LgToolWindowPanel::class.java)
    }
}
```

### Configurable (Settings)

```kotlin
class SettingsConfigurableTest : BasePlatformTestCase() {
    
    fun testConfigurableUI() {
        val configurable = LgSettingsConfigurable()
        val panel = configurable.createPanel()
        
        assertNotNull(panel)
        
        // Verify not modified initially
        assertFalse(configurable.isModified)
        
        // Modify через UI
        // (найти component и изменить)
        val textField = UIUtil.findComponentOfType(
            panel,
            JBTextField::class.java
        )
        textField?.text = "new value"
        
        // Verify modified
        assertTrue(configurable.isModified)
        
        // Apply
        configurable.apply()
        
        // Verify settings updated
        val settings = LgSettingsService.getInstance()
        assertEquals("new value", settings.state.someField)
    }
}
```

## Testing with Test Data

### Creating Test Files

```kotlin
class FileProcessingTest : BasePlatformTestCase() {
    
    fun testProcessYamlFile() {
        // Create test file
        val file = myFixture.configureByText(
            "sections.yaml",
            """
            all:
              extensions: [".py", ".md"]
              filters:
                mode: block
            """.trimIndent()
        )
        
        // Test processing
        val result = processYamlFile(file)
        
        assertNotNull(result)
    }
    
    fun testProcessFromTestData() {
        // Load from testData directory
        myFixture.configureByFile("testData/sample-sections.yaml")
        
        val psiFile = myFixture.file
        
        // Test
        val result = processPsiFile(psiFile)
        assertNotNull(result)
    }
    
    override fun getTestDataPath() = "src/test/resources/testData"
}
```

### Test Data Directory Structure

```
test/
  resources/
    testData/
      sample-sections.yaml
      sample-context.md
      test-project/
        lg-cfg/
          sections.yaml
        src/
          main.py
```

## Light и Heavy Tests

### Light Tests (быстрые)

Используют минимальный setup (нет реального проекта на диске):

```kotlin
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LightTest : BasePlatformTestCase() {
    // Light test — project в памяти
}
```

### Heavy Tests (медленные)

Создают реальный проект на диске:

```kotlin
import com.intellij.testFramework.HeavyPlatformTestCase

class HeavyTest : HeavyPlatformTestCase() {
    // Heavy test — реальный проект на диске
}
```

**Когда использовать Heavy:**
- Тестирование VFS операций с реальными файлами
- Тестирование external tools
- Integration tests

## Async Testing (Coroutines)

```kotlin
import kotlinx.coroutines.test.runTest

class AsyncServiceTest : BasePlatformTestCase() {
    
    fun testAsyncMethod() = runTest {
        val service = project.service<MyAsyncService>()
        
        val result = service.loadDataAsync()
        
        assertNotNull(result)
    }
}
```

## Testing Background Tasks

### Progress API Test

```kotlin
fun testBackgroundTask() {
    var taskCompleted = false
    var taskResult: String? = null
    
    val task = object : Task.Backgroundable(
        project,
        "Test Task",
        false
    ) {
        override fun run(indicator: ProgressIndicator) {
            indicator.text = "Processing..."
            taskResult = performWork()
        }
        
        override fun onSuccess() {
            taskCompleted = true
        }
    }
    
    ProgressManager.getInstance().run(task)
    
    assertTrue(taskCompleted)
    assertEquals("expected", taskResult)
}
```

### Kotlin Coroutines Test

```kotlin
import kotlinx.coroutines.test.*

class CoroutineServiceTest : BasePlatformTestCase() {
    
    fun testCoroutineExecution() = runTest {
        val service = MyService(project, this)
        
        service.executeTask()
        
        // Verify
        assertTrue(service.isCompleted())
    }
}
```

## Mocking

### Mock Service в тестах

```kotlin
// Test service implementation
class MockCliService : LgCliService {
    
    private val responses = mutableMapOf<List<String>, String>()
    
    fun mockResponse(args: List<String>, response: String) {
        responses[args] = response
    }
    
    override suspend fun execute(args: List<String>): String {
        return responses[args] 
            ?: throw IllegalArgumentException("No mock for: $args")
    }
}

// В тесте
class IntegrationTest : BasePlatformTestCase() {
    
    private lateinit var mockCli: MockCliService
    
    override fun setUp() {
        super.setUp()
        
        mockCli = MockCliService()
        
        // Replace service (через testServiceImplementation)
    }
    
    fun testWithMock() {
        mockCli.mockResponse(
            listOf("list", "sections"),
            """{"sections": ["all", "core"]}"""
        )
        
        // Test code using CLI
    }
}
```

### Mock через testServiceImplementation

```xml
<extensions defaultExtensionNs="com.intellij">
    <applicationService 
        serviceInterface="com.example.CliService"
        serviceImplementation="com.example.CliServiceImpl"
        testServiceImplementation="com.example.MockCliService"/>
</extensions>
```

В тестах автоматически используется `MockCliService`.

## Testing VFS Operations

```kotlin
class VfsOperationsTest : BasePlatformTestCase() {
    
    fun testCreateFile() {
        val baseDir = project.baseDir!!
        
        writeAction {
            val newFile = baseDir.createChildData(null, "test.txt")
            newFile.setBinaryContent("content".toByteArray())
        }
        
        val file = baseDir.findChild("test.txt")
        assertNotNull(file)
        assertEquals("content", String(file!!.contentsToByteArray()))
    }
    
    fun testFileListener() {
        var eventReceived = false
        
        project.messageBus.connect(testRootDisposable)
            .subscribe(
                VirtualFileManager.VFS_CHANGES,
                object : BulkFileListener {
                    override fun after(events: List<VFileEvent>) {
                        eventReceived = true
                    }
                }
            )
        
        writeAction {
            project.baseDir!!.createChildData(null, "new.txt")
        }
        
        assertTrue(eventReceived)
    }
}
```

## Testing Notifications

```kotlin
class NotificationTest : BasePlatformTestCase() {
    
    private val notifications = mutableListOf<Notification>()
    
    override fun setUp() {
        super.setUp()
        
        project.messageBus.connect(testRootDisposable)
            .subscribe(
                Notifications.TOPIC,
                object : Notifications {
                    override fun notify(notification: Notification) {
                        notifications.add(notification)
                    }
                }
            )
    }
    
    fun testSuccessNotification() {
        // Trigger notification
        notifySuccess(project, "Test success")
        
        assertEquals(1, notifications.size)
        
        val notification = notifications.first()
        assertEquals("Test success", notification.content)
        assertEquals(NotificationType.INFORMATION, notification.type)
    }
}
```

## Testing Settings Persistence

```kotlin
class PersistenceTest : BasePlatformTestCase() {
    
    fun testStatePersistence() {
        val settings = LgSettingsService.getInstance()
        
        // Modify state
        settings.state.cliPath = "/test/path"
        settings.state.timeout = 60
        
        // Serialize
        val element = XmlSerializer.serialize(settings.state)
        assertNotNull(element)
        
        // Deserialize into new instance
        val newState = XmlSerializer.deserialize(
            element,
            LgSettingsService.State::class.java
        )
        
        // Verify
        assertEquals("/test/path", newState.cliPath)
        assertEquals(60, newState.timeout)
    }
}
```

## Testing External Processes

### Mock Process Handler

```kotlin
class MockProcessHandler : ProcessHandler() {
    
    private val stdout = mutableListOf<String>()
    private var exitCode = 0
    
    fun mockOutput(text: String) {
        stdout.add(text)
    }
    
    fun mockExitCode(code: Int) {
        exitCode = code
    }
    
    override fun startNotify() {
        super.startNotify()
        
        // Emit stdout
        for (line in stdout) {
            notifyTextAvailable(line + "\n", ProcessOutputTypes.STDOUT)
        }
        
        // Terminate
        notifyProcessTerminated(exitCode)
    }
    
    override fun destroyProcessImpl() { }
    
    override fun detachProcessImpl() { }
    
    override fun detachIsDefault() = false
    
    override fun getProcessInput(): OutputStream? = null
}

// В тесте
fun testCliExecution() {
    val mockHandler = MockProcessHandler()
    mockHandler.mockOutput("""{"sections": ["all"]}""")
    mockHandler.mockExitCode(0)
    
    mockHandler.startNotify()
    
    // Test processing
}
```

## Test Fixtures

### CodeInsightTestFixture

```kotlin
class CodeInsightTest : BasePlatformTestCase() {
    
    fun testCodeCompletion() {
        // Configure test file
        myFixture.configureByText(
            "test.py",
            """
            def hello():
                pri<caret>
            """.trimIndent()
        )
        
        // Trigger completion
        myFixture.completeBasic()
        
        // Verify
        val lookupStrings = myFixture.lookupElementStrings
        assertContainsElements(lookupStrings, "print")
    }
    
    fun testIntention() {
        myFixture.configureByText(
            "test.py",
            "x = <caret>1"
        )
        
        val intentions = myFixture.availableIntentions
        
        assertNotEmpty(intentions)
    }
}
```

## UI Testing

### RemoteRobot (UI Automation)

```kotlin
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.*

class UiTest {
    
    private val robot = RemoteRobot("http://127.0.0.1:8082")
    
    @Test
    fun testToolWindow() {
        robot.find<ComponentFixture>(
            byXpath("//div[@class='ToolWindowHeader'][@text='Listing Generator']")
        ).click()
        
        val toolWindow = robot.find<CommonContainerFixture>(
            byXpath("//div[@class='Content']")
        )
        
        toolWindow.button("Generate").click()
        
        // Verify
        assertTrue(toolWindow.hasText("Generated successfully"))
    }
}
```

Настройка в `build.gradle.kts`:

```kotlin
intellijPlatform {
    testFramework(TestFrameworkType.Platform)
}
```

## Test Data Files

### Копирование testData в test

```kotlin
class TestDataTest : BasePlatformTestCase() {
    
    fun testProcessTestFile() {
        // Copy from testData
        myFixture.copyFileToProject(
            "sample-sections.yaml",      // From testData/
            "lg-cfg/sections.yaml"        // To project
        )
        
        // Find in project
        val file = LocalFileSystem.getInstance()
            .findFileByPath("${project.basePath}/lg-cfg/sections.yaml")
        
        assertNotNull(file)
    }
    
    override fun getTestDataPath() = "src/test/resources/testData"
}
```

## Parametrized Tests

### JUnit 5 @ParameterizedTest

```kotlin
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ParametrizedTest : BasePlatformTestCase() {
    
    @ParameterizedTest
    @ValueSource(strings = ["all", "core", "tests"])
    fun testRenderSection(section: String) = runBlocking {
        val output = cliService.renderSection(section)
        
        assertNotNull(output)
        assertTrue(output.isNotBlank())
    }
}
```

## Benchmark Tests

Для измерения производительности:

```kotlin
import com.intellij.testFramework.PlatformTestUtil

class PerformanceTest : BasePlatformTestCase() {
    
    fun testPerformance() {
        // Warm-up
        repeat(10) {
            processData(testData)
        }
        
        // Measure
        val duration = PlatformTestUtil.measureRealTime {
            repeat(1000) {
                processData(testData)
            }
        }
        
        assertTrue(
            duration < 1000,
            "Processing took ${duration}ms (expected < 1000ms)"
        )
    }
}
```

## Testing Threading

### EDT Tests

```kotlin
fun testOnEDT() {
    // Verify на EDT
    ApplicationManager.getApplication().assertIsDispatchThread()
    
    // UI operation
    updateUI()
}

fun testNotOnEDT() {
    // Verify НЕ на EDT
    ApplicationManager.getApplication().assertIsNonDispatchThread()
    
    // Background work
}
```

### Read/Write Actions in Tests

```kotlin
fun testWriteAction() {
    writeAction {
        val file = project.baseDir!!.createChildData(null, "test.txt")
        file.setBinaryContent("content".toByteArray())
    }
    
    val created = project.baseDir!!.findChild("test.txt")
    assertNotNull(created)
}

fun testReadAction() {
    val content = readAction {
        myFixture.file.text
    }
    
    assertNotNull(content)
}
```

## Disposable в тестах

```kotlin
class DisposableTest : BasePlatformTestCase() {
    
    private lateinit var disposable: Disposable
    
    override fun setUp() {
        super.setUp()
        disposable = Disposer.newDisposable()
    }
    
    override fun tearDown() {
        try {
            Disposer.dispose(disposable)
        } finally {
            super.tearDown()
        }
    }
    
    fun testWithDisposable() {
        val service = MyService(disposable)
        
        // Test
        service.doWork()
        
        // Cleanup автоматически в tearDown
    }
}
```

## Test Utils

### Common test utilities

```kotlin
object TestUtils {
    
    fun createMockProject(): Project {
        return ProjectManager.getInstance().defaultProject
    }
    
    fun createTestFile(
        project: Project,
        name: String,
        content: String
    ): VirtualFile {
        return writeAction {
            val file = project.baseDir!!.createChildData(null, name)
            file.setBinaryContent(content.toByteArray())
            file
        }
    }
    
    fun waitForCondition(
        timeoutMs: Long = 5000,
        condition: () -> Boolean
    ) {
        val start = System.currentTimeMillis()
        
        while (!condition()) {
            if (System.currentTimeMillis() - start > timeoutMs) {
                fail("Timeout waiting for condition")
            }
            Thread.sleep(10)
        }
    }
}
```

## CI/CD Testing

### GitHub Actions

```yaml
# .github/workflows/test.yml
name: Test

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v4
      
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
      
      - name: Run Tests
        run: ./gradlew test
      
      - name: Upload Test Results
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: test-results
          path: build/test-results/
```

### Plugin Verifier в CI

```yaml
- name: Verify Plugin
  run: ./gradlew verifyPlugin
```

## Best Practices

### 1. Изолируйте тесты

```kotlin
// ✅ Каждый тест независим
class MyTest : BasePlatformTestCase() {
    
    override fun setUp() {
        super.setUp()
        // Fresh setup для каждого теста
    }
    
    fun testA() {
        // Не влияет на testB
    }
    
    fun testB() {
        // Не зависит от testA
    }
}
```

### 2. Используйте setUp/tearDown

```kotlin
override fun setUp() {
    super.setUp() // ← ОБЯЗАТЕЛЬНО первым
    // Custom setup
}

override fun tearDown() {
    try {
        // Custom cleanup
    } finally {
        super.tearDown() // ← ОБЯЗАТЕЛЬНО в finally
    }
}
```

### 3. Не забывайте runBlocking для suspend

```kotlin
// ✅ Правильно
fun testAsync() = runBlocking {
    val result = service.asyncMethod()
    assertNotNull(result)
}

// ❌ Не скомпилируется
fun testAsync() {
    val result = service.asyncMethod() // suspend fun!
}
```

### 4. Используйте test data files

```kotlin
// ✅ Переиспользуемые test data
fun testYamlParsing() {
    myFixture.configureByFile("testData/valid-sections.yaml")
    // Test
}

// ❌ Hardcoded строки в коде
fun testYamlParsing() {
    myFixture.configureByText("test.yaml", """
        very: long
        yaml: content
        here: ...
    """)
}
```

### 5. Именование тестов

```kotlin
// ✅ Описательные имена
fun testListSectionsReturnsAllSections()
fun testRenderSectionThrowsExceptionWhenNotFound()
fun testConfigurableAppliesChangesToSettings()

// ❌ Неясные имена
fun test1()
fun testMethod()
fun test()
```

### 6. Arrange-Act-Assert pattern

```kotlin
fun testServiceMethod() {
    // Arrange
    val service = project.service<MyService>()
    val input = "test input"
    
    // Act
    val result = service.process(input)
    
    // Assert
    assertEquals("expected output", result)
}
```

## Integration Test Example

Полный пример теста для LG plugin:

```kotlin
import kotlinx.coroutines.runBlocking

class LgIntegrationTest : BasePlatformTestCase() {
    
    private lateinit var cliService: LgCliService
    private lateinit var catalogService: CatalogService
    
    override fun setUp() {
        super.setUp()
        
        // Setup lg-cfg/
        writeAction {
            val lgCfg = project.baseDir!!.createChildDirectory(null, "lg-cfg")
            
            val sectionsYaml = lgCfg.createChildData(null, "sections.yaml")
            sectionsYaml.setBinaryContent(
                """
                all:
                  extensions: [".py", ".md"]
                  filters:
                    mode: block
                """.trimIndent().toByteArray()
            )
        }
        
        cliService = project.service()
        catalogService = project.service()
    }
    
    fun testLoadSections() = runBlocking {
        val sections = catalogService.loadSections()
        
        assertNotEmpty(sections)
        assertContainsElements(sections, "all")
    }
    
    fun testGenerateListing() = runBlocking {
        // Create test source file
        writeAction {
            val src = project.baseDir!!.createChildDirectory(null, "src")
            val mainPy = src.createChildData(null, "main.py")
            mainPy.setBinaryContent("def hello(): pass".toByteArray())
        }
        
        // Generate
        val output = cliService.renderSection("all")
        
        assertNotNull(output)
        assertTrue(output.contains("main.py"))
        assertTrue(output.contains("def hello()"))
    }
    
    override fun getTestDataPath() = "src/test/resources/testData"
}
```
