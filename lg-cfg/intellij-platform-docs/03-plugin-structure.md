# Структура плагина IntelliJ Platform

## Общая структура проекта

Типичный Gradle-based плагин имеет следующую структуру:

```
my-plugin/
├── build.gradle.kts              # Gradle build configuration
├── gradle.properties             # Gradle properties
├── settings.gradle.kts           # Gradle settings
├── gradle/
│   └── wrapper/                  # Gradle Wrapper files
├── src/
│   ├── main/
│   │   ├── kotlin/               # Исходный код (Kotlin)
│   │   │   └── com/example/
│   │   │       ├── actions/      # Actions
│   │   │       ├── services/     # Services
│   │   │       ├── ui/           # UI components
│   │   │       └── settings/     # Settings
│   │   ├── java/                 # Исходный код (Java)
│   │   └── resources/
│   │       ├── META-INF/
│   │       │   ├── plugin.xml           # Конфигурация плагина ⭐
│   │       │   ├── withJava.xml         # Optional config
│   │       │   └── pluginIcon.svg       # Иконка плагина
│   │       ├── messages/
│   │       │   └── MyBundle.properties  # i18n ресурсы
│   │       └── icons/
│   │           └── myIcon.svg           # Иконки
│   └── test/
│       ├── kotlin/                # Тесты
│       └── resources/
│           └── testData/          # Тестовые данные
└── README.md
```

## Файл конфигурации plugin.xml

`plugin.xml` — центральный конфигурационный файл плагина. Содержит всю информацию о плагине и его расширениях.

### Минимальный plugin.xml

```xml
<idea-plugin>
    <!-- Уникальный идентификатор (обязательно) -->
    <id>com.example.myplugin</id>
    
    <!-- Отображаемое имя (обязательно) -->
    <name>My Plugin</name>
    
    <!-- Версия (обязательно) -->
    <version>1.0.0</version>
    
    <!-- Информация о разработчике (обязательно) -->
    <vendor email="support@example.com" url="https://example.com">
        Example Company
    </vendor>
    
    <!-- Описание (обязательно) -->
    <description><![CDATA[
        Short description of the plugin.
        <br/>
        Supports <b>HTML</b> <em>formatting</em>.
    ]]></description>
    
    <!-- Совместимость с версиями IDE (обязательно) -->
    <idea-version since-build="241" until-build="243.*"/>
    
    <!-- Зависимость от платформы (обязательно) -->
    <depends>com.intellij.modules.platform</depends>
</idea-plugin>
```

### Полная структура plugin.xml

