# Notifications в IntelliJ Platform

## Обзор

**Notifications** — неблокирующий способ уведомления пользователя без модальных диалогов.

**Принцип:** избегайте модальных message boxes — используйте notifications.

## Типы Notifications

### 1. Balloon Notifications (основной тип)

Всплывающие уведомления в правом нижнем углу:
- Автоматически исчезают через несколько секунд
- Можно кликнуть для действий
- Собираются в Notifications tool window

### 2. Tool Window Notifications

Balloon привязанный к конкретному Tool Window:
- Появляется рядом с tool window button
- Кликабельный

### 3. Editor Banner

Уведомление в верхней части редактора:
- Для важных действий (setup, missing SDK и т.д.)
- Постоянное (не исчезает автоматически)
- С action buttons

### 4. Editor Hints

Подсказки прямо в редакторе:
- Для code-related уведомлений
- Floating popup над кодом

### 5. Status Bar Messages

Временное сообщение в status bar:
- Для quick feedback
- Не требует действий

## Notification Groups

Группы для организации и настройки отображения.

### Регистрация Group

```xml
<extensions defaultExtensionNs="com.intellij">
    <notificationGroup 
        id="LG Notifications"
        displayType="BALLOON"
        key="notification.group.name"
        bundle="messages.LgBundle"/>
    
    <!-- Для tool window -->
    <notificationGroup 
        id="LG Tool Window Notifications"
        displayType="TOOL_WINDOW"
        toolWindowId="Listing Generator"/>
    
    <!-- Sticky balloon (не исчезает) -->
    <notificationGroup 
        id="LG Important"
        displayType="STICKY_BALLOON"/>
</extensions>
```

```properties
# messages/LgBundle.properties
notification.group.name=Listing Generator
```

### Display Types

- `BALLOON` — стандартный balloon (рекомендуется)
- `STICKY_BALLOON` — не исчезает автоматически
- `TOOL_WINDOW` — в tool window
- `NONE` — не показывать (только в log)

## Создание Notifications

### Simple Notification

```kotlin
import com.intellij.notification.*

NotificationGroupManager.getInstance()
    .getNotificationGroup("LG Notifications")
    .createNotification(
        "Listing generated successfully",
        NotificationType.INFORMATION
    )
    .notify(project)
```

### С заголовком и содержимым

```kotlin
NotificationGroupManager.getInstance()
    .getNotificationGroup("LG Notifications")
    .createNotification(
        "Generation Complete",                    // Title
        "Listing saved to output.md",            // Content
        NotificationType.INFORMATION
    )
    .notify(project)
```

### С HTML content

```kotlin
val notification = NotificationGroupManager.getInstance()
    .getNotificationGroup("LG Notifications")
    .createNotification(
        "Files Processed",
        """
        Processed <b>10 files</b>:
        <ul>
            <li>core.py</li>
            <li>utils.py</li>
            <li>config.yaml</li>
        </ul>
        """.trimIndent(),
        NotificationType.INFORMATION
    )

notification.notify(project)
```

## Notification Types

```kotlin
// Information (синий)
NotificationType.INFORMATION

// Warning (жёлтый)
NotificationType.WARNING

// Error (красный)
NotificationType.ERROR
```

## Notification Actions

Добавление кликабельных действий к уведомлению:

```kotlin
val notification = NotificationGroupManager.getInstance()
    .getNotificationGroup("LG Notifications")
    .createNotification(
        "Context Generated",
        "Context saved to clipboard",
        NotificationType.INFORMATION
    )

// Добавить action
notification.addAction(
    NotificationAction.createSimple("View Output") {
        openOutputFile()
    }
)

notification.addAction(
    NotificationAction.createSimple("Copy to Clipboard") {
        copyToClipboard()
    }
)

notification.addAction(
    NotificationAction.createSimple("Open Settings") {
        ShowSettingsUtil.getInstance()
            .showSettingsDialog(project, "LG Settings")
    }
)

notification.notify(project)
```

### Typed Actions

```kotlin
import com.intellij.notification.NotificationAction

class OpenFileNotificationAction(
    private val file: VirtualFile
) : NotificationAction("Open File") {
    
    override fun actionPerformed(
        e: AnActionEvent,
        notification: Notification
    ) {
        val project = e.project ?: return
        
        FileEditorManager.getInstance(project)
            .openFile(file, true)
        
        // Закрыть notification после клика
        notification.expire()
    }
}

// Использование
notification.addAction(OpenFileNotificationAction(file))
```

