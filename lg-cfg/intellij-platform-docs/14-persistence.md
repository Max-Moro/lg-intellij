# Persistence — Сохранение состояния

## Обзор

IntelliJ Platform предоставляет несколько механизмов для сохранения состояния:

1. **PersistentStateComponent** — полнофункциональное сохранение (рекомендуется)
2. **PropertiesComponent** — простое key-value хранилище
3. **Legacy JDOMExternalizable** — устаревший API

## PersistentStateComponent

Основной механизм для персистентности сложных объектов.

### Архитектура

```
Service (implements PersistentStateComponent)
   ↓
State class (data to persist)
   ↓
XML Serialization
   ↓
XML File (on disk)
```

### Базовая структура

```kotlin
import com.intellij.openapi.components.*

@Service
@State(
    name = "MySettings",                       // XML root tag name
    storages = [Storage("myPlugin.xml")]       // File name
)
class MySettings : PersistentStateComponent<MySettings.State> {
    
    // State class
    class State {
        var apiKey: String = ""
        var timeout: Int = 30
        var enabled: Boolean = false
    }
    
    private var state = State()
    
    override fun getState(): State {
        return state
    }
    
    override fun loadState(state: State) {
        this.state = state
    }
    
    companion object {
        fun getInstance(): MySettings = service()
    }
}
```

## Kotlin: SimplePersistentStateComponent

**Рекомендуемый** подход для Kotlin с автоматическим tracking изменений.

```kotlin
@Service
@State(
    name = "LgSettings",
    storages = [Storage("lg-settings.xml")]
)
class LgSettingsService : SimplePersistentStateComponent<LgSettingsService.State>(State()) {
    
    class State : BaseState() {
        // Property delegates
        var cliPath by string("")
        var pythonInterpreter by string("")
        var defaultTokenizer by string("tiktoken")
        var defaultEncoder by string("cl100k_base")
        var contextLimit by property(128000)
        var openAsEditable by property(false)
        var recentTemplates by list<String>()
        var options by map<String, String>()
    }
    
    companion object {
        fun getInstance(): LgSettingsService = service()
    }
}
```

### BaseState Property Delegates

```kotlin
class State : BaseState() {
    
    // String
    var name by string()                    // Default: ""
    var title by string("Untitled")         // Default: "Untitled"
    
    // Primitives
    var count by property(0)                // Int
    var ratio by property(0.5)              // Double
    var enabled by property(false)          // Boolean
    var timestamp by property(0L)           // Long
    
    // Enums
    var mode by enum(Mode.AUTO)             // Default: Mode.AUTO
    
    // Collections
    var items by list<String>()             // MutableList<String>
    var tags by stringSet()                 // MutableSet<String>
    var properties by map<String, String>() // MutableMap<String, String>
    
    // Nullable
    var optional by string(null)            // String?
}

enum class Mode { AUTO, MANUAL }
```

### Модификация State

```kotlin
val settings = LgSettingsService.getInstance()

// Прямая модификация
settings.state.cliPath = "/custom/path"
settings.state.contextLimit = 256000

// Collections
settings.state.recentTemplates.add("new-template")
settings.state.options["key"] = "value"

// ⚠️ Для nested collections нужен incrementModificationCount()
settings.state.complexData.nested.add("item")
settings.state.incrementModificationCount() // Вручную!
```

## Kotlin: SerializablePersistentStateComponent

Immutable state подход (2022.2+):

```kotlin
@Service
@State(
    name = "LgConfig",
    storages = [Storage("lg-config.xml")]
)
class LgConfigService : SerializablePersistentStateComponent<LgConfigService.State>(State()) {
    
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
    
    // Immutable data class
    data class State(
        @JvmField val apiUrl: String = "https://api.example.com",
        @JvmField val timeout: Int = 30,
        @JvmField val features: List<String> = emptyList()
    )
}
```

**Преимущества:**
- Thread-safe (атомарные обновления)
- Автоматический modification tracking
- Immutable state (проще reasoning)

## Storage Locations

### @Storage annotation

```kotlin
@State(
    name = "MySettings",
    storages = [
        Storage("myPlugin.xml")  // Файл в config directory
    ]
)
```

### Storage Path Macros

