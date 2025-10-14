# Settings в IntelliJ Platform

## Обзор

**Settings** (или Preferences на macOS) — механизм для конфигурации плагинов.

Settings могут быть двух уровней:
- **Application-level** — глобальные настройки для всех проектов
- **Project-level** — настройки конкретного проекта

## Архитектура Settings

Settings состоят из двух частей:

1. **Configurable** — UI для редактирования настроек
2. **PersistentStateComponent** — хранение состояния

```
┌────────────────────────────────────────┐
│     Settings Dialog UI                 │
│  ┌──────────────────────────────────┐  │
│  │   Configurable (UI)              │  │
│  │  ┌────────────────────────────┐  │  │
│  │  │ Kotlin UI DSL panel        │  │  │
│  │  │  Name: [____________]      │  │  │
│  │  │  Email: [___________]      │  │  │
│  │  └────────────────────────────┘  │  │
│  └──────────────────────────────────┘  │
│         ↕ bind                         │
│  ┌──────────────────────────────────┐  │
│  │ PersistentStateComponent         │  │
│  │   (State storage)                │  │
│  └──────────────────────────────────┘  │
└────────────────────────────────────────┘
```

## Создание Application Settings

### 1. State Service

```kotlin
import com.intellij.openapi.components.*

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
        var openAsEditable by property(false)
    }
    
    companion object {
        fun getInstance(): LgSettingsService = service()
    }
}
```

### 2. Configurable UI

```kotlin
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.ui.dsl.builder.*

class LgSettingsConfigurable : BoundConfigurable("Listing Generator") {
    
    private val settings = LgSettingsService.getInstance()
    
    override fun createPanel() = panel {
        
        group("CLI Configuration") {
            row("CLI Path:") {
                textFieldWithBrowseButton(
                    "Select listing-generator executable",
                    null,
                    FileChooserDescriptorFactory.createSingleFileDescriptor()
                ).bindText(settings.state::cliPath)
                    .align(AlignX.FILL)
                    .comment("Leave empty for auto-detection")
            }
            
            row("Python Interpreter:") {
                textFieldWithBrowseButton(
                    "Select Python Interpreter",
                    null,
                    FileChooserDescriptorFactory.createSingleFileDescriptor()
                ).bindText(settings.state::pythonInterpreter)
                    .align(AlignX.FILL)
            }
        }
        
        group("Tokenization Defaults") {
            row("Tokenizer Library:") {
                comboBox(listOf("tiktoken", "tokenizers", "sentencepiece"))
                    .bindItem(settings.state::defaultTokenizer)
            }
            
            row("Encoder:") {
                textField()
                    .bindText(settings.state::defaultEncoder)
                    .columns(30)
            }
            
            row("Context Limit:") {
                intTextField(range = 1000..2_000_000)
                    .bindIntText(settings.state::contextLimit)
            }
        }
        
        group("Editor") {
            row {
                checkBox("Open generated files as editable")
                    .bindSelected(settings.state::openAsEditable)
                    .comment("By default files open as read-only")
            }
        }
    }
}
```

### 3. Регистрация в plugin.xml

```xml
<extensions defaultExtensionNs="com.intellij">
    <applicationConfigurable 
        parentId="tools"
        instance="com.example.lg.settings.LgSettingsConfigurable"
        id="com.example.lg.settings"
        displayName="Listing Generator"/>
</extensions>
```

Или с локализацией:

```xml
<applicationConfigurable 
    parentId="tools"
    instance="com.example.lg.settings.LgSettingsConfigurable"
    id="com.example.lg.settings"
    key="settings.display.name"
    bundle="messages.LgBundle"/>
```

## Создание Project Settings

### 1. State Service