```xml
<idea-plugin url="https://example.com/myplugin">
    
    <!-- ========== БАЗОВАЯ ИНФОРМАЦИЯ ========== -->
    
    <id>com.example.myplugin</id>
    <name>My Plugin</name>
    <version>1.0.0</version>
    
    <vendor 
        email="support@example.com" 
        url="https://example.com">
        Example Company
    </vendor>
    
    <description><![CDATA[
        Detailed plugin description with <b>HTML</b> support.
        <ul>
            <li>Feature 1</li>
            <li>Feature 2</li>
        </ul>
    ]]></description>
    
    <change-notes><![CDATA[
        <h3>Version 1.0.0</h3>
        <ul>
            <li>Initial release</li>
        </ul>
    ]]></change-notes>
    
    <!-- ========== СОВМЕСТИМОСТЬ ========== -->
    
    <!-- Версии IDE -->
    <idea-version since-build="241" until-build="243.*"/>
    
    <!-- Зависимости от модулей/плагинов -->
    <depends>com.intellij.modules.platform</depends>
    <depends>com.intellij.modules.lang</depends>
    
    <!-- Опциональные зависимости -->
    <depends 
        optional="true" 
        config-file="withJava.xml">
        com.intellij.modules.java
    </depends>
    
    <!-- Несовместимость с плагином -->
    <incompatible-with>com.example.conflictingplugin</incompatible-with>
    
    <!-- ========== РЕСУРСЫ ========== -->
    
    <!-- Message bundle для i18n -->
    <resource-bundle>messages.MyBundle</resource-bundle>
    
    <!-- ========== EXTENSION POINTS ========== -->
    
    <!-- Собственные Extension Points -->
    <extensionPoints>
        <extensionPoint 
            name="myExtension"
            interface="com.example.MyExtensionPoint"/>
    </extensionPoints>
    
    <!-- ========== EXTENSIONS ========== -->
    
    <extensions defaultExtensionNs="com.intellij">
        
        <!-- Services -->
        <applicationService 
            serviceImplementation="com.example.MyAppService"/>
        <projectService 
            serviceImplementation="com.example.MyProjectService"/>
        
        <!-- Tool Window -->
        <toolWindow 
            id="My Tool Window"
            anchor="right"
            icon="AllIcons.Toolwindows.Documentation"
            factoryClass="com.example.MyToolWindowFactory"/>
        
        <!-- Settings -->
        <applicationConfigurable 
            parentId="tools"
            instance="com.example.MySettingsConfigurable"
            id="com.example.MySettingsConfigurable"
            displayName="My Plugin Settings"/>
        
        <!-- Notification Group -->
        <notificationGroup 
            id="My Notification Group"
            displayType="BALLOON"/>
    </extensions>
    
    <!-- ========== ACTIONS ========== -->
    
    <actions>
        <action 
            id="com.example.MyAction"
            class="com.example.MyAction"
            text="My Action"
            description="Performs my action"
            icon="AllIcons.Actions.Execute">
            
            <add-to-group 
                group-id="ToolsMenu" 
                anchor="first"/>
            <keyboard-shortcut 
                keymap="$default" 
                first-keystroke="control alt M"/>
        </action>
        
        <group 
            id="com.example.MyGroup"
            text="My Tools"
            popup="true">
            
            <action id="..." class="..."/>
            <separator/>
            <action id="..." class="..."/>
            
            <add-to-group 
                group-id="ToolsMenu" 
                anchor="last"/>
        </group>
    </actions>
    
    <!-- ========== LISTENERS ========== -->
    
    <!-- Application-level listeners -->
    <applicationListeners>
        <listener 
            topic="com.intellij.openapi.project.ProjectManagerListener"
            class="com.example.MyProjectListener"/>
    </applicationListeners>
    
    <!-- Project-level listeners -->
    <projectListeners>
        <listener 
            topic="com.intellij.openapi.vfs.newvfs.BulkFileListener"
            class="com.example.MyFileListener"/>
    </projectListeners>
    
    <!-- ========== INCLUDES ========== -->
    
    <!-- Включение дополнительных конфигов -->
    <xi:include 
        href="/META-INF/additional-config.xml"
        xmlns:xi="http://www.w3.org/2001/XInclude"/>
    
</idea-plugin>
```

## Обязательные элементы

### `<id>` — Plugin ID

**Требования:**
- Уникальный идентификатор (как Java package)
- Формат: `com.company.pluginname`
- Только буквы, цифры, `.`, `-`, `_`
- **Нельзя изменить** после публикации

```xml
<id>com.example.listinggenerator</id>
```

### `<name>` — Display Name

**Требования:**
- Видимое пользователю имя
- Title Case
- Без "Plugin" в конце (добавляется автоматически)

```xml
<name>Listing Generator</name>
```

### `<version>` — Version

**Требования:**
- Semantic Versioning: `MAJOR.MINOR.PATCH`
- Для Marketplace обязательно следовать SemVer

```xml
<version>1.2.3</version>
```

Можно патчить через Gradle:
```kotlin
intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("pluginVersion")
    }
}
```

### `<vendor>` — Vendor Info

```xml
<vendor 
    email="support@example.com" 
    url="https://example.com">
    Example Company
</vendor>
```

Или для личного разработчика:
```xml
<vendor email="john@example.com">John Doe</vendor>
```