```kotlin
import com.intellij.openapi.components.StoragePathMacros

// Workspace file (.idea/workspace.xml)
@Storage(StoragePathMacros.WORKSPACE_FILE)

// Project file (.ipr или .idea/misc.xml)
@Storage(StoragePathMacros.PROJECT_FILE)

// Cache file (.idea/caches/)
@Storage(StoragePathMacros.CACHE_FILE)

// Module file (.iml)
@Storage(StoragePathMacros.MODULE_FILE)
```

### Application vs Project Storage

```kotlin
// Application-level (IDE config dir)
@Service
@State(
    name = "LgAppSettings",
    storages = [Storage("lg-settings.xml")]
)
class LgAppSettings : SimplePersistentStateComponent<State>(State())

// Project-level (.idea/lg-project.xml)
@Service(Service.Level.PROJECT)
@State(
    name = "LgProjectSettings",
    storages = [Storage("lg-project.xml")]
)
class LgProjectSettings(project: Project) 
    : SimplePersistentStateComponent<State>(State())

// Project workspace (.idea/workspace.xml - не commit в VCS)
@Service(Service.Level.PROJECT)
@State(
    name = "LgWorkspace",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)]
)
class LgWorkspaceSettings(project: Project)
    : SimplePersistentStateComponent<State>(State())
```

### Где что хранить

| Тип данных | Storage | Sync | VCS |
|------------|---------|------|-----|
| User preferences | Application | ✓ | ✗ |
| Project config | Project file | ✓ | ✓ |
| UI state (selected tab, etc.) | Workspace | ✗ | ✗ |
| Caches | Cache file | ✗ | ✗ |
| Temporary data | PropertiesComponent | ✗ | ✗ |

## Roaming (синхронизация)

Settings могут синхронизироваться между установками IDE.

### RoamingType

```kotlin
@State(
    name = "LgSettings",
    storages = [
        Storage(
            value = "lg-settings.xml",
            roamingType = RoamingType.DEFAULT
        )
    ]
)
```

**Типы:**
- `DEFAULT` — синхронизировать между машинами ✅
- `PER_OS` — отдельно для каждой ОС
- `DISABLED` — не синхронизировать

**Когда использовать DISABLED:**
- Пути к файлам (машино-специфичные)
- Кэши
- Временные данные

**Когда использовать PER_OS:**
- OS-specific settings
- Keyboard shortcuts (разные на Mac/Win/Linux)

### Settings Category

```kotlin
@State(
    name = "LgSettings",
    storages = [Storage("lg-settings.xml")],
    category = SettingsCategory.TOOLS
)
```

**Категории:**
- `TOOLS` — инструменты (для LG ✓)
- `CODE` — код и форматирование
- `APPEARANCE` — внешний вид
- `SYSTEM` — системные
- `PLUGINS` — плагины
- `OTHER` — не синхронизируется

Категории используются в **Backup & Sync** plugin для выборочной синхронизации.

## XML Format Customization

### Annotations

```kotlin
import com.intellij.util.xmlb.annotations.*

class State {
    
    // Сохранить как attribute (не tag)
    @Attribute
    var id: String = ""
    
    // Кастомное имя тега
    @Tag("custom-name")
    var value: String = ""
    
    // Не сохранять
    @Transient
    var temporaryData: String = ""
    
    // Коллекция с кастомным форматом
    @XCollection(
        elementName = "item",
        valueAttributeName = "value"
    )
    var items: MutableList<String> = mutableListOf()
    
    // Map
    @MapAnnotation(
        entryTagName = "entry",
        keyAttributeName = "key",
        valueAttributeName = "value"
    )
    var options: MutableMap<String, String> = mutableMapOf()
}
```

### Custom Converter

```kotlin
import com.intellij.util.xmlb.Converter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class LocalDateTimeConverter : Converter<LocalDateTime>() {
    
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    
    override fun fromString(value: String): LocalDateTime {
        return LocalDateTime.parse(value, formatter)
    }
    
    override fun toString(value: LocalDateTime): String {
        return value.format(formatter)
    }
}

class State {
    @OptionTag(converter = LocalDateTimeConverter::class)
    var lastModified: LocalDateTime = LocalDateTime.now()
}
```

## PropertiesComponent

Простое key-value хранилище для **временных** данных.

### Application-level

