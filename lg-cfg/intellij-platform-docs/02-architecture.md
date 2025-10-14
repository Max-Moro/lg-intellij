# Архитектура IntelliJ Platform

## Компонентно-ориентированная архитектура

IntelliJ Platform построена на принципах **компонентно-ориентированной архитектуры** с чёткой системой расширения функциональности.

### Основные строительные блоки

```
┌─────────────────────────────────────────────────────┐
│           IntelliJ Platform (Core)                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────┐  │
│  │Extension     │  │  Services    │  │ Actions  │  │
│  │Points        │  │              │  │          │  │
│  └──────────────┘  └──────────────┘  └──────────┘  │
└─────────────────────────────────────────────────────┘
              ↑                ↑                ↑
              │                │                │
    ┌─────────┴────────────────┴────────────────┴──┐
    │         Plugin 1        Plugin 2    Plugin N │
    │  (Extensions)      (Extensions)  (Extensions)│
    └──────────────────────────────────────────────┘
```

### Extension Points и Extensions

**Extension Points (EP)** — точки расширения, объявленные платформой или плагинами:
- Определяют **контракт** для расширения функциональности
- Объявляются в `plugin.xml` через `<extensionPoints>`
- Примеры: `toolWindow`, `applicationService`, `action`

**Extensions** — реализации, регистрируемые плагинами:
- Реализуют контракт Extension Point
- Регистрируются в `plugin.xml` через `<extensions>`
- Загружаются лениво (on-demand)

**Пример:**

Платформа объявляет EP:
```xml
<!-- В платформе -->
<extensionPoint 
    name="toolWindow"
    interface="com.intellij.openapi.wm.ToolWindowFactory"/>
```

Плагин регистрирует Extension:
```xml
<!-- В плагине -->
<extensions defaultExtensionNs="com.intellij">
    <toolWindow 
        id="My Tool Window"
        factoryClass="com.example.MyToolWindowFactory"/>
</extensions>
```

## Уровни компонентов

IntelliJ Platform организована в несколько уровней scope:

### 1. Application Level (глобальный)
- **Lifetime:** весь жизненный цикл IDE
- **Singleton:** одна инстанция на всё приложение
- **Примеры:** Application Services, Application-level Settings

```kotlin
@Service
class MyApplicationService {
    // Один экземпляр на всё приложение
}

// Получение
val service = service<MyApplicationService>()
```

### 2. Project Level
- **Lifetime:** пока проект открыт
- **Instance per Project:** для каждого проекта своя инстанция
- **Примеры:** Project Services, Project-level Settings, Project-level Extensions

```kotlin
@Service(Service.Level.PROJECT)
class MyProjectService(private val project: Project) {
    // Отдельный экземпляр для каждого проекта
}

// Получение
val service = project.service<MyProjectService>()
```

### 3. Module Level (не рекомендуется)
- **Lifetime:** пока модуль существует в проекте
- **Deprecated:** может увеличивать потребление памяти
- **Альтернатива:** используйте Project-level service с передачей Module в методы

## Ключевые подсистемы платформы

### 1. Application

[`Application`](https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/openapi/application/Application.java) — главный компонент платформы, представляющий приложение.

Доступ:
```kotlin
import com.intellij.openapi.application.ApplicationManager

val app = ApplicationManager.getApplication()
```

Основные методы:
- `invokeLater()` — выполнить на EDT с учётом modality
- `runReadAction()` — выполнить read action
- `runWriteAction()` — выполнить write action
- `isUnitTestMode()` — проверка режима тестирования
- `isHeadlessEnvironment()` — проверка headless mode

### 2. Project

[`Project`](https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/openapi/project/Project.java) — представляет открытый проект.

Получение Project:
```kotlin
// Из AnActionEvent
val project = e.project

// Из Editor
val project = editor.project

// Из PsiElement
val project = psiElement.project

// Из VirtualFile (через ProjectFileIndex)
val project = ProjectUtil.guessProjectForFile(virtualFile)
```