### `<description>` — Description

**Требования:**
- Детальное описание функциональности
- Поддержка HTML (обязательно в `<![CDATA[ ]]>`)
- Отображается на странице плагина в Marketplace

```xml
<description><![CDATA[
    Provides integration with the Listing Generator CLI tool.
    <br/>
    <h3>Features:</h3>
    <ul>
        <li>Generate code listings</li>
        <li>Create AI prompts from code</li>
        <li>Manage sections and contexts</li>
    </ul>
    <p>
    Visit <a href="https://example.com/docs">documentation</a> 
    for more info.
    </p>
]]></description>
```

### `<idea-version>` — Compatibility

```xml
<!-- Конкретная версия и выше -->
<idea-version since-build="241"/>

<!-- Диапазон версий -->
<idea-version since-build="241" until-build="243.*"/>

<!-- Точная версия (редко используется) -->
<idea-version since-build="241.14494" until-build="241.14494"/>
```

Формат build number: `BRANCH.BUILD.PATCH`
- `241` = 2024.1
- `242` = 2024.2
- `243` = 2024.3
- Wildcard `*` = все патчи в ветке

### `<depends>` — Dependencies

```xml
<!-- Обязательная зависимость от платформы -->
<depends>com.intellij.modules.platform</depends>

<!-- Для Language плагинов -->
<depends>com.intellij.modules.lang</depends>

<!-- Для Java плагинов -->
<depends>com.intellij.modules.java</depends>

<!-- Зависимость от другого плагина -->
<depends>org.jetbrains.kotlin</depends>

<!-- Опциональная зависимость с доп. конфигом -->
<depends 
    optional="true" 
    config-file="withPython.xml">
    com.intellij.modules.python
</depends>
```

## Дополнительные конфигурационные файлы

Плагин может иметь несколько `plugin.xml` файлов для условной функциональности.

### Основной plugin.xml

```xml
<idea-plugin>
    <id>com.example.myplugin</id>
    <name>My Plugin</name>
    
    <depends>com.intellij.modules.platform</depends>
    
    <!-- Опциональная Java поддержка -->
    <depends 
        optional="true" 
        config-file="withJava.xml">
        com.intellij.modules.java
    </depends>
    
    <!-- Базовые расширения -->
    <extensions defaultExtensionNs="com.intellij">
        <toolWindow id="My Tool" .../>
    </extensions>
</idea-plugin>
```

### withJava.xml (опциональный)

```xml
<idea-plugin>
    <!-- Расширения, требующие Java plugin -->
    <extensions defaultExtensionNs="com.intellij">
        <configurationType 
            implementation="com.example.JavaRunConfiguration"/>
    </extensions>
    
    <actions>
        <action 
            id="com.example.JavaSpecificAction"
            class="com.example.JavaSpecificAction">
            <add-to-group group-id="JavaMenu"/>
        </action>
    </actions>
</idea-plugin>
```

Этот файл загружается **только если** Java plugin установлен и включён.

## Локализация

### Локализация plugin.xml атрибутов

Вместо hardcoded текстов используйте message bundles:

```xml
<!-- Регистрация bundle -->
<resource-bundle>messages.MyBundle</resource-bundle>

<!-- Локализация через key -->
<applicationConfigurable
    parentId="tools"
    instance="com.example.MySettings"
    id="com.example.MySettings"
    key="settings.display.name"
    bundle="messages.MyBundle"/>
```

```properties
# messages/MyBundle.properties (English - default)
settings.display.name=My Plugin Settings
action.generate.text=Generate Listing
action.generate.description=Generate code listing from current file

# messages/MyBundle_ru.properties (Russian)
settings.display.name=Настройки плагина
action.generate.text=Сгенерировать листинг
action.generate.description=Генерация листинга кода из текущего файла
```

### Локализация Actions

Для Actions без явного `text` атрибута:

```xml
<actions resource-bundle="messages.ActionsBundle">
    <action 
        id="com.example.MyAction"
        class="com.example.MyAction"/>
</actions>
```

```properties
# messages/ActionsBundle.properties
action.com.example.MyAction.text=My Action
action.com.example.MyAction.description=Performs useful operation
```

Формат ключей:
- `action.<action-id>.text` — текст действия
- `action.<action-id>.description` — описание (в статус-баре)

### Локализация Tool Windows

```xml
<toolWindow 
    id="My Tool Window"
    factoryClass="..."/>
```

```properties
# messages/MyBundle.properties
toolwindow.stripe.My_Tool_Window=My Tool Window
```

**Важно:** пробелы в ID заменяются на `_` в ключе.

## Dependencies (Зависимости)

### Типы зависимостей

#### 1. Module Dependencies (базовые модули платформы)

Стандартные модули:
- `com.intellij.modules.platform` — базовая платформа (всегда)
- `com.intellij.modules.lang` — language support
- `com.intellij.modules.vcs` — VCS support
- `com.intellij.modules.xml` — XML support
- `com.intellij.modules.xdebugger` — debugger API

```xml
<depends>com.intellij.modules.platform</depends>
<depends>com.intellij.modules.lang</depends>
```

#### 2. Plugin Dependencies (другие плагины)

```xml
<!-- Java plugin -->
<depends>com.intellij.java</depends>

<!-- Kotlin plugin -->
<depends>org.jetbrains.kotlin</depends>

<!-- Python plugin -->
<depends>com.intellij.modules.python</depends>
```

#### 3. Optional Dependencies

```xml
<depends 
    optional="true" 
    config-file="withKotlin.xml">
    org.jetbrains.kotlin
</depends>
```

**Когда использовать optional:**
- Функциональность работает без зависимости
- Дополнительные features если зависимость установлена
- Поддержка разных IDE (некоторые без Java plugin)

### Gradle Dependencies

Соответствующие зависимости в `build.gradle.kts`:

```kotlin
dependencies {
    intellijPlatform {
        // Базовая IDE
        intellijIdeaCommunity("2024.1")
        
        // Встроенные плагины
        bundledPlugins(
            "com.intellij.java",
            "org.jetbrains.kotlin"
        )
        
        // Плагины из Marketplace
        plugin("com.github.copilot", "1.5.0")
        
        // Для тестирования
        testFramework(TestFrameworkType.Platform)
        pluginVerifier()
    }
}
```

## Extensions (Расширения)

Extensions регистрируются в блоке `<extensions>`.

### Namespace

```xml
<!-- С default namespace -->
<extensions defaultExtensionNs="com.intellij">
    <applicationService serviceImplementation="..."/>
    <toolWindow id="..." factoryClass="..."/>
</extensions>

<!-- Без default namespace (fully qualified) -->
<extensions>
    <com.intellij.applicationService serviceImplementation="..."/>
    <com.example.pluginA.myExtension implementation="..."/>
</extensions>
```

### Общие атрибуты Extensions

Все Extensions поддерживают базовые атрибуты:

#### `id` — идентификатор extension
```xml
<applicationService 
    id="com.example.MyService"
    serviceImplementation="..."/>
```

#### `order` — порядок загрузки

```xml
<!-- Первым -->
<myExtension order="first" implementation="..."/>

<!-- Последним -->
<myExtension order="last" implementation="..."/>

<!-- Перед другой extension -->
<myExtension order="before otherExtensionId" implementation="..."/>

<!-- После другой extension -->
<myExtension order="after otherExtensionId" implementation="..."/>

<!-- Комбинация -->
<myExtension order="after extA, before extB" implementation="..."/>
```

#### `os` — ограничение по ОС

```xml
<!-- Только Windows -->
<action os="windows" .../>

<!-- Только macOS -->
<toolWindow os="mac" .../>

<!-- Только Unix (Linux, macOS, FreeBSD) -->
<applicationService os="unix" .../>
```