```kotlin
import com.intellij.ide.util.PropertiesComponent

val props = PropertiesComponent.getInstance()

// String
props.setValue("com.example.lg.lastTemplate", "default")
val value = props.getValue("com.example.lg.lastTemplate")

// Boolean
props.setValue("com.example.lg.treeView", true)
val isTreeView = props.getBoolean("com.example.lg.treeView", false)

// Int
props.setValue("com.example.lg.count", 10)
val count = props.getInt("com.example.lg.count", 0)

// Float
props.setValue("com.example.lg.ratio", 0.5f)
val ratio = props.getFloat("com.example.lg.ratio", 0f)

// List (as comma-separated)
props.setValues("com.example.lg.items", arrayOf("a", "b", "c"))
val items = props.getValues("com.example.lg.items") // Array<String>?
```

### Project-level

```kotlin
val projectProps = PropertiesComponent.getInstance(project)

projectProps.setValue("com.example.lg.projectId", project.name)
val projectId = projectProps.getValue("com.example.lg.projectId")
```

**Важно:**
- Всегда используйте **префикс** (plugin ID)
- **Не синхронизируется** (roaming disabled)
- Только для **UI state**, не для настроек

## State Lifecycle

### Когда загружается state

1. **При создании service** — `loadState()` вызывается если есть сохранённый state
2. **При external изменении XML** — если файл изменён извне (VCS update)

```kotlin
override fun loadState(state: State) {
    this.state = state
    
    // Уведомить компоненты
    notifyStateLoaded()
}
```

### Когда сохраняется state

1. **Frame deactivation** — при переключении на другое приложение
2. **IDE close** — при закрытии IDE
3. **Project close** — при закрытии проекта
4. **Settings Apply** — при применении настроек

```kotlin
override fun getState(): State {
    // Вызывается при каждом save
    return state
}
```

**Optimization:** если state == default state → не сохраняется (пустой файл).

### Manual Save

```kotlin
// Force save сейчас
val stateComponent = service<MySettings>()
stateComponent.state.value = "new"

// Trigger save
AppSettingsState.getInstance().fireStateChanged()
```

## State Migration

При изменении структуры State нужна миграция.

### Version Field Pattern

```kotlin
@State(
    name = "LgSettings",
    storages = [Storage("lg-settings.xml")]
)
class LgSettings : SimplePersistentStateComponent<State>(State()) {
    
    class State : BaseState() {
        var version by property(2)          // Текущая версия
        
        // V2 fields
        var cliPath by string("")
        var timeout by property(30)
        
        // V1 fields (deprecated)
        @Deprecated("Migrated to cliPath")
        var legacyPath by string("")
    }
    
    override fun loadState(state: State) {
        super.loadState(state)
        
        // Миграция при загрузке старого формата
        if (state.version < 2) {
            migrateV1toV2(state)
            state.version = 2
        }
    }
    
    private fun migrateV1toV2(state: State) {
        if (state.cliPath.isBlank() && state.legacyPath.isNotBlank()) {
            state.cliPath = state.legacyPath
            state.legacyPath = ""
        }
    }
}
```

### ConverterProvider (сложные миграции)

Для миграции формата XML файлов:

```kotlin
import com.intellij.conversion.ConverterProvider
import com.intellij.conversion.ProjectConverter

class LgSettingsConverterProvider : ConverterProvider("lg-settings-v2") {
    
    override fun getConversionDescription() = 
        "Migrate Listing Generator settings to version 2"
    
    override fun createConverter(context: ConversionContext): ProjectConverter {
        return LgSettingsConverter(context)
    }
}

class LgSettingsConverter(
    private val context: ConversionContext
) : ProjectConverter() {
    
    override fun getAdditionalAffectedFiles(): Collection<File> {
        // Файлы для миграции
        return listOf(
            File(context.settingsBaseDir, "lg-settings.xml")
        )
    }
    
    override fun isConversionNeeded(): Boolean {
        val file = File(context.settingsBaseDir, "lg-settings.xml")
        if (!file.exists()) return false
        
        val content = file.readText()
        return !content.contains("version=\"2\"")
    }
    
    override fun process() {
        // Миграция XML
        val file = File(context.settingsBaseDir, "lg-settings.xml")
        val xml = JDOMUtil.load(file)
        
        // Transform XML
        migrateXml(xml)
        
        // Save
        JDOMUtil.write(xml, file)
    }
}
```

## Persisting Complex Types

### Collections

```kotlin
class State : BaseState() {
    
    // List
    var items by list<String>()
    
    var complexItems by list<Item>()
    
    // Set
    var tags by stringSet()
    
    // Map
    var options by map<String, String>()
    
    var complexMap by map<String, Item>()
}

// Nested class
class Item {
    var name: String = ""
    var value: Int = 0
}
```