Основные методы:
- `getName()` — имя проекта
- `getBasePath()` — путь к директории проекта
- `getProjectFile()` — VirtualFile проектного файла (.ipr)
- `getService()` — получить project-level service

### 3. Module

[`Module`](https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/openapi/module/Module.java) — представляет модуль проекта.

**Модули** — это части проекта с собственными:
- Source roots
- Dependencies
- SDK
- Build configuration

Получение Module:
```kotlin
// Из Project
val modules = ModuleManager.getInstance(project).modules

// Из PsiElement
val module = ModuleUtilCore.findModuleForPsiElement(psiElement)

// Из VirtualFile
val module = ModuleUtilCore.findModuleForFile(virtualFile, project)
```

> **Примечание:** Module-level services и extensions не рекомендуются из-за увеличения потребления памяти. Используйте Project-level с передачей Module как параметра.

## Модель данных платформы

### Иерархия файлов и классов

```
Project
  └── Module (multiple)
       ├── Content Root (multiple)
       │    ├── Source Root
       │    ├── Test Source Root
       │    └── Resource Root
       └── Dependencies
            ├── SDK
            ├── Libraries
            └── Other Modules
```

### Три представления файла

IntelliJ Platform работает с файлами на трёх уровнях:

#### 1. VirtualFile (VFS level)
- **Абстракция** над файловой системой
- Работает с файлами на диске, в архивах, на HTTP серверах
- **Кэширование** содержимого в памяти
- **Event-driven** — уведомления об изменениях

```kotlin
import com.intellij.openapi.vfs.VirtualFile

val file: VirtualFile = ...
val name = file.name
val content = file.contentsToByteArray()
val isDirectory = file.isDirectory
```

#### 2. PsiFile (PSI level)
- **Синтаксическое дерево** файла
- Язык-специфичный парсинг
- Семантический анализ
- Навигация по структуре кода

```kotlin
import com.intellij.psi.PsiFile

val psiFile: PsiFile = ...
val language = psiFile.language
val text = psiFile.text
val classes = (psiFile as? PsiJavaFile)?.classes
```

#### 3. Document (Editor level)
- **Редактируемое** представление текста
- Отображается в Editor
- Поддержка undo/redo
- Синхронизация с VirtualFile и PsiFile

```kotlin
import com.intellij.openapi.editor.Document

val document: Document = ...
val text = document.text
document.insertString(0, "// Header\n")
```

### Связь между представлениями

```
VirtualFile ←→ Document ←→ PsiFile
     ↑            ↑            ↑
     │            │            │
  VFS Layer   Editor Layer  PSI Layer
```

Конверсия:
```kotlin
// VirtualFile → Document
val document = FileDocumentManager.getInstance()
    .getDocument(virtualFile)

// VirtualFile → PsiFile
val psiFile = PsiManager.getInstance(project)
    .findFile(virtualFile)

// Document → PsiFile
val psiFile = PsiDocumentManager.getInstance(project)
    .getPsiFile(document)

// PsiFile → VirtualFile
val virtualFile = psiFile.virtualFile

// PsiFile → Document
val document = PsiDocumentManager.getInstance(project)
    .getDocument(psiFile)
```

## Threading Model

IntelliJ Platform — **многопоточная среда** с жёсткими правилами доступа к данным.

### Типы потоков

#### EDT (Event Dispatch Thread)
- **Назначение:** обработка UI событий и запись данных
- **Один поток** на приложение
- **Правила:**
  - Все UI операции только на EDT
  - Запись в PSI/VFS/Project Model только на EDT
  - Операции должны быть **быстрыми** (не блокировать UI)

#### BGT (Background Threads)
- **Назначение:** длительные операции и чтение данных
- **Множество потоков**
- **Правила:**
  - Чтение данных в read action
  - Нельзя напрямую трогать UI компоненты

### Read-Write Lock

Единый application-wide read-write lock для доступа к данным:

| Lock Type | Allows | Acquired From | Can't be acquired if |
|-----------|--------|---------------|---------------------|
| **Read Lock** | Чтение данных | Любой поток (concurrent) | Write lock held |
| **Write Intent Lock** | Чтение + возможность upgrade | Любой поток | Write Intent or Write lock held |
| **Write Lock** | Чтение и запись | Только EDT | Любой lock held |