Допустимые значения: `windows`, `mac`, `linux`, `unix`, `freebsd`

### Основные Extension Points

#### Services

```xml
<extensions defaultExtensionNs="com.intellij">
    <!-- Application service -->
    <applicationService 
        serviceInterface="com.example.MyService"
        serviceImplementation="com.example.MyServiceImpl"/>
    
    <!-- Project service -->
    <projectService 
        serviceInterface="com.example.MyProjectService"
        serviceImplementation="com.example.MyProjectServiceImpl"/>
    
    <!-- Module service (deprecated) -->
    <moduleService 
        serviceInterface="com.example.MyModuleService"
        serviceImplementation="com.example.MyModuleServiceImpl"/>
</extensions>
```

**Light Services** не требуют регистрации — используйте `@Service` аннотацию.

#### Tool Windows

```xml
<toolWindow 
    id="Listing Generator"
    anchor="right"
    secondary="false"
    icon="icons.LgIcons.ToolWindow"
    factoryClass="com.example.lg.ui.LgToolWindowFactory"/>
```

Атрибуты:
- `id` — уникальный ID (отображается если не локализован)
- `anchor` — позиция: `left`, `right`, `bottom`
- `secondary` — primary или secondary группа
- `icon` — иконка на полосе
- `factoryClass` — фабрика для создания содержимого

#### Settings (Configurable)

```xml
<applicationConfigurable 
    parentId="tools"
    instance="com.example.MySettingsConfigurable"
    id="com.example.MySettingsConfigurable"
    displayName="My Settings"/>

<projectConfigurable 
    parentId="tools"
    instance="com.example.MyProjectSettings"
    id="com.example.MyProjectSettings"
    key="settings.project.display.name"
    bundle="messages.MyBundle"
    nonDefaultProject="true"/>
```

Атрибуты:
- `parentId` — родительская группа (`appearance`, `editor`, `build`, `language`, `tools`)
- `instance` — FQN класса Configurable
- `displayName` — отображаемое имя (или `key` + `bundle` для i18n)
- `nonDefaultProject` — не показывать для default project (project-level only)

#### Notification Groups

```xml
<notificationGroup 
    id="Listing Generator Notifications"
    displayType="BALLOON"
    key="notification.group.name"
    bundle="messages.MyBundle"/>
```

`displayType`:
- `BALLOON` — всплывающий balloon (рекомендуется)
- `TOOL_WINDOW` — в tool window
- `STICKY_BALLOON` — balloon не исчезает автоматически

## Actions (Действия)

Actions определяются в блоке `<actions>`.

### Простой Action

```xml
<action 
    id="com.example.GenerateListing"
    class="com.example.actions.GenerateListingAction"
    text="Generate Listing"
    description="Generate code listing from current selection"
    icon="AllIcons.Actions.Export">
    
    <!-- Добавить в меню -->
    <add-to-group 
        group-id="EditorPopupMenu" 
        anchor="last"/>
    
    <!-- Keyboard shortcut -->
    <keyboard-shortcut 
        keymap="$default" 
        first-keystroke="control alt G"/>
</action>
```

### Action с локализацией

```xml
<!-- Без text атрибута -->
<action 
    id="com.example.MyAction"
    class="com.example.MyAction"
    icon="AllIcons.Actions.Execute">
    <add-to-group group-id="ToolsMenu"/>
</action>
```

```properties
# messages/MyBundle.properties
action.com.example.MyAction.text=My Action
action.com.example.MyAction.description=Does something useful
```

### Action Groups

```xml
<group 
    id="com.example.LgGroup"
    text="Listing Generator"
    description="Listing Generator actions"
    popup="true"
    icon="icons.LgIcons.Group">
    
    <action id="com.example.Generate" class="..."/>
    <action id="com.example.Config" class="..."/>
    <separator/>
    <action id="com.example.Help" class="..."/>
    
    <add-to-group 
        group-id="ToolsMenu" 
        anchor="last"/>
</group>
```