## Notification с иконкой

```kotlin
val notification = Notification(
    "LG Notifications",                    // Group ID
    AllIcons.FileTypes.Text,               // Icon
    "Title",
    null,                                   // Subtitle
    "Content",
    NotificationType.INFORMATION,
    null                                    // Listener
)

notification.notify(project)
```

## Sticky Notifications (не исчезают)

```kotlin
// Через sticky group
val notification = NotificationGroupManager.getInstance()
    .getNotificationGroup("LG Important")
    .createNotification(
        "Important: Action Required",
        "Please configure your API key",
        NotificationType.WARNING
    )

notification.addAction(
    NotificationAction.createSimple("Configure") {
        openSettings()
        notification.expire() // Закрыть после действия
    }
)

notification.notify(project)

// Или через setImportant
notification.setImportant(true) // Не исчезнет автоматически
```

## Tool Window Notifications

Balloon привязанный к tool window:

```kotlin
import com.intellij.openapi.wm.ToolWindowManager

ToolWindowManager.getInstance(project)
    .notifyByBalloon(
        "Listing Generator",              // Tool window ID
        MessageType.INFO,                  // Type
        "Data refreshed",                  // Message
        AllIcons.Actions.Refresh,          // Icon
        null                               // Hyperlink listener
    )
```

## Editor Banner

Постоянное уведомление в верхней части editor.

### EditorNotificationProvider

```kotlin
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotificationPanel
import java.awt.Color

class LgConfigMissingNotificationProvider : EditorNotificationProvider {
    
    override fun collectNotificationData(
        project: Project,
        file: VirtualFile
    ): Function<FileEditor, JComponent?>? {
        
        // Проверить условие показа
        if (!shouldShowNotification(project)) {
            return null
        }
        
        return Function { fileEditor ->
            createNotificationPanel(project, fileEditor)
        }
    }
    
    private fun shouldShowNotification(project: Project): Boolean {
        val basePath = project.basePath ?: return false
        val lgCfgDir = File(basePath, "lg-cfg")
        return !lgCfgDir.exists()
    }
    
    private fun createNotificationPanel(
        project: Project,
        fileEditor: FileEditor
    ): EditorNotificationPanel {
        return EditorNotificationPanel(fileEditor, Color.YELLOW).apply {
            text = "lg-cfg directory not found"
            
            createActionLabel("Create Config") {
                createLgConfig(project)
                // Скрыть banner
                EditorNotifications.getInstance(project)
                    .updateAllNotifications()
            }
            
            createActionLabel("Dismiss") {
                // Скрыть banner
                EditorNotifications.getInstance(project)
                    .updateAllNotifications()
            }
        }
    }
}
```

Регистрация:

```xml
<extensions defaultExtensionNs="com.intellij">
    <editorNotificationProvider 
        implementation="com.example.lg.LgConfigMissingNotificationProvider"/>
</extensions>
```

## Status Bar Messages

### Временное сообщение

```kotlin
import com.intellij.openapi.wm.WindowManager

val statusBar = WindowManager.getInstance()
    .getStatusBar(project)

// Временное сообщение (3 сек по умолчанию)
statusBar?.info = "File saved"

// С явным временем
statusBar?.setInfo("Processing...", 5000) // 5 sec
```

### Через StatusBarWidget