```kotlin
@Service(Service.Level.PROJECT)
@State(
    name = "LgProjectSettings",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)]
)
class LgProjectSettingsService(
    private val project: Project
) : SimplePersistentStateComponent<State>(State()) {
    
    class State : BaseState() {
        var defaultSection by string("all")
        var defaultTemplate by string("")
        var selectedMode by string("planning")
        var activeTags by list<String>()
    }
    
    companion object {
        fun getInstance(project: Project): LgProjectSettingsService {
            return project.service()
        }
    }
}
```

### 2. Configurable UI

```kotlin
class LgProjectSettingsConfigurable(
    private val project: Project
) : BoundConfigurable("Listing Generator Project Settings") {
    
    private val settings = LgProjectSettingsService.getInstance(project)
    
    override fun createPanel() = panel {
        
        group("Defaults") {
            row("Default Section:") {
                comboBox(loadSections())
                    .bindItem(settings.state::defaultSection)
            }
            
            row("Default Template:") {
                comboBox(loadTemplates())
                    .bindItem(settings.state::defaultTemplate)
            }
        }
        
        group("Adaptive Settings") {
            row("Mode:") {
                comboBox(listOf("planning", "development", "review"))
                    .bindItem(settings.state::selectedMode)
            }
        }
    }
    
    private fun loadSections(): List<String> {
        // Load from CLI
        return listOf("all", "core", "tests")
    }
    
    private fun loadTemplates(): List<String> {
        // Load from lg-cfg/
        return listOf("", "default", "api-docs")
    }
}
```

### 3. Регистрация

```xml
<extensions defaultExtensionNs="com.intellij">
    <projectConfigurable 
        parentId="tools"
        instance="com.example.lg.settings.LgProjectSettingsConfigurable"
        id="com.example.lg.project.settings"
        displayName="Listing Generator"
        nonDefaultProject="true"/>
</extensions>
```

**Атрибут `nonDefaultProject="true"`:**
- Settings **не показываются** для default project
- Рекомендуется для большинства project settings

## Parent Groups

Укажите `parentId` для размещения в Settings dialog:

| Parent Group | `parentId` | Когда использовать |
|--------------|------------|--------------------|
| **Appearance & Behavior** | `appearance` | Темы, шрифты, UI настройки |
| **Editor** | `editor` | Код, подсветка, форматирование |
| **Build, Execution, Deployment** | `build` | Build tools, компиляторы |
| **Languages & Frameworks** | `language` | Language-specific настройки |
| **Tools** | `tools` | **Сторонние инструменты (LG!)** |

```xml
<!-- ✅ Правильно для LG -->
<applicationConfigurable parentId="tools" .../>

<!-- ❌ НЕ используйте -->
<applicationConfigurable parentId="other" .../> <!-- Deprecated -->
```

## BoundConfigurable

**Рекомендуемый** базовый класс для Configurable с data binding.

### Полный пример

```kotlin
class MyConfigurable : BoundConfigurable("My Settings") {
    
    private val settings = MySettings.getInstance()
    
    // Создание UI с автоматическим binding
    override fun createPanel() = panel {
        row("API Key:") {
            textField()
                .bindText(settings.state::apiKey)
                .columns(40)
                .comment("Get your key from https://example.com")
        }
        
        row("Timeout (sec):") {
            intTextField(range = 1..300)
                .bindIntText(settings.state::timeout)
        }
        
        row {
            checkBox("Enable debug mode")
                .bindSelected(settings.state::debugMode)
        }
    }
    
    // Опционально: custom validation
    override fun apply() {
        super.apply() // Применит все bindings
        
        // Custom logic после apply
        restartService()
    }
    
    // Опционально: проверка перед apply
    override fun isModified(): Boolean {
        if (super.isModified()) return true
        
        // Custom modification check
        return hasCustomChanges()
    }
}
```

**BoundConfigurable автоматически:**
- Вызывает `panel.apply()` → обновляет state
- Вызывает `panel.reset()` → восстанавливает из state
- Проверяет `panel.isModified()` → активирует Apply button