Атрибуты:
- `popup="true"` — показать как submenu
- `popup="false"` — показать inline с separator
- `compact="true"` — скрыть disabled actions
- `searchable="false"` — не показывать в Find Action

### Keyboard Shortcuts

```xml
<keyboard-shortcut 
    keymap="$default"
    first-keystroke="control alt G"
    second-keystroke="C"/>
```

Keymaps:
- `$default` — все keymaps
- `Mac OS X` — только macOS
- `Windows` — только Windows
- `Default for XWin` — только Linux

Modifiers:
- `control` — Ctrl (Cmd на Mac)
- `alt` — Alt (Option на Mac)
- `shift` — Shift
- `meta` — Cmd на Mac, Win на Windows

### Mouse Shortcuts

```xml
<mouse-shortcut 
    keymap="$default"
    keystroke="control button3 doubleClick"/>
```

### Override Text (контекстный текст)

```xml
<action 
    id="com.example.MyAction"
    class="..."
    text="Long Action Name: Do Something">
    
    <!-- Короткий текст для MainMenu -->
    <override-text 
        place="MainMenu" 
        text="Do Something"/>
    
    <!-- Переиспользовать текст MainMenu -->
    <override-text 
        place="EditorPopup" 
        use-text-of-place="MainMenu"/>
</action>
```

### Synonyms и Abbreviations

Для улучшения поиска в **Find Action** (Ctrl+Shift+A):

```xml
<action 
    id="com.example.Generate"
    class="..."
    text="Generate Listing">
    
    <!-- Синонимы для поиска -->
    <synonym text="Create Listing"/>
    <synonym text="Export Code"/>
    
    <!-- Аббревиатура -->
    <abbreviation value="gl"/>
</action>
```

Теперь action можно найти по:
- "Generate Listing" (основной текст)
- "Create Listing" (synonym)
- "Export Code" (synonym)
- "gl" (abbreviation)

## Extension Points (свои точки расширения)

Если ваш плагин предоставляет API для других плагинов, объявите Extension Points.

### Объявление Extension Point

```xml
<extensionPoints>
    <!-- Interface-based EP -->
    <extensionPoint 
        name="myProcessor"
        interface="com.example.MyProcessor"/>
    
    <!-- Bean-based EP -->
    <extensionPoint 
        name="myProvider"
        beanClass="com.example.MyProviderBean">
        <with 
            attribute="implementation"
            implements="com.example.MyProvider"/>
    </extensionPoint>
    
    <!-- Dynamic EP (для dynamic plugins) -->
    <extensionPoint 
        name="myDynamicEP"
        interface="com.example.MyExtension"
        dynamic="true"/>
    
    <!-- Project-level EP -->
    <extensionPoint 
        name="myProjectEP"
        interface="com.example.MyProjectExtension"
        area="IDEA_PROJECT"/>
</extensionPoints>
```

Атрибуты:
- `name` — short name (будет `com.example.myplugin.myProcessor`)
- `qualifiedName` — full name (альтернатива `name`)
- `interface` — интерфейс для реализации
- `beanClass` — bean класс с @Attribute полями
- `dynamic` — поддержка dynamic loading/unloading
- `area` — scope: `IDEA_APPLICATION` (default), `IDEA_PROJECT`

### Использование Extension Point

Другие плагины регистрируют расширения:

```xml
<!-- В другом плагине -->
<depends>com.example.myplugin</depends>

<extensions defaultExtensionNs="com.example.myplugin">
    <myProcessor implementation="com.other.MyProcessorImpl"/>
</extensions>
```

### Получение Extensions в коде

```kotlin
// Получить все зарегистрированные extensions
val processors = ExtensionPointName<MyProcessor>(
    "com.example.myplugin.myProcessor"
).extensionList

// Использовать
for (processor in processors) {
    processor.process(data)
}
```