**XML Output:**

```xml
<State>
    <items>
        <item value="item1"/>
        <item value="item2"/>
    </items>
    <complexItems>
        <Item>
            <name>Name1</name>
            <value>100</value>
        </Item>
    </complexItems>
    <tags>
        <tag value="tag1"/>
        <tag value="tag2"/>
    </tags>
    <options>
        <entry key="key1" value="value1"/>
        <entry key="key2" value="value2"/>
    </options>
</State>
```

### Custom Objects

```kotlin
import kotlinx.serialization.Serializable

@Serializable
data class AdvancedConfig(
    val host: String,
    val port: Int,
    val ssl: Boolean
)

class State : BaseState() {
    
    @get:Attribute
    @get:Tag("config")
    var config: AdvancedConfig? by property(null)
}
```

### JDOM Element (полный контроль)

Для полного контроля над XML:

```kotlin
@Service
@State(name = "ComplexSettings", storages = [Storage("complex.xml")])
class ComplexSettings : PersistentStateComponent<Element> {
    
    private var data = mapOf<String, Any>()
    
    override fun getState(): Element {
        val root = Element("settings")
        
        for ((key, value) in data) {
            val item = Element("item")
            item.setAttribute("key", key)
            item.setAttribute("value", value.toString())
            root.addContent(item)
        }
        
        return root
    }
    
    override fun loadState(element: Element) {
        data = element.getChildren("item").associate { item ->
            val key = item.getAttributeValue("key")
            val value = item.getAttributeValue("value")
            key to value
        }
    }
}
```

**Используйте только если:**
- Очень сложная структура
- Обратная совместимость с внешним форматом
- Иначе используйте BaseState delegates

## Application vs Project Storage

### Application-level State

```kotlin
@Service
@State(
    name = "LgGlobalSettings",
    storages = [Storage("lg-global.xml")]
)
class LgGlobalSettings : SimplePersistentStateComponent<State>(State()) {
    
    class State : BaseState() {
        var lastOpenedProject by string("")
        var recentFiles by list<String>()
    }
}
```

**Location:**
- Windows: `%APPDATA%\JetBrains\IntelliJIdea2024.1\options\lg-global.xml`
- macOS: `~/Library/Application Support/JetBrains/IntelliJIdea2024.1/options/lg-global.xml`
- Linux: `~/.config/JetBrains/IntelliJIdea2024.1/options/lg-global.xml`

### Project-level State

```kotlin
@Service(Service.Level.PROJECT)
@State(
    name = "LgProjectState",
    storages = [Storage("lg-project.xml")]
)
class LgProjectState(project: Project) 
    : SimplePersistentStateComponent<State>(State()) {
    
    class State : BaseState() {
        var selectedSection by string("all")
        var selectedTemplate by string("")
    }
}
```

**Location:** `.idea/lg-project.xml` (commit в VCS ✓)

### Workspace State (UI state)

```kotlin
@Service(Service.Level.PROJECT)
@State(
    name = "LgWorkspaceState",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)]
)
class LgWorkspaceState(project: Project)
    : SimplePersistentStateComponent<State>(State()) {
    
    class State : BaseState() {
        var toolWindowWidth by property(300)
        var selectedTab by property(0)
        var treeViewMode by property(true)
    }
}
```

**Location:** `.idea/workspace.xml` (НЕ commit в VCS ✗)

## Modification Tracking

### Automatic (SimplePersistentStateComponent)

```kotlin
val settings = LgSettings.getInstance()

settings.state.value = "new" // Tracked автоматически
```

### Manual (для nested modifications)

```kotlin
class State : BaseState() {
    var items by list<Item>()
}

val settings = MySettings.getInstance()

// Модификация вложенного объекта
settings.state.items.first().name = "new name"

// ⚠️ Нужно уведомить
settings.state.incrementModificationCount()
```

### isModified() для Configurable

```kotlin
class MyConfigurable : Configurable {
    
    private val settings = MySettings.getInstance()
    private val originalState = settings.state.copy()
    
    override fun isModified(): Boolean {
        return settings.state != originalState
    }
}
```

С `BoundConfigurable` и UI DSL — автоматически.

## State Defaults

### Default values в State class