### Read Actions

```kotlin
// Kotlin (2024.1+)
val psiFile = readAction {
    PsiManager.getInstance(project).findFile(virtualFile)
}

// Java или старые версии Kotlin
val psiFile = ReadAction.compute<PsiFile, Throwable> {
    PsiManager.getInstance(project).findFile(virtualFile)
}
```

**Правила:**
- Чтение данных на BGT **требует** read action
- Чтение на EDT (через `invokeLater`) **не требует** read action (implicit write intent lock)

### Write Actions

```kotlin
// Kotlin (2024.1+)
writeAction {
    document.insertString(0, "// Header\n")
}

// Java или старые версии Kotlin
WriteAction.run<Throwable> {
    document.insertString(0, "// Header\n")
}
```

**Правила:**
- Запись **только на EDT**
- Запись **только через** `Application.invokeLater()` (с 2023.3+)
- Всегда в write action

### Modality State

**ModalityState** — стек активных модальных диалогов.

Зачем нужен:
- Предотвращает выполнение операций, пока открыт модальный диалог
- Гарантирует согласованность данных

```kotlin
// Выполнить когда нет модальных диалогов
Application.invokeLater({
    // Modify data
}, ModalityState.nonModal())

// Выполнить в текущем modality context
Application.invokeLater({
    // Modify data
}, ModalityState.defaultModalityState())

// Выполнить даже если есть диалоги
Application.invokeLater({
    // Modify data
}, ModalityState.any())
```

## Services

**Service** — компонент с жизненным циклом, загружаемый on-demand.

### Light Services (рекомендуется)

Современный способ создания services без регистрации в `plugin.xml`:

```kotlin
// Application-level service
@Service
class MyAppService {
    fun doSomething() { }
}

// Project-level service
@Service(Service.Level.PROJECT)
class MyProjectService(private val project: Project) {
    fun doSomething() {
        val projectName = project.name
    }
}
```

**Требования:**
- Класс должен быть `final` (в Kotlin по умолчанию)
- Нет constructor injection (получайте зависимости on-demand)
- Для PersistentStateComponent: roaming must be disabled (application-level)

### Получение Service

```kotlin
// Application service
val appService = service<MyAppService>()

// Project service
val projectService = project.service<MyProjectService>()
```

**Важно:**
- **Никогда** не сохраняйте service в полях заранее
- **Всегда** получайте service в месте использования
- **Только** when needed — не в конструкторах

### Non-Light Services (legacy)

Регистрация в `plugin.xml`:

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
</extensions>
```

Используйте только если:
- Нужно переопределение через другой плагин
- Нужно exposing API для других плагинов
- Требуются специальные атрибуты (`os`, `testServiceImplementation`)

## Actions

**Action** — команда, доступная через меню, toolbar, keyboard shortcuts.

### Базовый класс AnAction

```kotlin
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class MyAction : AnAction() {
    
    // Выполнение действия
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        // Выполнить операцию
    }
    
    // Обновление состояния (enabled/visible)
    override fun update(e: AnActionEvent) {
        // Действие доступно только когда проект открыт
        e.presentation.isEnabledAndVisible = e.project != null
    }
    
    // Указание потока для update() (2022.3+)
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT // или ActionUpdateThread.EDT
    }
}
```

### Важные правила для Actions:

1. **Нет полей класса** — Actions живут всё время работы приложения
   ```kotlin
   // ❌ ПЛОХО — утечка памяти
   class MyAction : AnAction() {
       private val project: Project? = null // НЕ ДЕЛАЙТЕ ТАК!
   }
   
   // ✅ ХОРОШО
   class MyAction : AnAction() {
       override fun actionPerformed(e: AnActionEvent) {
           val project = e.project // Получайте здесь
       }
   }
   ```

2. **update() должен быть быстрым** — вызывается часто
   - Нельзя долгих операций
   - Нельзя работы с файловой системой
   - Можно проверить selection, открытые файлы и т.д.

3. **Dumb-aware actions** — доступны во время индексации
   ```kotlin
   class MyAction : DumbAwareAction() {
       // Доступно даже при индексации
   }
   ```

### Регистрация Actions

```xml
<actions>
    <action 
        id="com.example.MyAction"
        class="com.example.MyAction"
        text="My Action"
        description="Does something useful"
        icon="AllIcons.Actions.Execute">
        
        <!-- Добавить в меню Tools -->
        <add-to-group 
            group-id="ToolsMenu" 
            anchor="first"/>
        
        <!-- Keyboard shortcut -->
        <keyboard-shortcut 
            keymap="$default" 
            first-keystroke="control alt M"/>
    </action>