Или статичное объявление:
```kotlin
companion object {
    private val EP_NAME = ExtensionPointName<MyProcessor>(
        "com.example.myplugin.myProcessor"
    )
}

fun processAll(data: Data) {
    EP_NAME.extensionList.forEach { it.process(data) }
}
```

## Listeners (Слушатели событий)

### Типы Listeners

#### Application-level

```xml
<applicationListeners>
    <listener 
        topic="com.intellij.ide.AppLifecycleListener"
        class="com.example.MyAppListener"
        activeInTestMode="false"
        activeInHeadlessMode="false"/>
</applicationListeners>
```

```kotlin
class MyAppListener : AppLifecycleListener {
    override fun appStarted() {
        // IDE started
    }
    
    override fun appWillBeClosed(isRestart: Boolean) {
        // IDE closing
    }
}
```

#### Project-level

```xml
<projectListeners>
    <listener 
        topic="com.intellij.openapi.vfs.newvfs.BulkFileListener"
        class="com.example.MyFileListener"/>
</projectListeners>
```

```kotlin
class MyFileListener : BulkFileListener {
    override fun after(events: List<VFileEvent>) {
        for (event in events) {
            when (event) {
                is VFileCreateEvent -> // File created
                is VFileDeleteEvent -> // File deleted
                is VFileContentChangeEvent -> // Content changed
            }
        }
    }
}
```

### Programmatic Registration

```kotlin
class MyService(private val project: Project) : Disposable {
    
    init {
        // Subscribe to topic
        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun fileOpened(
                    source: FileEditorManager,
                    file: VirtualFile
                ) {
                    // File opened
                }
            }
        )
    }
    
    override fun dispose() {
        // Auto-unsubscribe через connect(disposable)
    }
}
```

## XInclude (модульность конфигурации)

Для больших плагинов можно разбить `plugin.xml` на части:

### Главный plugin.xml

```xml
<idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
    <id>com.example.bigplugin</id>
    <name>Big Plugin</name>
    
    <!-- Включить другие конфиги -->
    <xi:include href="/META-INF/actions.xml"/>
    <xi:include href="/META-INF/services.xml"/>
    <xi:include href="/META-INF/ui.xml"/>
</idea-plugin>
```

### actions.xml

```xml
<idea-plugin>
    <actions>
        <action id="..." class="..."/>
        <group id="..." ...>...</group>
    </actions>
</idea-plugin>
```

### services.xml

```xml
<idea-plugin>
    <extensions defaultExtensionNs="com.intellij">
        <applicationService .../>
        <projectService .../>
    </extensions>
</idea-plugin>
```

## Dynamic Plugins

**Dynamic Plugins** — плагины без перезагрузки IDE.

### Требования

В `plugin.xml`:
```xml
<idea-plugin require-restart="false">
    <!-- ... -->
</idea-plugin>
```

**Или по умолчанию** (если атрибут не указан, считается `false`).

### Что нужно для dynamic plugin:

1. **Не использовать Components** — только Services
   ```kotlin
   // ❌ Deprecated
   class MyComponent : ApplicationComponent
   
   // ✅ Modern
   @Service
   class MyService
   ```

2. **Использовать Disposable** для cleanup
   ```kotlin
   @Service
   class MyService : Disposable {
       private val connection = createConnection()
       
       override fun dispose() {
           connection.close()
       }
   }
   ```

3. **Extension Points должны быть dynamic**
   ```xml
   <extensionPoint 
       name="myEP"
       interface="..."
       dynamic="true"/>
   ```

4. **Listeners должны unregister** при dispose
   - Через `connect(disposable)` — автоматически
   - Или явно в `dispose()`

5. **Избегать статических полей** с ссылками на Project/Module

### Тестирование dynamic behaviour