```kotlin
class State : BaseState() {
    var timeout by property(30)           // Default: 30
    var enabled by property(false)        // Default: false
    var path by string("/default/path")   // Default: "/default/path"
}
```

### Проверка is-default

```kotlin
override fun getState(): State? {
    // Не сохранять если state == default
    if (state == State()) {
        return null // Пустой файл
    }
    return state
}
```

## Sensitive Data

Для **паролей и токенов** используйте **PasswordSafe**:

```kotlin
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials

fun saveApiKey(key: String) {
    val attributes = CredentialAttributes(
        serviceName = "com.example.lg.apiKey"
    )
    
    val credentials = Credentials(null, key)
    
    PasswordSafe.instance.set(attributes, credentials)
}

fun loadApiKey(): String? {
    val attributes = CredentialAttributes(
        serviceName = "com.example.lg.apiKey"
    )
    
    return PasswordSafe.instance.getPassword(attributes)
}
```

**НЕ храните** пароли в PersistentStateComponent!

## State с validation

```kotlin
@State(
    name = "LgSettings",
    storages = [Storage("lg-settings.xml")]
)
class LgSettings : SimplePersistentStateComponent<State>(State()) {
    
    class State : BaseState() {
        var port by property(8080)
        var timeout by property(30)
    }
    
    override fun loadState(state: State) {
        // Валидация при загрузке
        if (state.port !in 1..65535) {
            LOG.warn("Invalid port: ${state.port}, resetting to default")
            state.port = 8080
        }
        
        if (state.timeout < 1) {
            state.timeout = 30
        }
        
        super.loadState(state)
    }
    
    companion object {
        private val LOG = logger<LgSettings>()
    }
}
```

## Reacting to State Changes

### Through Message Bus

```kotlin
// Topic
interface SettingsListener {
    fun settingsChanged(newState: State)
    
    companion object {
        val TOPIC = Topic.create(
            "LG Settings",
            SettingsListener::class.java
        )
    }
}

// Service уведомляет об изменениях
@Service
@State(...)
class LgSettings : SimplePersistentStateComponent<State>(State()) {
    
    fun updateSetting(key: String, value: String) {
        state.options[key] = value
        
        // Notify listeners
        ApplicationManager.getApplication().messageBus
            .syncPublisher(SettingsListener.TOPIC)
            .settingsChanged(state)
    }
}

// Listener
@Service(Service.Level.PROJECT)
class LgCacheService(private val project: Project) : Disposable {
    
    init {
        ApplicationManager.getApplication().messageBus
            .connect(this)
            .subscribe(SettingsListener.TOPIC, object : SettingsListener {
                override fun settingsChanged(newState: State) {
                    invalidateCache()
                }
            })
    }
    
    override fun dispose() { }
}
```

## Testing Persistence

```kotlin
class PersistenceTest : BasePlatformTestCase() {
    
    fun testStateSaved() {
        val settings = LgSettings.getInstance()
        
        // Modify state
        settings.state.cliPath = "/test/path"
        settings.state.timeout = 60
        
        // Trigger save
        val serialized = XmlSerializer.serialize(settings.state)
        
        // Verify XML
        assertNotNull(serialized)
        
        // Load into new instance
        val newSettings = LgSettings()
        val deserialized = XmlSerializer.deserialize(
            serialized,
            State::class.java
        )
        newSettings.loadState(deserialized)
        
        // Verify
        assertEquals("/test/path", newSettings.state.cliPath)
        assertEquals(60, newSettings.state.timeout)
    }
}
```

## Performance Tips

### 1. Ленивая инициализация

```kotlin
// ❌ Загрузка в init
@Service
class MyService {
    private val data = loadFromState()
}

// ✅ Ленивая загрузка
@Service
class MyService {
    private val data by lazy { loadFromState() }
}
```

### 2. Избегайте тяжёлых state объектов

```kotlin
// ❌ Сохранение огромного кэша
class State : BaseState() {
    var cache by map<String, LargeObject>() // Плохо!
}

// ✅ Храните ссылки, данные в cache file
class State : BaseState() {
    var cacheKeys by stringSet()
}

// Данные в отдельном файле
@State(
    name = "LgCache",
    storages = [Storage(StoragePathMacros.CACHE_FILE)]
)
class LgCache : SimplePersistentStateComponent<State>(State())
```

### 3. Используйте @Transient для runtime data