## Configurable Interface (низкоуровневый)

Если нужен полный контроль:

```kotlin
import com.intellij.openapi.options.Configurable
import javax.swing.JComponent

class MyAdvancedConfigurable : Configurable {
    
    private val settings = MySettings.getInstance()
    private var panel: MySettingsPanel? = null
    
    override fun getDisplayName() = "My Advanced Settings"
    
    override fun createComponent(): JComponent {
        panel = MySettingsPanel(settings)
        return panel!!
    }
    
    override fun isModified(): Boolean {
        return panel?.isModified() ?: false
    }
    
    override fun apply() {
        panel?.apply()
    }
    
    override fun reset() {
        panel?.reset()
    }
    
    override fun disposeUIResources() {
        panel = null
    }
}
```

**Lifecycle:**
1. User opens Settings → `createComponent()` вызывается
2. User edits → `isModified()` проверяется для Apply button
3. User clicks Apply/OK → `apply()` вызывается
4. User clicks Cancel или закрывает → `disposeUIResources()`

## Validation в Settings

### Input Validation

```kotlin
override fun createPanel() = panel {
    row("Port:") {
        intTextField(range = 1..65535)
            .bindIntText(settings.state::port)
            .validationOnInput { textField ->
                val value = textField.text.toIntOrNull()
                when {
                    value == null -> 
                        error("Must be a number")
                    value !in 1..65535 -> 
                        error("Port must be between 1 and 65535")
                    else -> null
                }
            }
    }
}
```

### Apply Validation

```kotlin
override fun createPanel() = panel {
    row("API Key:") {
        passwordField()
            .bindText(settings.state::apiKey)
            .validationOnApply { field ->
                val key = String(field.password)
                if (key.isBlank()) {
                    error("API Key is required")
                } else if (!validateApiKey(key)) {
                    error("Invalid API Key format")
                } else {
                    null
                }
            }
    }
}
```

### Custom Validation Logic

```kotlin
class MyConfigurable : BoundConfigurable("Settings") {
    
    override fun createPanel() = panel {
        // ... UI
    }
    
    override fun apply() {
        // Валидация перед apply
        val errors = validateSettings()
        if (errors.isNotEmpty()) {
            throw ConfigurationException(
                errors.joinToString("\n")
            )
        }
        
        super.apply()
        
        // Post-apply actions
        notifySettingsChanged()
    }
    
    private fun validateSettings(): List<String> {
        val errors = mutableListOf<String>()
        
        if (settings.state.apiKey.isBlank()) {
            errors.add("API Key is required")
        }
        
        if (settings.state.timeout < 1) {
            errors.add("Timeout must be positive")
        }
        
        return errors
    }
}
```

## Opening Settings Programmatically

### Открыть Settings Dialog

```kotlin
import com.intellij.openapi.options.ShowSettingsUtil

// Открыть общие Settings
ShowSettingsUtil.getInstance().showSettingsDialog(project)

// Открыть конкретную страницу
ShowSettingsUtil.getInstance().showSettingsDialog(
    project,
    "com.example.lg.settings" // Configurable ID
)

// Открыть и выполнить action
ShowSettingsUtil.getInstance().editConfigurable(
    project,
    MyConfigurable()
)
```

### Из Notification

```kotlin
val notification = NotificationGroupManager.getInstance()
    .getNotificationGroup("LG Notifications")
    .createNotification(
        "Configuration required",
        "Please configure Listing Generator settings",
        NotificationType.WARNING
    )

notification.addAction(
    NotificationAction.createSimple("Open Settings") {
        ShowSettingsUtil.getInstance()
            .showSettingsDialog(project, "com.example.lg.settings")
    }
)

notification.notify(project)
```

## Settings Groups (иерархия)

### Создание подгруппы