```kotlin
class MyPluginTest : BasePlatformTestCase() {
    
    fun testDynamicUnload() {
        // Эмуляция unload плагина
        val pluginDescriptor = PluginManagerCore.getPlugin(
            PluginId.getId("com.example.myplugin")
        )!!
        
        DynamicPlugins.unloadPlugin(pluginDescriptor)
        
        // Проверить что ресурсы освобождены
        assertNull(service<MyService>())
    }
}
```

## Plugin Icons

### Основная иконка плагина

`src/main/resources/META-INF/pluginIcon.svg`:
- **Размер:** 40×40 px (или 80×80 для Retina)
- **Формат:** SVG (предпочтительно) или PNG
- **Цвета:** поддержка dark/light themes

### Иконки для UI элементов

`src/main/kotlin/icons/MyIcons.kt`:
```kotlin
package icons

import com.intellij.openapi.util.IconLoader

object MyIcons {
    @JvmField
    val ToolWindow = IconLoader.getIcon(
        "/icons/toolWindow.svg",
        MyIcons::class.java
    )
    
    @JvmField
    val Generate = IconLoader.getIcon(
        "/icons/generate.svg",
        MyIcons::class.java
    )
}
```

Использование:
```xml
<toolWindow icon="icons.MyIcons.ToolWindow" .../>
<action icon="icons.MyIcons.Generate" .../>
```

Или используйте платформенные иконки:
```xml
<action icon="AllIcons.Actions.Execute" .../>
<toolWindow icon="AllIcons.Toolwindows.Documentation" .../>
```

Все доступные иконки: [`AllIcons`](https://github.com/JetBrains/intellij-community/blob/master/platform/util/ui/src/com/intellij/icons/AllIcons.java)

## Best Practices для plugin.xml

### 1. Используйте осмысленные ID

```xml
<!-- ❌ Плохо -->
<action id="action1" .../>

<!-- ✅ Хорошо -->
<action id="com.example.myplugin.GenerateListingAction" .../>
```

### 2. Указывайте until-build

```xml
<!-- ❌ Рискованно -->
<idea-version since-build="241"/>

<!-- ✅ Безопасно -->
<idea-version since-build="241" until-build="243.*"/>
```

### 3. Используйте Light Services

```kotlin
// ✅ Не требует регистрации
@Service
class MyService

// ❌ Требует регистрации в plugin.xml
<applicationService serviceImplementation="..."/>
```

### 4. Локализуйте текст

```xml
<!-- ❌ Hardcoded -->
<action text="Generate Listing" .../>

<!-- ✅ Локализованный -->
<action 
    id="com.example.Generate"
    class="..."/>
```

```properties
action.com.example.Generate.text=Generate Listing
action.com.example.Generate.text_ru=Сгенерировать листинг
```

### 5. Группируйте related items

```xml
<!-- ✅ Логичная группировка -->
<actions>
    <group id="com.example.MainGroup" popup="true">
        <action id="..."/>
        <action id="..."/>
    </group>
</actions>
```

### 6. Документируйте с помощью комментариев

```xml
<idea-plugin>
    <!-- ========== CORE EXTENSIONS ========== -->
    <extensions defaultExtensionNs="com.intellij">
        <!-- CLI Integration Service -->
        <applicationService .../>
        
        <!-- Main Tool Window -->
        <toolWindow .../>
    </extensions>
    
    <!-- ========== ACTIONS ========== -->
    <actions>
        <!-- Generate actions -->
        <action .../>
    </actions>
</idea-plugin>
```

## Валидация plugin.xml

### Plugin DevKit Inspections

IntelliJ IDEA автоматически проверяет `plugin.xml`:
- Несуществующие классы
- Неправильные Extension Point references
- Дублирующиеся ID
- Missing required attributes

### Gradle Verification

```bash
./gradlew verifyPluginConfiguration
```

Проверяет:
- Корректность plugin.xml
- Совместимость since-build/until-build
- Dependencies resolution

## Пример полного плагина

См. [serial-monitor plugin](https://github.com/JetBrains/intellij-plugins/tree/master/serial-monitor) как пример хорошо структурированного плагина.