</actions>
```

### Action Groups

```xml
<actions>
    <group 
        id="com.example.MyGroup"
        text="My Tools"
        popup="true">
        
        <action id="com.example.Action1" class="..."/>
        <action id="com.example.Action2" class="..."/>
        <separator/>
        <action id="com.example.Action3" class="..."/>
        
        <add-to-group group-id="ToolsMenu" anchor="last"/>
    </group>
</actions>
```

## Listeners

**Listeners** — механизм подписки на события в IDE.

### Application-level Listeners

```xml
<applicationListeners>
    <listener 
        topic="com.intellij.openapi.project.ProjectManagerListener"
        class="com.example.MyProjectListener"/>
</applicationListeners>
```

```kotlin
class MyProjectListener : ProjectManagerListener {
    override fun projectOpened(project: Project) {
        // Проект открыт
    }
    
    override fun projectClosed(project: Project) {
        // Проект закрыт
    }
}
```

### Project-level Listeners

```xml
<projectListeners>
    <listener 
        topic="com.intellij.openapi.vfs.newvfs.BulkFileListener"
        class="com.example.MyFileListener"/>
</projectListeners>
```

### Programmatic Subscription

```kotlin
// В Service или где нужно
class MyService(private val project: Project) : Disposable {
    
    init {
        project.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    // Handle file changes
                }
            }
        )
    }
    
    override fun dispose() {
        // Cleanup автоматический через connect(disposable)
    }
}
```

## Disposable и управление ресурсами

[`Disposable`](https://github.com/JetBrains/intellij-community/blob/master/platform/util/src/com/intellij/openapi/Disposable.java) — интерфейс для освобождения ресурсов.

### Иерархия Disposable

```
Application (root disposable)
  └── Project
       └── Module
            └── Editor
                 └── ...
```

При dispose родителя автоматически вызывается dispose у всех детей.

### Создание Disposable компонентов

```kotlin
class MyService : Disposable {
    private val connection = createConnection()
    
    override fun dispose() {
        connection.close()
    }
}
```

### Регистрация cleanup callback

```kotlin
// Выполнить cleanup когда project закроется
Disposer.register(project, Disposable {
    // Cleanup code
})
```

## Индексация и Dumb Mode

### Indexing

IntelliJ Platform строит **индексы** для быстрого поиска и навигации:
- **PSI Stubs** — компактное представление структуры файла
- **File-based Indexes** — индексы по содержимому файлов
- **VFS** — индекс файловой системы

### Dumb Mode

Во время индексации IDE переходит в **Dumb Mode** (тупой режим):
- Многие features отключены (code completion, find usages и т.д.)
- Доступны только **Dumb-Aware** компоненты

### Dumb-Aware API

```kotlin
// Action доступен в dumb mode
class MyAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        // Работает даже при индексации
    }
}

// Service доступен в dumb mode
@Service
class MyService : DumbAware {
    // ...
}

// Программная проверка
if (DumbService.isDumb(project)) {
    // Индексация идёт
    return
}