```kotlin
// Parent Configurable
class LgMainConfigurable : BoundConfigurable("Listing Generator") {
    
    override fun createPanel() = panel {
        row("General settings here") {
            textField()
        }
    }
}

// Child Configurable
class LgAdvancedConfigurable : BoundConfigurable("Advanced") {
    
    override fun createPanel() = panel {
        row("Advanced settings here") {
            textField()
        }
    }
}
```

Регистрация:

```xml
<!-- Parent -->
<applicationConfigurable 
    parentId="tools"
    instance="com.example.lg.LgMainConfigurable"
    id="com.example.lg.settings"
    displayName="Listing Generator"/>

<!-- Child -->
<applicationConfigurable 
    parentId="com.example.lg.settings"
    instance="com.example.lg.LgAdvancedConfigurable"
    id="com.example.lg.settings.advanced"
    displayName="Advanced"/>
```

Результат в UI:
```
Tools
  └─ Listing Generator
      └─ Advanced
```

## Configurable Provider (условный)

Для Settings, показываемых **только при определённых условиях**:

```kotlin
import com.intellij.openapi.options.ConfigurableProvider

class LgConfigurableProvider : ConfigurableProvider() {
    
    override fun canCreateConfigurable(): Boolean {
        // Показывать только если CLI найден
        val cliService = service<CliDetectionService>()
        return cliService.detectCli() != null
    }
    
    override fun createConfigurable(): Configurable {
        return LgSettingsConfigurable()
    }
}
```

Регистрация:

```xml
<applicationConfigurable 
    parentId="tools"
    provider="com.example.lg.LgConfigurableProvider"
    id="com.example.lg.settings"
    displayName="Listing Generator"/>
```

**Важно:** используйте `provider` вместо `instance`.

## SearchableConfigurable

Для улучшения поиска в Settings (Ctrl+F):

```kotlin
import com.intellij.openapi.options.SearchableConfigurable

class MySearchableConfigurable : SearchableConfigurable, BoundConfigurable("Settings") {
    
    override fun getId() = "com.example.mysettings"
    
    override fun createPanel() = panel {
        // UI
    }
}
```

ID используется для:
- Deep links: `idea://settings?id=com.example.mysettings`
- Search indexing
- Уникальная идентификация

## Composite Configurable (с children)

```kotlin
import com.intellij.openapi.options.Configurable

class LgRootConfigurable : Configurable.Composite {
    
    override fun getDisplayName() = "Listing Generator"
    
    override fun getConfigurables(): Array<Configurable> {
        return arrayOf(
            LgGeneralConfigurable(),
            LgTokenizationConfigurable(),
            LgAdvancedConfigurable()
        )
    }
    
    override fun createComponent(): JComponent? = null
    override fun isModified() = false
    override fun apply() { }
}
```

Дети появятся как вложенные страницы.

## PropertiesComponent (простое хранилище)

Для **простых** key-value настроек без UI:

```kotlin
import com.intellij.ide.util.PropertiesComponent

// Application-level
val props = PropertiesComponent.getInstance()
props.setValue("com.example.lg.lastTemplate", "default")
val lastTemplate = props.getValue("com.example.lg.lastTemplate")

// Project-level
val projectProps = PropertiesComponent.getInstance(project)
projectProps.setValue("com.example.lg.lastSection", "all")
val lastSection = projectProps.getValue("com.example.lg.lastSection")

// Boolean
projectProps.setBoolean("com.example.lg.treeView", true)
val isTreeView = projectProps.getBoolean("com.example.lg.treeView", false)

// Int
projectProps.setInt("com.example.lg.count", 10)
val count = projectProps.getInt("com.example.lg.count", 0)
```

**Важно:**
- Всегда используйте **префикс** с plugin ID
- **Не синхронизируется** между машинами (roaming disabled)
- Только для **временных** значений, не для Settings

## Settings Migration

Если изменили структуру State:

```kotlin
@State(
    name = "LgSettings",
    storages = [Storage("lg-settings.xml")]
)
class LgSettingsService : SimplePersistentStateComponent<State>(State()) {
    
    class State : BaseState() {
        // Новые поля
        var version by property(2)
        var cliPath by string("")
        var timeout by property(30)
        
        // Старые поля (deprecated, для миграции)
        @Deprecated("Use cliPath")
        var legacyPath by string("")
    }
    
    override fun loadState(state: State) {
        super.loadState(state)
        
        // Миграция при загрузке
        if (state.version < 2) {
            migrateToV2(state)
            state.version = 2
        }
    }
    
    private fun migrateToV2(state: State) {
        if (state.cliPath.isBlank() && state.legacyPath.isNotBlank()) {
            state.cliPath = state.legacyPath
            state.legacyPath = ""
        }
    }
}
```

## Accessing Settings

### Из Actions

```kotlin
class MyAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        // Application settings
        val settings = LgSettingsService.getInstance()
        val cliPath = settings.state.cliPath
        
        // Project settings
        val project = e.project ?: return
        val projectSettings = LgProjectSettingsService.getInstance(project)
        val section = projectSettings.state.defaultSection
    }
    
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
```

### Из Services

```kotlin
@Service(Service.Level.PROJECT)
class LgGeneratorService(private val project: Project) {
    
    suspend fun generateListing(): String {
        // Читаем настройки
        val appSettings = LgSettingsService.getInstance()
        val projectSettings = LgProjectSettingsService.getInstance(project)
        
        val args = buildList {
            add("listing-generator")
            add("render")
            add("sec:${projectSettings.state.defaultSection}")
            add("--lib")
            add(appSettings.state.defaultTokenizer)
        }
        
        return project.service<LgCliService>().execute(args)
    }
}
```

## Settings Notifications

При изменении Settings уведомите другие компоненты:

### Through Message Bus

```kotlin
// Topic
interface SettingsChangedListener {
    fun settingsChanged(settings: LgSettingsService.State)
    
    companion object {
        val TOPIC = Topic.create(
            "LG Settings Changed",
            SettingsChangedListener::class.java
        )
    }
}

// Configurable
class LgSettingsConfigurable : BoundConfigurable("Settings") {
    
    override fun apply() {
        super.apply()
        
        // Уведомить об изменениях
        ApplicationManager.getApplication().messageBus
            .syncPublisher(SettingsChangedListener.TOPIC)
            .settingsChanged(settings.state)
    }
}

// Listener
@Service(Service.Level.PROJECT)
class LgService(private val project: Project) : Disposable {
    
    init {
        ApplicationManager.getApplication().messageBus
            .connect(this)
            .subscribe(
                SettingsChangedListener.TOPIC,
                object : SettingsChangedListener {
                    override fun settingsChanged(settings: State) {
                        // Reload configuration
                        reloadConfig()
                    }
                }
            )
    }
    
    override fun dispose() { }
}
```

## Reset to Defaults

```kotlin
override fun createPanel() = panel {
    
    // ... настройки ...
    
    row {
        button("Reset to Defaults") {
            resetToDefaults()
        }
    }
}

private fun resetToDefaults() {
    settings.loadState(State()) // Новый state с defaults
    reset() // Обновить UI
}
```

## Import/Export Settings

### Export Settings to File

```kotlin
import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

fun exportSettings(file: File) {
    val settings = LgSettingsService.getInstance()
    
    val json = Json.encodeToString(settings.state)
    Files.writeString(file.toPath(), json)
}
```

### Import Settings from File

```kotlin
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

fun importSettings(file: File) {
    val json = Files.readString(file.toPath())
    val importedState = Json.decodeFromString<State>(json)
    
    val settings = LgSettingsService.getInstance()
    settings.loadState(importedState)
    
    // Уведомить UI о изменениях
    ShowSettingsUtil.getInstance().showSettingsDialog(
        null,
        "com.example.lg.settings"
    )
}
```