См. [UI Components](05-ui-components.md#status-bar) для создания permanent widget.

## Editor Hints

Для уведомлений в редакторе:

```kotlin
import com.intellij.codeInsight.hint.HintManager

// Error hint
HintManager.getInstance().showErrorHint(
    editor,
    "Cannot generate listing at current position"
)

// Info hint
HintManager.getInstance().showInformationHint(
    editor,
    "Listing generated to clipboard"
)

// Warning hint
HintManager.getInstance().showQuestionHint(
    editor,
    "Unsaved changes. Continue?",
    0, 10, // position
    object : QuestionAction {
        override fun execute(): Boolean {
            // Yes clicked
            return true
        }
    }
)
```

## "Got It" Tooltips

Для обучения новым features (onboarding):

```kotlin
import com.intellij.ui.GotItTooltip

GotItTooltip(
    "lg.feature.intro",                  // ID (для "don't show again")
    "New Feature: Send to AI",            // Header
    "Click here to send context directly to AI provider" // Text
)
    .withIcon(AllIcons.General.Information)
    .withLink("Learn more", {
        BrowserUtil.browse("https://example.com/docs")
    })
    .show(component, GotItTooltip.BOTTOM_MIDDLE)
```

**Показывается только один раз** (per user), если не нажат "Don't show again".

## Notification Lifecycle

### Expire (закрыть)

```kotlin
val notification = createNotification()

// Показать
notification.notify(project)

// Закрыть программно
notification.expire()

// Или после delay
scope.launch {
    delay(5000)
    notification.expire()
}
```

### Hide/Drop (скрыть)

```kotlin
// Hide — скрыть balloon но оставить в Notifications tool window
notification.hideBalloon()

// Drop — полностью удалить
notification.expire()
```

### Listeners

```kotlin
notification.whenExpired {
    // Notification expired (вызовется из любого места)
    println("Notification closed")
}

notification.setListener { notification, hyperlinkEvent ->
    // Hyperlink clicked
    BrowserUtil.browse(hyperlinkEvent.description)
}
```

## Notification Settings

Пользователь может настроить отображение каждой группы:

**Settings → Appearance & Behavior → Notifications**

Доступные режимы:
- **Balloon** — всплывающий balloon
- **Tool Window** — только в Notifications tool window
- **No Popup** — не показывать

## Patterns для LG Plugin

### Success Notification

```kotlin
fun notifySuccess(project: Project, message: String) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("LG Notifications")
        .createNotification(
            "Success",
            message,
            NotificationType.INFORMATION
        )
        .notify(project)
}

// Использование
notifySuccess(project, "Listing generated successfully")
```

### Error Notification с retry

```kotlin
fun notifyError(
    project: Project,
    message: String,
    error: Exception,
    retryAction: () -> Unit
) {
    val notification = NotificationGroupManager.getInstance()
        .getNotificationGroup("LG Notifications")
        .createNotification(
            "Error",
            "$message: ${error.message}",
            NotificationType.ERROR
        )
    
    notification.addAction(
        NotificationAction.createSimple("Retry") {
            retryAction()
            notification.expire()
        }
    )
    
    notification.addAction(
        NotificationAction.createSimple("View Log") {
            ShowSettingsUtil.getInstance()
                .showSettingsDialog(project, "com.example.lg.settings")
        }
    )
    
    notification.notify(project)
}
```

### Progress Notification

```kotlin
fun showProgressNotification(project: Project): Notification {
    val notification = NotificationGroupManager.getInstance()
        .getNotificationGroup("LG Notifications")
        .createNotification(
            "Generating...",
            "Processing files (0%)",
            NotificationType.INFORMATION
        )
    
    notification.notify(project)
    return notification
}

fun updateProgress(notification: Notification, percent: Int) {
    notification.setContent("Processing files ($percent%)")
}

fun completeProgress(notification: Notification, success: Boolean) {
    if (success) {
        notification.setTitle("Generation Complete")
        notification.setContent("Listing saved successfully")
    } else {
        notification.setTitle("Generation Failed")
        notification.setContent("See error log for details")
    }
    
    // Expire после 3 секунд
    scope.launch {
        delay(3000)
        notification.expire()
    }
}
```

### Copy to Clipboard Notification

```kotlin
fun notifyCopiedToClipboard(project: Project, content: String) {
    CopyPasteManager.getInstance()
        .setContents(StringSelection(content))
    
    NotificationGroupManager.getInstance()
        .getNotificationGroup("LG Notifications")
        .createNotification(
            "Copied to clipboard",
            NotificationType.INFORMATION
        )
        .notify(project)
}
```

### Configuration Required Notification

```kotlin
fun notifyConfigurationRequired(project: Project) {
    val notification = NotificationGroupManager.getInstance()
        .getNotificationGroup("LG Notifications")
        .createNotification(
            "Configuration Required",
            "Listing Generator is not configured",
            NotificationType.WARNING
        )
    
    notification.addAction(
        NotificationAction.createSimple("Configure") {
            ShowSettingsUtil.getInstance()
                .showSettingsDialog(project, "com.example.lg.settings")
        }
    )
    
    notification.addAction(
        NotificationAction.createSimple("Don't Show Again") {
            // Save preference
            PropertiesComponent.getInstance().setValue(
                "com.example.lg.hideConfigNotification",
                true
            )
            notification.expire()
        }
    )
    
    notification.notify(project)
}
```

## NotificationGroup API

### Получение группы

```kotlin
val group = NotificationGroupManager.getInstance()
    .getNotificationGroup("LG Notifications")

if (group == null) {
    LOG.error("Notification group not found")
    return
}
```

### Создание через NotificationGroup

Для programmatic creation без регистрации в plugin.xml:

```kotlin
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationDisplayType

val group = NotificationGroup.create(
    "My Dynamic Group",
    NotificationDisplayType.BALLOON,
    isLogByDefault = true
)

group.createNotification(
    "Message",
    NotificationType.INFORMATION
).notify(project)
```

## Notification Severity Icons

```kotlin
// Auto icons по типу
NotificationType.INFORMATION  // ℹ️ info icon
NotificationType.WARNING      // ⚠️ warning icon
NotificationType.ERROR        // ❌ error icon

// Custom icon
val notification = Notification(
    "Group ID",
    AllIcons.FileTypes.Text,  // Custom icon
    "Title",
    null,
    "Content",
    NotificationType.INFORMATION,
    null
)
```

## Hyperlinks в Notifications

### Inline links

```kotlin
val notification = NotificationGroupManager.getInstance()
    .getNotificationGroup("LG Notifications")
    .createNotification(
        "Update Available",
        "New version 2.0 is available. <a href='download'>Download</a> or <a href='info'>Learn more</a>",
        NotificationType.INFORMATION
    )

notification.setListener { notification, event ->
    when (event.description) {
        "download" -> downloadUpdate()
        "info" -> openDocumentation()
    }
}

notification.notify(project)
```

### NotificationAction vs Hyperlinks

```kotlin
// ❌ Hyperlink в content (менее заметно)
createNotification(
    "Title",
    "Message. <a href='action'>Click here</a>",
    NotificationType.INFORMATION
)

// ✅ NotificationAction (более заметно, как кнопки)
val notification = createNotification("Title", "Message", ...)
notification.addAction(
    NotificationAction.createSimple("Click Here") { }
)
```

## Важные Notifications

### Предупреждение о breaking change

```kotlin
val notification = NotificationGroupManager.getInstance()
    .getNotificationGroup("LG Important")
    .createNotification(
        "Breaking Change",
        "lg-cfg format has changed. Migration required.",
        NotificationType.WARNING
    )

notification.setImportant(true) // Не исчезнет автоматически

notification.addAction(
    NotificationAction.createSimple("Migrate Now") {
        runMigration(project)
        notification.expire()
    }
)

notification.addAction(
    NotificationAction.createSimple("Learn More") {
        BrowserUtil.browse("https://example.com/migration")
    }
)

notification.notify(project)
```

### Ошибка с Show Details

```kotlin
fun notifyCliError(project: Project, error: CliException) {
    val notification = NotificationGroupManager.getInstance()
        .getNotificationGroup("LG Notifications")
        .createNotification(
            "CLI Error",
            error.message ?: "Unknown error",
            NotificationType.ERROR
        )
    
    // Если есть stderr
    if (error.stderr.isNotBlank()) {
        notification.addAction(
            NotificationAction.createSimple("Show Details") {
                showErrorDetails(project, error.stderr)
            }
        )
    }
    
    notification.addAction(
        NotificationAction.createSimple("Open Settings") {
            ShowSettingsUtil.getInstance()
                .showSettingsDialog(project, "LG Settings")
        }
    )
    
    notification.notify(project)
}

private fun showErrorDetails(project: Project, stderr: String) {
    val dialog = object : DialogWrapper(project) {
        init {
            title = "Error Details"
            init()
        }
        
        override fun createCenterPanel(): JComponent {
            return JBScrollPane(
                JTextArea(stderr, 20, 80).apply {
                    isEditable = false
                    font = JBFont.create(Font.MONOSPACED, 12)
                }
            )
        }
    }
    
    dialog.show()
}
```

## Event Log (Notifications Tool Window)

Все notifications собираются в **Event Log** tool window.

### Открыть Event Log

```kotlin
import com.intellij.notification.EventLog

EventLog.toggleLog(project, null)
```

### Очистить Event Log

```kotlin
EventLog.getLogModel(project).clearAll()
```

## Conditional Notifications

### Don't show again

```kotlin
fun showOnboardingNotification(project: Project) {
    // Проверить preference
    val props = PropertiesComponent.getInstance()
    val hideKey = "com.example.lg.hideOnboarding"
    
    if (props.getBoolean(hideKey, false)) {
        return // Не показывать
    }
    
    val notification = NotificationGroupManager.getInstance()
        .getNotificationGroup("LG Notifications")
        .createNotification(
            "Welcome to Listing Generator",
            "Click 'Get Started' to configure",
            NotificationType.INFORMATION
        )
    
    notification.addAction(
        NotificationAction.createSimple("Get Started") {
            runWizard(project)
        }
    )
    
    notification.addAction(
        NotificationAction.createSimple("Don't Show Again") {
            props.setValue(hideKey, true)
            notification.expire()
        }
    )
    
    notification.notify(project)
}
```

### Первый запуск

```kotlin
@Service
class FirstRunService {
    
    private val props = PropertiesComponent.getInstance()
    private val firstRunKey = "com.example.lg.firstRun"
    
    fun checkFirstRun(project: Project) {
        if (props.getBoolean(firstRunKey, true)) {
            props.setValue(firstRunKey, false)
            showWelcomeNotification(project)
        }
    }
}
```

## Balloon Positioning

### Позиция balloon

```kotlin
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon

val balloon = JBPopupFactory.getInstance()
    .createHtmlTextBalloonBuilder(
        "Message here",
        MessageType.INFO,
        null
    )
    .setFadeoutTime(3000)
    .setHideOnClickOutside(true)
    .setHideOnKeyOutside(true)
    .createBalloon()

// Показать относительно component
balloon.show(
    RelativePoint.getSouthOf(component),
    Balloon.Position.below
)
```

**Positions:**
- `above` / `below` — над/под компонентом
- `atLeft` / `atRight` — слева/справа
- `above` / `below` — над/под

## Throttling Notifications

Избегайте spam:

```kotlin
@Service
class NotificationThrottler {
    
    private val lastNotificationTime = mutableMapOf<String, Long>()
    private val throttleMs = 3000L // 3 sec
    
    fun shouldShow(key: String): Boolean {
        val now = System.currentTimeMillis()
        val last = lastNotificationTime[key] ?: 0
        
        if (now - last < throttleMs) {
            return false // Слишком рано
        }
        
        lastNotificationTime[key] = now
        return true
    }
}

// Использование
fun notifyFilesSaved(project: Project) {
    val throttler = service<NotificationThrottler>()
    
    if (!throttler.shouldShow("files.saved")) {
        return // Не спамим
    }
    
    NotificationGroupManager.getInstance()
        .getNotificationGroup("LG Notifications")
        .createNotification(
            "Files saved",
            NotificationType.INFORMATION
        )
        .notify(project)
}
```

## Grouping Similar Notifications

```kotlin
class NotificationAggregator {
    
    private val pendingErrors = mutableListOf<String>()
    private var aggregateJob: Job? = null
    
    fun reportError(error: String) {
        pendingErrors.add(error)
        
        // Отменить предыдущий таймер
        aggregateJob?.cancel()
        
        // Запустить новый (debounce)
        aggregateJob = scope.launch {
            delay(2000) // Собираем ошибки 2 сек
            
            val errors = pendingErrors.toList()
            pendingErrors.clear()
            
            withContext(Dispatchers.EDT) {
                showAggregatedErrors(errors)
            }
        }
    }
    
    private fun showAggregatedErrors(errors: List<String>) {
        val message = when {
            errors.size == 1 -> errors.first()
            else -> "${errors.size} errors occurred"
        }
        
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("LG Notifications")
            .createNotification(
                "Errors",
                message,
                NotificationType.ERROR
            )
        
        if (errors.size > 1) {
            notification.addAction(
                NotificationAction.createSimple("Show All") {
                    showErrorList(errors)
                }
            )
        }
        
        notification.notify(project)
    }
}
```

## Notification Testing

```kotlin
import com.intellij.notification.Notification
import com.intellij.notification.Notifications

class NotificationTest : BasePlatformTestCase() {
    
    private val receivedNotifications = mutableListOf<Notification>()
    
    override fun setUp() {
        super.setUp()
        
        // Перехватить notifications
        project.messageBus.connect(testRootDisposable)
            .subscribe(
                Notifications.TOPIC,
                object : Notifications {
                    override fun notify(notification: Notification) {
                        receivedNotifications.add(notification)
                    }
                }
            )
    }
    
    fun testNotificationShown() {
        // Trigger notification
        notifySuccess(project, "Test message")
        
        // Verify
        assertEquals(1, receivedNotifications.size)
        val notification = receivedNotifications.first()
        assertEquals("Test message", notification.content)
        assertEquals(NotificationType.INFORMATION, notification.type)
    }
}
```

## Best Practices

### 1. Используйте правильный тип

```kotlin
// ✅ Information для успеха
NotificationType.INFORMATION

// ✅ Warning для предупреждений
NotificationType.WARNING

// ✅ Error для ошибок
NotificationType.ERROR
```

### 2. Не спамьте notifications

```kotlin
// ❌ Плохо
for (file in files) {
    notify("Processed ${file.name}") // 100 notifications!
}

// ✅ Хорошо
notify("Processed ${files.size} files")

// Или aggregate
notify("Processed: ${files.joinToString { it.name }}")
```

### 3. Добавляйте actions для важных

```kotlin
// ✅ С возможностью действия
val notification = createNotification(...)
notification.addAction(
    NotificationAction.createSimple("Fix") {
        fixProblem()
    }
)

// ❌ Без action (пользователь не знает что делать)
createNotification("Configuration error", NotificationType.ERROR)
```

### 4. Локализуйте текст

```kotlin
// ✅ Через bundle
val message = LgBundle.message("notification.success.generated")

// ❌ Hardcoded
val message = "Listing generated successfully"
```

### 5. Используйте sticky для важных

```kotlin
// ✅ Important notification
notification.setImportant(true)

// Или через sticky group
<notificationGroup 
    id="LG Important"
    displayType="STICKY_BALLOON"/>
```

### 6. Expire programmatically

```kotlin
// ✅ Закрыть когда не актуально
scope.launch {
    val notification = showProcessingNotification()
    
    val result = processData()
    
    notification.expire() // Закрыть старое
    showResultNotification(result) // Показать новое
}
```

### 7. Группируйте related actions

```kotlin
notification.addAction(
    NotificationAction.createSimple("Copy Result") { }
)
notification.addAction(
    NotificationAction.createSimple("Open File") { }
)
notification.addAction(
    NotificationAction.createSimple("Share") { }
)
// Все появятся как кнопки под notification
```

## Integration с другими компонентами

### Notification из Service

```kotlin
@Service(Service.Level.PROJECT)
class LgGeneratorService(private val project: Project) {
    
    suspend fun generate(template: String) {
        try {
            val result = withBackgroundProgress(
                project,
                "Generating...",
                cancellable = true
            ) {
                performGeneration(template)
            }
            
            // Success notification
            notifySuccess("Generated successfully")
            
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Error notification
            notifyError("Generation failed", e)
        }
    }
    
    private fun notifySuccess(message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("LG Notifications")
            .createNotification(message, NotificationType.INFORMATION)
            .notify(project)
    }
    
    private fun notifyError(message: String, error: Exception) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("LG Notifications")
            .createNotification(
                message,
                error.message ?: "Unknown error",
                NotificationType.ERROR
            )
            .notify(project)
    }
}
```

### Notification из Action

```kotlin
class GenerateAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        project.service<LgGeneratorService>().generateAsync { result ->
            NotificationGroupManager.getInstance()
                .getNotificationGroup("LG Notifications")
                .createNotification(
                    "Generation Complete",
                    "Listing saved to ${result.path}",
                    NotificationType.INFORMATION
                )
                .addAction(
                    NotificationAction.createSimple("Open") {
                        openFile(project, result.path)
                    }
                )
                .notify(project)
        }
    }
    
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
```

## Alternatives to Notifications

Когда **НЕ** использовать notifications:

### 1. Validation Errors → DialogWrapper.doValidate()

```kotlin
// ❌ НЕ notification для validation
override fun doOKAction() {
    if (input.isBlank()) {
        notify("Input is empty", NotificationType.ERROR)
        return
    }
}

// ✅ Dialog validation
override fun doValidate(): ValidationInfo? {
    if (input.isBlank()) {
        return ValidationInfo("Input cannot be empty", inputField)
    }
    return null
}
```

### 2. Editor Errors → HintManager

```kotlin
// ❌ НЕ notification для editor operations
notify("Cannot refactor at this position", NotificationType.ERROR)

// ✅ Editor hint
HintManager.getInstance().showErrorHint(
    editor,
    "Cannot refactor at this position"
)
```

### 3. Immediate Feedback → Status Bar

```kotlin
// ❌ Notification для простого feedback
notify("File saved", NotificationType.INFORMATION)

// ✅ Status bar message
WindowManager.getInstance().getStatusBar(project)?.info = "File saved"
```