// Выполнить когда индексация закончится
DumbService.getInstance(project).runWhenSmart {
    // Код требующий индексов
}
```

## Dependency Injection (ограниченная)

IntelliJ Platform **не использует** полноценный DI фреймворк (Spring, Guice и т.д.).

**Поддерживаемая DI:**
- Services constructor injection: **только Project/Module** (не другие services)
  ```kotlin
  @Service(Service.Level.PROJECT)
  class MyService(private val project: Project) { }
  ```

- Extension/Listener constructor: **не поддерживается**
  ```kotlin
  // ❌ НЕ СРАБОТАЕТ
  class MyAction(private val service: MyService) : AnAction()
  
  // ✅ ПРАВИЛЬНО
  class MyAction : AnAction() {
      override fun actionPerformed(e: AnActionEvent) {
          val service = service<MyService>() // Получить здесь
      }
  }
  ```

**Получайте зависимости on-demand:**
```kotlin
class MyService {
    fun doWork() {
        val otherService = service<OtherService>() // Здесь
        otherService.help()
    }
}
```

## Модель сборки и зависимостей

### Gradle Dependencies

```kotlin
dependencies {
    intellijPlatform {
        // Базовая платформа
        intellijIdeaCommunity("2024.1")
        
        // Встроенные плагины
        bundledPlugins("com.intellij.java")
        bundledPlugins("org.jetbrains.kotlin")
        
        // Плагины из Marketplace
        plugin("com.github.copilot", "1.2.3")
        
        // Инструменты для тестирования
        pluginVerifier()
        testFramework(TestFrameworkType.Platform)
    }
}
```

### Module Dependencies

Плагин может зависеть от модулей платформы или других плагинов:

```xml
<!-- Зависимость от базовой платформы (всегда) -->
<depends>com.intellij.modules.platform</depends>

<!-- Зависимость от Java plugin -->
<depends>com.intellij.modules.java</depends>

<!-- Зависимость от Kotlin plugin -->
<depends>org.jetbrains.kotlin</depends>

<!-- Опциональная зависимость -->
<depends optional="true" config-file="withPython.xml">
    com.intellij.modules.python
</depends>
```

## API Stability

IntelliJ Platform API **не гарантирует обратную совместимость** между major releases.

### API Changes List

Каждый релиз публикует список изменений API:
- [API Changes 2024](https://plugins.jetbrains.com/docs/intellij/api-changes-list-2024.html)
- [API Changes 2025](https://plugins.jetbrains.com/docs/intellij/api-changes-list-2025.html)

### Plugin Verifier

Автоматическая проверка совместимости:
```bash
./gradlew verifyPlugin
```

Проверяет:
- Deprecated API usage
- Internal API usage (помечены `@ApiStatus.Internal`)
- Binary incompatibility
- Missing dependencies

## Архитектурные паттерны

### 1. Extension Pattern

Вместо наследования используется композиция через Extension Points:

```kotlin
// Платформа объявляет контракт
interface MyExtensionPoint {
    fun process(data: Data)
}

// Плагин реализует
class MyExtension : MyExtensionPoint {
    override fun process(data: Data) { }
}
```

### 2. Service Locator Pattern

Services получаются через service locator:

```kotlin
val service = service<MyService>()
val projectService = project.service<MyProjectService>()
```

### 3. Message Bus (Pub-Sub)

Event-driven коммуникация через topics:

```kotlin
// Объявить topic
interface MyTopic {
    companion object {
        val TOPIC = Topic.create(
            "My Topic",
            MyTopic::class.java
        )
    }
    
    fun onEvent(data: Data)
}

// Subscribe
project.messageBus.connect(disposable).subscribe(
    MyTopic.TOPIC,
    object : MyTopic {
        override fun onEvent(data: Data) { }
    }
)

// Publish
project.messageBus.syncPublisher(MyTopic.TOPIC)
    .onEvent(data)
```

## Производительность и Best Practices

### 1. Ленивая инициализация

```kotlin
// ❌ Плохо — загрузка в конструкторе
class MyService {
    private val heavyData = loadHeavyData()
}

// ✅ Хорошо — ленивая загрузка
class MyService {
    private val heavyData by lazy { loadHeavyData() }
}
```

### 2. Избегайте преждевременной загрузки классов

```kotlin
// ❌ Плохо — import загружает класс
import com.example.HeavyClass