## Settings Storage Location

```kotlin
@State(
    name = "MySettings",
    storages = [
        Storage("myPlugin.xml")  // Application: IDE config dir
    ]
)

@State(
    name = "MyProjectSettings",
    storages = [
        Storage(StoragePathMacros.WORKSPACE_FILE)  // Project: .idea/workspace.xml
    ]
)

@State(
    name = "MyCache",
    storages = [
        Storage(
            value = StoragePathMacros.CACHE_FILE,  // .idea/caches/
            roamingType = RoamingType.DISABLED      // Не синхронизировать
        )
    ]
)
```

**Storage locations:**

Application-level:
- Windows: `%APPDATA%\JetBrains\<Product><Version>\options\myPlugin.xml`
- macOS: `~/Library/Application Support/JetBrains/<Product><Version>/options/myPlugin.xml`
- Linux: `~/.config/JetBrains/<Product><Version>/options/myPlugin.xml`

Project-level:
- `.idea/myPlugin.xml` или `.idea/workspace.xml`

## Settings Sync (Backup and Sync)

Для синхронизации между машинами:

```kotlin
@State(
    name = "LgSettings",
    storages = [
        Storage(
            value = "lg-settings.xml",
            roamingType = RoamingType.DEFAULT // Синхронизировать
        )
    ],
    category = SettingsCategory.TOOLS
)
class LgSettingsService : SimplePersistentStateComponent<State>(State())
```

**SettingsCategory:**
- `TOOLS` — инструменты ✅ для LG
- `CODE` — код и форматирование
- `APPEARANCE` — внешний вид
- `SYSTEM` — системные настройки
- `OTHER` — не синхронизируется

**RoamingType:**
- `DEFAULT` — синхронизировать между всеми машинами
- `PER_OS` — отдельно для каждой ОС
- `DISABLED` — не синхронизировать

## Configurable Marker Interfaces

### Configurable.NoScroll

Убрать scroll bars (для компонентов с собственным scrolling):

```kotlin
class MyConfigurable : BoundConfigurable("Settings"), Configurable.NoScroll {
    
    override fun createPanel() = panel {
        row {
            val tree = Tree(model)
            scrollCell(tree) // Собственный JScrollPane
                .align(Align.FILL)
        }.resizableRow()
    }
}
```

### Configurable.NoMargin

Убрать margins вокруг панели:

```kotlin
class MyConfigurable : BoundConfigurable("Settings"), Configurable.NoMargin {
    // No empty border around panel
}
```

### Configurable.Beta

Показать Beta badge в Settings tree (2022.3+):

```kotlin
class MyNewConfigurable : BoundConfigurable("New Feature"), Configurable.Beta {
    // "New Feature [Beta]" в дереве
}
```

## Testing Settings

```kotlin
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SettingsTest : BasePlatformTestCase() {
    
    fun testSettingsPersistence() {
        val settings = LgSettingsService.getInstance()
        
        // Изменить
        settings.state.cliPath = "/custom/path"
        settings.state.timeout = 60
        
        // Эмулировать save
        settings.noStateLoaded() // Trigger save
        
        // Создать новый instance
        val newSettings = LgSettingsService()
        newSettings.loadState(settings.state)
        
        // Проверить
        assertEquals("/custom/path", newSettings.state.cliPath)
        assertEquals(60, newSettings.state.timeout)
    }
    
    fun testConfigurableUI() {
        val configurable = LgSettingsConfigurable()
        val panel = configurable.createPanel()
        
        assertNotNull(panel)
        
        // Изменить через UI
        // ... interact with panel components ...
        
        // Apply
        configurable.apply()
        
        // Проверить что state обновился
        val settings = LgSettingsService.getInstance()
        assertEquals("expected", settings.state.someValue)
    }
}
```

## Best Practices

### 1. Используйте BoundConfigurable