```kotlin
class State : BaseState() {
    var savedValue by string("")
    
    @Transient
    var runtimeCache: Map<String, Any>? = null // Не сохраняется
}
```

## Common Patterns

### Settings Service Pattern

```kotlin
// Combine: PersistentStateComponent + public API
@Service
@State(
    name = "LgSettings",
    storages = [Storage("lg-settings.xml")],
    category = SettingsCategory.TOOLS
)
class LgSettingsService : SimplePersistentStateComponent<State>(State()) {
    
    class State : BaseState() {
        var cliPath by string("")
        var timeout by property(30)
    }
    
    // Public API
    fun getCliPath(): String = state.cliPath
    
    fun setCliPath(path: String) {
        state.cliPath = path
        notifyChanged()
    }
    
    fun getTimeout(): Int = state.timeout
    
    fun setTimeout(value: Int) {
        require(value > 0) { "Timeout must be positive" }
        state.timeout = value
        notifyChanged()
    }
    
    private fun notifyChanged() {
        // Notify listeners
    }
    
    companion object {
        fun getInstance(): LgSettingsService = service()
    }
}
```

### Recent Items Pattern

```kotlin
@Service
@State(
    name = "LgRecentItems",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)]
)
class LgRecentItems : SimplePersistentStateComponent<State>(State()) {
    
    class State : BaseState() {
        var recentTemplates by list<String>()
        var recentSections by list<String>()
    }
    
    fun addRecentTemplate(template: String) {
        val list = state.recentTemplates
        
        // Remove if exists (для reordering)
        list.remove(template)
        
        // Add to front
        list.add(0, template)
        
        // Limit size
        while (list.size > 10) {
            list.removeAt(list.size - 1)
        }
    }
    
    fun getRecentTemplates(): List<String> {
        return state.recentTemplates.toList()
    }
}
```

### Cache Pattern

```kotlin
@Service
@State(
    name = "LgCache",
    storages = [
        Storage(
            value = StoragePathMacros.CACHE_FILE,
            roamingType = RoamingType.DISABLED
        )
    ]
)
class LgCacheService : SimplePersistentStateComponent<State>(State()) {
    
    class State : BaseState() {
        var encoderCache by map<String, Long>() // Encoder -> timestamp
        var configHash by string("")
    }
    
    fun isCached(encoder: String): Boolean {
        val timestamp = state.encoderCache[encoder] ?: return false
        val age = System.currentTimeMillis() - timestamp
        return age < 86400_000 // 24 hours
    }
    
    fun markCached(encoder: String) {
        state.encoderCache[encoder] = System.currentTimeMillis()
    }
    
    fun clearCache() {
        state.encoderCache.clear()
        state.configHash = ""
    }
}
```

## Best Practices

### 1. Используйте SimplePersistentStateComponent (Kotlin)

```kotlin
// ✅ Рекомендуется
class MySettings : SimplePersistentStateComponent<State>(State())

// ❌ Больше кода
class MySettings : PersistentStateComponent<State> {
    private var state = State()
    override fun getState() = state
    override fun loadState(s: State) { state = s }
}
```

### 2. Правильный storage для типа данных

```kotlin
// ✅ Settings → собственный файл
@Storage("lg-settings.xml")

// ✅ UI state → workspace file
@Storage(StoragePathMacros.WORKSPACE_FILE)

// ✅ Cache → cache file
@Storage(StoragePathMacros.CACHE_FILE)
```

### 3. Версионируйте State

```kotlin
class State : BaseState() {
    var version by property(1) // Для миграций
    // ... остальные поля
}
```

### 4. Validate при loadState

```kotlin
override fun loadState(state: State) {
    // Sanitize
    if (state.timeout < 1) state.timeout = 30
    if (state.port !in 1..65535) state.port = 8080
    
    super.loadState(state)
}
```

### 5. Не храните sensitive data

```kotlin
// ❌ НЕБЕЗОПАСНО
class State : BaseState() {
    var password by string("") // В plaintext!
}

// ✅ Используйте PasswordSafe
fun savePassword(password: String) {
    PasswordSafe.instance.setPassword(
        CredentialAttributes("com.example.lg.password"),
        password
    )
}
```

### 6. Префикс для PropertiesComponent

```kotlin
// ✅ С префиксом
props.setValue("com.example.lg.lastValue", "value")

// ❌ Без префикса (конфликты!)
props.setValue("lastValue", "value")
```