// ✅ Хорошо — загрузка по требованию
val clazz = Class.forName("com.example.HeavyClass")
```

### 3. Используйте cancellable operations

```kotlin
fun processFiles(files: List<VirtualFile>) {
    for (file in files) {
        ProgressManager.checkCanceled() // Проверка отмены
        processFile(file)
    }
}
```

### 4. Батчинг операций

```kotlin
// ❌ Плохо — много мелких write actions
for (element in elements) {
    writeAction { element.delete() }
}

// ✅ Хорошо — одна write action для всех
writeAction {
    for (element in elements) {
        element.delete()
    }
}
```

## Versioning и Forward Compatibility

### Since-Build и Until-Build

```xml
<!-- Поддержка 2024.1 - 2024.3 -->
<idea-version since-build="241" until-build="243.*"/>

<!-- Поддержка 2024.1 и выше (рискованно) -->
<idea-version since-build="241"/>
```

**Рекомендации:**
- Указывайте `until-build` с wildcard (`243.*`)
- Тестируйте на EAP версиях перед релизом
- Следите за API Changes List

### Runtime Version Checks

```kotlin
import com.intellij.openapi.application.ApplicationInfo

val appInfo = ApplicationInfo.getInstance()
val buildNumber = appInfo.build

if (buildNumber.baselineVersion >= 241) {
    // Функциональность для 2024.1+
}
```

## Debugging и Troubleshooting

### Internal Actions

Активируйте Internal Mode для доступа к инструментам разработки:

**Help → Edit Custom Properties**
```properties
idea.is.internal=true
```

После перезапуска: **Tools → Internal Actions →**
- **UI → UI Inspector** — инспектор UI
- **UI → Kotlin UI DSL → UI DSL Showcase** — примеры UI DSL
- **VFS → Show Watched VFS Roots** — отслеживаемые корни VFS
- **Dump Threads** — дамп потоков
- И многое другое...

### Логирование

```kotlin
import com.intellij.openapi.diagnostic.Logger

class MyClass {
    companion object {
        private val LOG = Logger.getInstance(MyClass::class.java)
    }
    
    fun doSomething() {
        LOG.info("Info message")
        LOG.warn("Warning message")
        LOG.error("Error message", exception)
        
        if (LOG.isDebugEnabled) {
            LOG.debug("Debug message")
        }
    }
}
```

Логи пишутся в `idea.log`:
**Help → Show Log in Explorer/Finder**

### Exception Handling

```kotlin
import com.intellij.openapi.diagnostic.logger

private val LOG = logger<MyClass>()

try {
    riskyOperation()
} catch (e: ProcessCanceledException) {
    // ❌ НИКОГДА не ловите PCE!
    throw e // Всегда re-throw
} catch (e: Exception) {
    LOG.error("Operation failed", e)
    // Notify user
}
```

## Plugin Class Loaders

Каждый плагин загружается в **отдельном ClassLoader**:
- Изоляция между плагинами
- Возможность использовать разные версии библиотек
- Sharing через `plugin.xml` зависимости

### Dependencies между плагинами

```xml
<!-- Плагин A -->
<idea-plugin>
    <id>com.example.pluginA</id>
    
    <!-- Expose API -->
    <extensionPoints>
        <extensionPoint name="myExtension" interface="..."/>
    </extensionPoints>
</idea-plugin>

<!-- Плагин B зависит от A -->
<idea-plugin>
    <id>com.example.pluginB</id>
    <depends>com.example.pluginA</depends>
    
    <extensions defaultExtensionNs="com.example.pluginA">
        <myExtension implementation="..."/>
    </extensions>
</idea-plugin>
```

## Заключение

Архитектура IntelliJ Platform основана на:
- **Component-oriented** подходе с Extension Points
- **Service Locator** для получения компонентов
- **Event-driven** коммуникации через Message Bus
- **Read-Write Lock** для многопоточного доступа к данным
- **Lazy loading** для производительности

Понимание этих принципов критично для создания качественных плагинов.