```kotlin
// ✅ Современный подход (меньше boilerplate)
class MyConfigurable : BoundConfigurable("Settings") {
    override fun createPanel() = panel { }
}

// ❌ Legacy (больше кода)
class MyConfigurable : Configurable {
    override fun createComponent(): JComponent { }
    override fun isModified(): Boolean { }
    override fun apply() { }
    override fun reset() { }
}
```

### 2. Правильный parentId

```kotlin
// ✅ Tools для сторонних инструментов
<applicationConfigurable parentId="tools" .../>

// ✅ Language для language-specific
<applicationConfigurable parentId="language" .../>

// ❌ НЕ используйте "other" (deprecated)
```

### 3. Используйте SimplePersistentStateComponent

```kotlin
// ✅ Рекомендуется
@State(...)
class MySettings : SimplePersistentStateComponent<State>(State()) {
    class State : BaseState() {
        var value by string("default")
    }
}

// ❌ Больше boilerplate
@State(...)
class MySettings : PersistentStateComponent<State> {
    private var state = State()
    override fun getState() = state
    override fun loadState(s: State) { state = s }
}
```

### 4. Валидируйте критичные поля

```kotlin
row("API Key:") {
    textField()
        .bindText(settings.state::apiKey)
        .validationOnApply {
            if (it.text.isBlank()) {
                error("API Key is required")
            } else null
        }
}
```

### 5. Группируйте related settings

```kotlin
panel {
    group("Connection") {
        // Connection settings
    }
    
    group("Appearance") {
        // UI settings
    }
    
    group("Advanced") {
        // Advanced settings
    }
}
```

### 6. Добавляйте комментарии

```kotlin
row("Timeout:") {
    intTextField(range = 1..300)
        .bindIntText(settings.state::timeout)
        .comment("In seconds. Default: 30")
}
```

### 7. Reset to defaults button

```kotlin
panel {
    // ... settings ...
    
    separator()
    
    row {
        button("Reset to Defaults") {
            if (Messages.showYesNoDialog(
                "Reset all settings to defaults?",
                "Confirm Reset",
                Messages.getQuestionIcon()
            ) == Messages.YES) {
                settings.loadState(State())
                reset()
            }
        }
    }
}
```

## Common Patterns

### Settings с async loading

```kotlin
class LgSettingsConfigurable : BoundConfigurable("Settings") {
    
    private val settings = LgSettingsService.getInstance()
    private lateinit var encoderComboBox: Cell<ComboBox<String>>
    
    override fun createPanel() = panel {
        group("Tokenization") {
            row("Encoder:") {
                encoderComboBox = comboBox(listOf("Loading..."))
                    .bindItem(settings.state::defaultEncoder)
            }
        }
    }
    
    override fun reset() {
        super.reset()
        
        // Загрузить encoders асинхронно
        loadEncodersAsync()
    }
    
    private fun loadEncodersAsync() {
        scope.launch {
            val encoders = loadEncoders()
            
            withContext(Dispatchers.EDT) {
                val combo = encoderComboBox.component
                combo.removeAllItems()
                encoders.forEach { combo.addItem(it) }
                combo.selectedItem = settings.state.defaultEncoder
            }
        }
    }
}
```

### Settings с validation через external API

```kotlin
row("API Key:") {
    val field = textField()
        .bindText(settings.state::apiKey)
        .columns(40)
    
    button("Validate") {
        validateApiKey(field.component.text)
    }
}

private fun validateApiKey(key: String) {
    object : Task.Backgroundable(null, "Validating API Key...", false) {
        override fun run(indicator: ProgressIndicator) {
            val isValid = checkApiKeyOnServer(key)
            
            ApplicationManager.getApplication().invokeLater {
                if (isValid) {
                    Messages.showInfoMessage("API Key is valid", "Success")
                } else {
                    Messages.showErrorDialog("Invalid API Key", "Error")
                }
            }
        }
    }.queue()
}
```
