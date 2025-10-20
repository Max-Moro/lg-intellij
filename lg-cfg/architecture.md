# Архитектура плагина Listing Generator для IntelliJ Platform

## Обзор

**Listing Generator IntelliJ Plugin** — плагин для интеграции CLI инструмента Listing Generator в экосистему JetBrains IDE.

**Целевая платформа:** IntelliJ Platform 2024.1+  
**Язык разработки:** Kotlin  
**UI Toolkit:** Swing + IntelliJ UI Components + Kotlin UI DSL  
**Async:** Kotlin Coroutines

**Совместимость:** IntelliJ IDEA, PyCharm, WebStorm, CLion и другие IDE на базе IntelliJ Platform.

---

## Ключевые принципы архитектуры

### 1. Соответствие платформенным паттернам

- **Services** для инкапсуляции бизнес-логики и состояния
- **Actions** для команд пользователя
- **Extensions** для интеграции с IDE
- **Message Bus** для слабосвязанной коммуникации между компонентами
- **Disposable** для корректного управления lifecycle и ресурсами

### 2. Разделение ответственности (High Cohesion, Low Coupling)

- **Presentation Layer** (UI) изолирован от бизнес-логики
- **Service Layer** не знает о UI компонентах
- **CLI Integration Layer** инкапсулирует все взаимодействия с внешним процессом
- **State Management** централизован в dedicated services

### 3. Расширяемость

- Чёткие интерфейсы между слоями
- Extension Points для будущих расширений
- Модульная структура позволяет добавлять функциональность без переписывания существующего кода

### 4. Асинхронность и Production-Ready

- Kotlin Coroutines для всех длительных операций
- Корректное управление потоками (EDT для UI, BGT для чтения, IO для внешних процессов)
- Cancellation support для всех фоновых операций
- Structured concurrency через Service-injected CoroutineScope

---

## Структура проекта

```
lg-intellij/
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
├── src/
│   ├── main/
│   │   ├── kotlin/lg/intellij/
│   │   │   ├── actions/              # Команды пользователя
│   │   │   ├── cli/                  # CLI интеграция
│   │   │   ├── services/             # Бизнес-логика
│   │   │   │   ├── core/             # Основные services
│   │   │   │   ├── catalog/          # Каталог sections/contexts
│   │   │   │   ├── generation/       # Генерация контента
│   │   │   │   ├── state/            # Управление состоянием
│   │   │   │   ├── vfs/              # Virtual File System интеграция
│   │   │   │   └── ai/               # AI интеграция
│   │   │   ├── ui/                   # UI компоненты
│   │   │   │   ├── toolwindow/       # Tool Window панели
│   │   │   │   ├── dialogs/          # Диалоги
│   │   │   │   ├── components/       # Переиспользуемые компоненты
│   │   │   │   └── renderers/        # Tree/List renderers
│   │   │   ├── settings/             # Настройки плагина
│   │   │   ├── git/                  # Git интеграция
│   │   │   ├── listeners/            # Event listeners
│   │   │   ├── models/               # Data models
│   │   │   └── utils/                # Утилиты
│   │   └── resources/
│   │       ├── META-INF/
│   │       │   └── plugin.xml        # Конфигурация плагина
│   │       ├── messages/
│   │       │   └── LgBundle.properties  # i18n
│   │       └── icons/
│   │           └── lg-icon.svg
│   └── test/
│       ├── kotlin/
│       └── resources/
│           └── testData/
└── README.md
```

---

## Основные архитектурные слои

### Layer 1: CLI Integration (нижний слой)

**Ответственность:** изоляция всего взаимодействия с внешним CLI процессом.

#### `cli/CliExecutor`
- Единая точка выполнения команд CLI
- Управление жизненным циклом процесса (запуск, остановка, timeout)
- Захват stdout/stderr
- Обработка ошибок выполнения
- Поддержка stdin для передачи task text
- Cancellation support через Kotlin Coroutines

#### `cli/CliResolver`
- Обнаружение местоположения listing-generator executable
- Стратегии резолюции: explicit path (из настроек) → system PATH → managed venv → Python module
- Кэширование resolved path
- Invalidation cache при изменении настроек

#### `cli/CliResponseParser`
- Парсинг JSON ответов от CLI
- Маппинг в Kotlin data classes
- Обработка ошибок парсинга
- Версионирование протокола взаимодействия

**Зависимости:** IntelliJ Platform Process API (`GeneralCommandLine`, `CapturingProcessHandler`)

**Потребители:** Service Layer

---

### Layer 2: Service Layer (бизнес-логика)

Все Services реализованы как **Light Services** с `@Service` аннотацией. Project-level services получают injected `CoroutineScope` для асинхронных операций.

#### Core Services

##### `services/core/LgCatalogService` (Project-level)
- Загрузка списков sections, contexts, mode-sets, tag-sets из CLI
- Кэширование списков в памяти
- Автоматическая инвалидация при изменении lg-cfg/ (через VFS listener)
- Reactive exposure через Kotlin Flow (`StateFlow<List<String>>`)
- Методы: `getSections()`, `getContexts()`, `getModeSets()`, `getTagSets()`, `reload()`

##### `services/core/LgGenerationService` (Project-level)
- Генерация listings и contexts через CLI
- Управление параметрами генерации (tokenizer, encoder, modes, tags, task)
- Progress reporting через IntelliJ Platform Progress API
- Методы: `generateListing()`, `generateContext()`, `generateReport()` (возвращают suspending functions)

##### `services/core/LgDiagnosticsService` (Project-level)
- Запуск диагностики через `lg diag`
- Сброс кэша через `lg diag --rebuild-cache`
- Построение diagnostic bundle
- Методы: `runDiagnostics()`, `rebuildCache()`, `buildBundle()`

#### State Management Services

##### `services/state/LgPanelStateService` (Project-level)
- Сохранение состояния Control Panel (selected section/template, tokenization params, modes, tags, task text, target branch)
- Реализует `PersistentStateComponent` через `SimplePersistentStateComponent`
- Storage: workspace file (не коммитится в VCS)
- Reactive state exposure через `StateFlow`

##### `services/state/LgSettingsService` (Application-level)
- Глобальные настройки плагина (CLI path, Python interpreter, install strategy, default tokenizer/encoder/ctx-limit, AI provider, openAsEditable flag)
- Реализует `PersistentStateComponent`
- Storage: application config directory, roaming enabled
- Методы: `getInstance()` (singleton getter)

##### `services/state/LgWorkspaceStateService` (Project-level)
- Workspace-specific UI state (tree view mode для Included Files, последние выбранные значения, window positions)
- Storage: workspace file
- Не подлежит синхронизации между машинами

#### Catalog Services

##### `services/catalog/TokenizerCatalogService` (Application-level)
- Загрузка списка tokenizer libraries и encoders через CLI
- Кэширование с TTL
- Определение cached vs available encoders
- Методы: `getLibraries()`, `getEncoders(lib: String)`, `invalidate()`

##### `services/catalog/GitBranchCatalogService` (Project-level)
- Интеграция с Git4Idea API для получения списка веток
- Опциональная зависимость от Git plugin
- Graceful degradation если Git недоступен
- Методы: `isGitAvailable()`, `getBranches()`, `getCurrentBranch()`

#### Generation Services

##### `services/generation/LgContextGenerator` (Project-level)
- Генерация контекстов с учётом всех параметров
- Mapping UI state → CLI arguments
- Task text handling (inline, from file, from stdin)
- Возврат typed результата (`GenerationResult` data class)

##### `services/generation/LgListingGenerator` (Project-level)
- Генерация listings для секций
- Аналогично `LgContextGenerator` но для sections

##### `services/generation/LgStatsCollector` (Project-level)
- Получение статистики через `lg report`
- Парсинг ReportSchema JSON
- Предоставление typed моделей для UI

#### AI Integration

##### `services/ai/AiIntegrationService` (Application-level)
- Центральный сервис для AI провайдеров
- Registry паттерн для провайдеров (clipboard, местные копайлоты — JetBrains AI Assistant + Junie, openai.api и др.)
- Детекция доступных провайдеров при старте
- Единая точка отправки контента в AI
- Методы: `detectProviders()`, `sendTo(providerId, content)`, `getAvailableProviders()`

##### `services/ai/providers/*` (пакет с провайдерами)
- Реализации для каждого AI provider
- Общий интерфейс `AiProvider`
- Базовые классы: `CliBasedProvider`, `ApiBasedProvider`, `ExtensionBasedProvider`
- Graceful degradation если провайдер недоступен

**Зависимости:** CLI Integration Layer, Settings Services

**Потребители:** UI Layer, Actions

---

### Layer 3: UI Layer

#### Tool Window

##### `ui/toolwindow/LgToolWindowFactory`
- Реализует `ToolWindowFactory` с `DumbAware`
- Создаёт Tool Window с двумя вкладками: Control Panel и Included Files
- Инициализация: установка свойств tool window (anchor, icon, stripe title)
- Conditional visibility через `isApplicableAsync()` — показывать только если есть lg-cfg/ в проекте

##### `ui/toolwindow/LgControlPanel`
- Главная панель управления (первая вкладка)
- Наследуется от `SimpleToolWindowPanel`
- Содержит: template selector, section selector, task input field, tokenization settings (library/encoder/ctx-limit), adaptive settings (mode-sets, tag-sets, target-branch selector), action buttons
- Построена с использованием **Kotlin UI DSL**
- Интеграция с `LgPanelStateService` для персистентности
- Reactive updates через подписку на StateFlow из catalog services
- Toolbar с actions: Refresh, Settings, Help

##### `ui/toolwindow/LgIncludedFilesPanel`
- Вторая вкладка Tool Window
- Tree view с поддержкой flat/tree режимов
- Использует стандартный IntelliJ `Tree` компонент
- Custom `TreeCellRenderer` для отображения файлов с иконками
- Double-click открывает файл в editor
- Context menu: Open, Copy Path, Refresh
- Toggle view mode через dedicated Action в toolbar
- Состояние (view mode) хранится в `LgWorkspaceStateService`

#### Dialogs

##### `ui/dialogs/LgStatsDialog`
- Наследуется от `DialogWrapper`
- Отображает детальную статистику по ReportSchema
- Содержит: summary cards (files count, tokens, size), grouped table с файлами (с фильтрацией и сортировкой), adapter metrics (collapsible sections)
- Toolbar: Refresh, Send to AI, Generate
- Copy to clipboard action
- Task text input field (integrated, как в Stats webview VS Code версии)
- Построен с использованием Swing components и частично Kotlin UI DSL

##### `ui/dialogs/LgDoctorDialog`
- Отображает результаты диагностики
- Содержит: config status, cache status, environment info, checks table, applied migrations list
- Actions: Refresh, Rebuild Cache, Build Bundle, Copy JSON
- Raw JSON viewer в collapsible section

##### `ui/dialogs/LgInitWizardDialog` (опционально)
- Wizard для создания lg-cfg/ через `lg init`
- Multi-step dialog с выбором preset
- Conflict resolution UI
- Может быть упрощён до простого dialog с ComboBox

#### UI Components (переиспользуемые)

##### `ui/components/LgComboBoxWithAutoComplete`
- Wrapper над `ComboBox` с поддержкой фильтрации и autocomplete
- Для encoder selector (поддержка custom values)
- Отображение cached items (badge или icon)

##### `ui/components/LgTaskInputField`
- Custom component для ввода task description
- Multi-line expandable текст (аналог chat input в VS Code)
- Может быть реализован через `JTextArea` с custom sizing logic или `EditorTextField`

##### `ui/components/LgGroupedTable`
- Table с поддержкой группировки по директориям (аналог grouped table в VS Code Stats)
- Hierarchical grouping level control (slider ←N→ ∞)
- Filtering и sorting
- Может быть реализован через `JBTable` с custom `TableModel`

##### `ui/components/LgModeSetsPanel`
- Panel с динамически генерируемыми ComboBox для каждого mode-set
- Layout через Kotlin UI DSL
- Two-way binding с `LgPanelStateService`

##### `ui/components/LgTagSetsPanel`
- Panel с checkboxes для tag selection
- Организация в collapsible groups по tag-set
- ScrollPane для большого количества тегов
- Может быть реализован как отдельный Dialog или inline panel

##### `ui/renderers/FileTreeCellRenderer`
- Custom `ColoredTreeCellRenderer` для дерева файлов
- Отображение файлов с иконками по file type
- Отображение директорий с folder icons
- Опциональное отображение metadata (size, modified status)

---

### Layer 4: Actions

Actions регистрируются в `plugin.xml` и используются в меню, toolbar, keyboard shortcuts.

#### Main Actions

##### `actions/LgGenerateListingAction`
- Генерация listing для выбранной section
- Получение параметров из `LgPanelStateService`
- Вызов `LgListingGenerator` service
- Отображение результата через `LgVirtualFileService`
- Keyboard shortcut: Ctrl+Shift+G (tentative)

##### `actions/LgGenerateContextAction`
- Генерация context для выбранного template
- Аналогичен `LgGenerateListingAction` но для contexts
- Keyboard shortcut: Ctrl+Shift+C (tentative)

##### `actions/LgShowStatsAction`
- Открытие `LgStatsDialog` с детальной статистикой
- Выбор: stats для section или для context (в зависимости от выбранного в Control Panel)

##### `actions/LgShowIncludedFilesAction`
- Загрузка списка included files через CLI
- Обновление Included Files tab
- Автоматический switch на эту вкладку в Tool Window

##### `actions/LgSendToAiAction`
- Генерация контента (listing или context)
- Отправка через `AiIntegrationService`
- Обработка ошибок и fallback на clipboard

##### `actions/LgCreateStarterConfigAction`
- Запуск wizard для `lg init`
- Интеграция с `LgInitWizardDialog`
- Открытие sections.yaml после создания

##### `actions/LgOpenConfigAction`
- Открытие lg-cfg/sections.yaml в editor
- Fallback: предложение создать через `LgCreateStarterConfigAction` если не существует

##### `actions/LgRunDoctorAction`
- Запуск диагностики
- Открытие `LgDoctorDialog` с результатами

##### `actions/LgResetCacheAction`
- Сброс LG cache через diagnostics service
- Confirmation dialog перед выполнением

##### `actions/LgRefreshCatalogsAction`
- Принудительная перезагрузка lists из CLI (sections, contexts, encoders, branches)
- Обновление UI после загрузки

#### Toggle Actions

##### `actions/LgToggleTreeViewModeAction`
- Переключение между flat и tree режимами для Included Files
- Обновление состояния в `LgWorkspaceStateService`
- Обновление UI панели

#### Toolbar Action Groups

Действия группируются в `DefaultActionGroup` для использования в toolbars:
- Control Panel Toolbar Group: Refresh, Settings, Help
- Included Files Toolbar Group: Refresh, Toggle View Mode
- Stats Dialog Toolbar Group: Refresh, Generate, Send to AI

**Зависимости:** Service Layer

**Потребители:** зарегистрированы в `plugin.xml`, вызываются IDE

---

### Layer 5: Settings & Configuration

#### `settings/LgSettingsConfigurable`
- Реализует `BoundConfigurable`
- Application-level настройки
- UI построен через Kotlin UI DSL
- Секции:
  - **CLI Configuration**: CLI Path, Python Interpreter, Install Strategy
  - **Tokenization Defaults**: Default Library, Default Encoder, Default Context Limit
  - **Editor Behavior**: Open As Editable checkbox
  - **AI Integration**: AI Provider selector, OpenAI API Key configuration

#### `settings/LgProjectSettingsConfigurable` (опционально)
- Project-level настройки
- Секции:
  - **Defaults**: Default Section, Default Template
  - **Modes**: Default mode для каждого mode-set
  
Может быть избыточным на первом этапе, так как эти значения сохраняются в `LgPanelStateService`.

**Зависимости:** `LgSettingsService`, `LgPanelStateService`

**Интеграция:** регистрация через `<applicationConfigurable>` в `plugin.xml`

---

### Layer 6: Virtual Files (аналог VirtualDocProvider в VS Code)

#### `vfs/LgVirtualFileService` (Project-level)
- Управление созданием и отображением generated контента
- Два режима работы (в зависимости от `openAsEditable` настройки):
  1. **Virtual mode**: `LightVirtualFile` в памяти (read-only)
  2. **Editable mode**: temporary файл на диске в system temp directory
- Определение `FileType` для syntax highlighting (Markdown, YAML, JSON)
- Методы: `openListing()`, `openContext()`, `openStats()` (принимают content и filename)

**Отличия от VS Code:**
- В VS Code используется custom URI scheme (`lg://`)
- В IntelliJ используется `LightVirtualFile` (поддерживается платформой из коробки)
- `LightVirtualFile` автоматически получает syntax highlighting по `FileType`

**Зависимости:** IntelliJ Platform VFS API, `FileEditorManager`

**Потребители:** Actions, Generation Services

---

### Layer 7: Git Integration (опциональная зависимость)

#### `git/LgGitService` (Project-level)
- Опциональная зависимость от `Git4Idea` plugin
- Получение списка веток (local + remote) для target branch selector
- Получение текущей ветки
- Graceful degradation если Git plugin отсутствует или проект не под Git
- Методы: `isGitAvailable()`, `getBranches()`, `getCurrentBranch()`

**Зависимости:** Git4Idea plugin (optional dependency через `plugin.xml`)

**Потребители:** `LgCatalogService`, `LgControlPanel`

---

### Layer 8: Listeners (реактивность)

#### `listeners/LgConfigFileListener` (Project-level)
- Слушает изменения файлов в lg-cfg/ через `BulkFileListener`
- При изменении sections.yaml, *.sec.yaml, *.ctx.md, *.tpl.md → invalidation catalog cache
- Уведомление UI через `LgCatalogService` о необходимости refresh
- Debounce механизм для избежания множественных reload при batch изменениях

#### `listeners/LgSettingsChangeListener` (Application-level)
- Слушает изменения в `LgSettingsService` через custom Topic
- При изменении CLI path или Python interpreter → invalidation CLI resolver cache
- При изменении tokenizer defaults → update UI defaults

**Реализация:** через IntelliJ Platform Message Bus

**Зависимости:** VFS API, Settings Services, Catalog Services

---

## Потоки данных и взаимодействие компонентов

### Поток 1: Генерация Listing

```
User Action (Generate Button Click)
  ↓
LgGenerateListingAction.actionPerformed()
  ↓
LgPanelStateService.getState() → CliParams
  ↓
LgListingGenerator.generate(params) [в корутине с Progress]
  ↓
CliExecutor.execute(args) [Dispatchers.IO]
  ↓
CliResponseParser.parseText(stdout)
  ↓
LgVirtualFileService.openListing(content) [EDT]
  ↓
FileEditorManager.openFile() [платформа открывает в editor]
```

### Поток 2: Загрузка каталогов

```
Plugin Startup
  ↓
LgToolWindowFactory.createToolWindowContent()
  ↓
LgControlPanel init
  ↓
LgCatalogService.loadAll() [корутина]
  ↓
Parallel coroutines:
  - CliExecutor.execute(["list", "sections"])
  - CliExecutor.execute(["list", "contexts"])
  - CliExecutor.execute(["list", "mode-sets"])
  - CliExecutor.execute(["list", "tag-sets"])
  - TokenizerCatalogService.getLibraries()
  - GitBranchCatalogService.getBranches()
  ↓
CliResponseParser.parse*() → Data models
  ↓
LgCatalogService._sections.value = result [StateFlow update]
  ↓
LgControlPanel collect() [Flow collector на EDT]
  ↓
UI Components update (ComboBox.removeAllItems/addItem)
```

### Поток 3: VFS Change → Reload

```
User edits lg-cfg/sections.yaml
  ↓
VFS fires VFileContentChangeEvent
  ↓
LgConfigFileListener.after(events)
  ↓
Filter: event.file?.parent?.name == "lg-cfg"?
  ↓
Debounce (500ms)
  ↓
LgCatalogService.reload() [корутина]
  ↓
Reload flow (как в Поток 2)
```

### Поток 4: Send to AI

```
User Action (Send to AI Button)
  ↓
LgSendToAiAction.actionPerformed()
  ↓
Determine: context или section?
  ↓
LgGenerationService.generate*() [с Progress]
  ↓
content: String
  ↓
LgSettingsService.state.aiProvider → providerId
  ↓
AiIntegrationService.sendTo(providerId, content)
  ↓
Provider-specific logic:
  - Clipboard: CopyPasteManager.setContents()
  - OpenAI: HTTP request через OpenAI API
  - Cursor/Copilot: IDE command execution
  ↓
Success Notification или Error Notification
```

---

## Управление состоянием

### Три уровня State

#### 1. Application State (`LgSettingsService`)
- **Lifetime:** весь жизненный цикл IDE
- **Scope:** все проекты
- **Storage:** `~/.config/JetBrains/.../options/lg-settings.xml`
- **Sync:** roaming enabled
- **Содержимое:** CLI path, Python interpreter, install strategy, default tokenizer/encoder/ctx-limit, AI provider, openAsEditable

#### 2. Project State (`LgPanelStateService`)
- **Lifetime:** пока проект открыт
- **Scope:** текущий проект
- **Storage:** `.idea/workspace.xml`
- **Sync:** disabled (не коммитится в VCS)
- **Содержимое:** selected section, selected template, current modes, active tags, task text, target branch, tokenizer params (override defaults)

#### 3. Workspace State (`LgWorkspaceStateService`)
- **Lifetime:** пока проект открыт
- **Scope:** текущий проект
- **Storage:** `.idea/workspace.xml`
- **Sync:** disabled
- **Содержимое:** UI-specific state (tree view mode, tool window tab selection, column widths, etc.)

### Reactive State Management

Catalog Services expose data через **Kotlin StateFlow**:

```
LgCatalogService
  ├── sections: StateFlow<List<String>>
  ├── contexts: StateFlow<List<String>>
  ├── modeSets: StateFlow<ModeSetsList>
  └── tagSets: StateFlow<TagSetsList>
```

UI подписывается через `scope.launch { flow.collect { ... } }` и обновляется на EDT.

---

## Threading Model

### Dispatchers Usage

- **Dispatchers.Default** — CPU-bound операции (парсинг JSON, построение data structures)
- **Dispatchers.IO** — CLI execution, file I/O
- **Dispatchers.EDT** — все UI updates, write actions

### Read/Write Actions

- **readAction { }** — чтение PSI/VFS на background thread (если потребуется)
- **writeAction { }** — модификация VFS/PSI (только на EDT)

В рамках LG plugin **минимальное** использование PSI — в основном работа с file paths.

### Service Scopes

Project-level services получают injected `CoroutineScope`:

```
@Service(Service.Level.PROJECT)
class MyService(
    private val project: Project,
    private val scope: CoroutineScope  // Auto-managed
)
```

Scope автоматически cancels при dispose проекта.

---

## Обработка ошибок

### Typed Error Handling

Определить sealed class для результатов CLI:

#### `models/CliResult`
```
sealed class CliResult<out T>
  - Success(data: T)
  - Failure(exitCode: Int, stderr: String)
  - Timeout(duration: Long)
  - NotFound(message: String)
```

Services возвращают `CliResult<T>`, UI pattern-matching для обработки.

### Error Notifications

- **CLI errors** → Error notification с "Show Details" action
- **Network errors** (AI providers) → Error notification с Retry и "Copy to Clipboard" fallback
- **Validation errors** → inline validation в UI (через UI DSL validation или `ValidationInfo`)

### Graceful Degradation

- Git недоступен → скрыть target branch selector
- CLI не найден → показать notification с предложением настроить в Settings
- AI provider недоступен → fallback на clipboard

---

## Интеграция с IntelliJ Platform

### Extension Points (регистрация в plugin.xml)

```xml
<extensions defaultExtensionNs="com.intellij">
    <!-- Tool Window -->
    <toolWindow 
        id="Listing Generator"
        anchor="right"
        factoryClass="...LgToolWindowFactory"
        icon="icons.LgIcons.ToolWindow"/>
    
    <!-- Settings -->
    <applicationConfigurable
        parentId="tools"
        instance="...LgSettingsConfigurable"/>
    
    <!-- Notification Groups -->
    <notificationGroup 
        id="LG Notifications"
        displayType="BALLOON"/>
    <notificationGroup 
        id="LG Important"
        displayType="STICKY_BALLOON"/>
</extensions>
```

### Actions Registration

```xml
<actions>
    <group id="LgMainGroup" text="Listing Generator" popup="true">
        <action id="LgGenerateListing" class="..."/>
        <action id="LgGenerateContext" class="..."/>
        <separator/>
        <action id="LgShowStats" class="..."/>
        <action id="LgShowIncluded" class="..."/>
        <separator/>
        <action id="LgCreateConfig" class="..."/>
        <action id="LgRunDoctor" class="..."/>
        
        <add-to-group group-id="ToolsMenu" anchor="last"/>
    </group>
</actions>
```

### Listeners Registration

```xml
<projectListeners>
    <listener
        topic="com.intellij.openapi.vfs.newvfs.BulkFileListener"
        class="...LgConfigFileListener"/>
</projectListeners>

<applicationListeners>
    <listener
        topic="...LgSettingsChangeListener.TOPIC"
        class="...LgSettingsChangeNotifier"/>
</applicationListeners>
```

### Optional Dependencies

```xml
<!-- Git integration (optional) -->
<depends optional="true" config-file="withGit.xml">
    Git4Idea
</depends>
```

`withGit.xml`:
```xml
<idea-plugin>
    <extensions defaultExtensionNs="com.intellij">
        <!-- Git-specific extensions если нужны -->
    </extensions>
</idea-plugin>
```

---

## Models (Data Classes)

### CLI Response Models

Kotlin data classes для typed парсинга JSON ответов CLI:

#### `models/ReportSchema`
- Маппинг JSON schema из CLI
- Properties: protocol, scope, target, tokenizerLib, encoder, ctxLimit, total, files, context
- Nested data classes: `TotalsData`, `FileRow`, `ContextBlock`

#### `models/DiagReport`
- Маппинг диагностического отчёта
- Properties: protocol, tool_version, root, config, cache, checks, env
- Nested: `DiagConfig`, `DiagCache`, `DiagCheck`, `DiagEnv`

#### `models/ModeSetsList`, `models/TagSetsList`
- Маппинг для adaptive settings
- Hierarchical structure: ModeSet → Mode[], TagSet → Tag[]

### UI State Models

#### `models/UiState`
```
data class ControlPanelState(
    val selectedSection: String,
    val selectedTemplate: String,
    val tokenizerLib: String,
    val encoder: String,
    val ctxLimit: Int,
    val modes: Map<String, String>,
    val tags: Set<String>,
    val taskText: String,
    val targetBranch: String
)
```

Синхронизирован с `LgPanelStateService.State`.

---

## Dependency Injection и Service Location

IntelliJ Platform **не использует** полноценный DI фреймворк.

### Service Получение

```
// Application service
service<LgSettingsService>()

// Project service
project.service<LgCatalogService>()
```

### Constructor Injection (ограниченный)

Только для Project/Module:

```
@Service(Service.Level.PROJECT)
class MyService(
    private val project: Project,      // ✅ Injected
    private val scope: CoroutineScope  // ✅ Injected (2024.1+)
)

// ❌ НЕ поддерживается
class MyService(
    private val otherService: OtherService  // НЕ РАБОТАЕТ
)
```

### Service Dependencies

Services получают другие services **on-demand** (не в конструкторе):

```
class LgGenerationService {
    fun generate() {
        val cliService = project.service<LgCliService>()  // Здесь
        cliService.execute(...)
    }
}
```

---

## Lifecycle Management

### Service Lifecycle

```
User opens project
  ↓
IntelliJ Platform creates Project instance
  ↓
First access to service<LgCatalogService>()
  ↓
Platform creates service instance
  ↓
Constructor executed
  ↓
CoroutineScope injected (if declared)
  ↓
Service ready
  ↓
... project usage ...
  ↓
User closes project
  ↓
Platform calls Disposable.dispose() (if implemented)
  ↓
CoroutineScope cancelled automatically
  ↓
Service destroyed
```

### UI Component Lifecycle

```
Tool Window opened
  ↓
LgToolWindowFactory.createToolWindowContent()
  ↓
Create LgControlPanel instance
  ↓
LgControlPanel init block:
  - Setup UI via Kotlin UI DSL
  - Subscribe to StateFlow from services
  - Launch initial data loading coroutines
  ↓
Tool Window displayed
  ↓
... user interactions ...
  ↓
Project closed or Tool Window disposed
  ↓
Disposable.dispose() на panel
  ↓
Coroutines cancelled
  ↓
Subscriptions removed
```

---

## Расширяемость

### Добавление нового AI Provider

1. Создать класс в `services/ai/providers/` реализующий `AiProvider` interface
2. Зарегистрировать в `AiIntegrationService.providers` registry
3. Добавить в enum для Settings UI
4. Никаких изменений в Actions или UI

### Добавление нового Action

1. Создать класс в `actions/` наследующий `AnAction`
2. Зарегистрировать в `plugin.xml`
3. Опционально добавить в Action Group для toolbar
4. Action использует существующие Services через service locator

### Добавление новой вкладки в Tool Window

1. Создать panel class в `ui/toolwindow/`
2. Добавить в `LgToolWindowFactory.createToolWindowContent()`
3. Опционально создать dedicated state service для новой вкладки

### Добавление нового типа генерации

1. Создать dedicated generator service в `services/generation/`
2. Создать Action для trigger
3. Добавить UI controls в Control Panel (если нужно)
4. Использовать существующие `CliExecutor` и `LgVirtualFileService`

---

## Plugin Configuration (plugin.xml structure)

### Основные секции

```xml
<idea-plugin>
    <!-- Базовая информация -->
    <id>lg.intellij</id>
    <name>Listing Generator</name>
    <version>1.0.0</version>
    <vendor email="..." url="...">Author</vendor>
    
    <!-- Совместимость -->
    <idea-version since-build="241" until-build="243.*"/>
    
    <!-- Зависимости -->
    <depends>com.intellij.modules.platform</depends>
    <depends optional="true" config-file="withGit.xml">
        Git4Idea
    </depends>
    
    <!-- Локализация -->
    <resource-bundle>messages.LgBundle</resource-bundle>
    
    <!-- Extensions -->
    <extensions defaultExtensionNs="com.intellij">
        <!-- Services -->
        <!-- Tool Windows -->
        <!-- Settings -->
        <!-- Notification Groups -->
    </extensions>
    
    <!-- Actions -->
    <actions>
        <!-- Main Action Group -->
    </actions>
    
    <!-- Listeners -->
    <applicationListeners>
        <!-- ... -->
    </applicationListeners>
    <projectListeners>
        <!-- ... -->
    </projectListeners>
</idea-plugin>
```

---

## Testing Strategy

### Unit Tests
- **CLI Integration:** mock process handlers, verify argument construction
- **Services:** mock CLI responses, verify business logic
- **State Management:** verify persistence serialization/deserialization
- **Parsers:** verify JSON parsing для различных CLI outputs

### Integration Tests
- **Tool Window:** verify content creation, tab management
- **Actions:** verify enablement conditions, context data access
- **Settings:** verify UI binding, apply/reset logic
- **VFS operations:** verify file creation, listener triggers

### UI Tests (опционально)
- RemoteRobot для автоматизации UI interactions
- Критичные user flows: generate listing, send to AI

---

## Security Considerations

### Sensitive Data

- **API Keys** (OpenAI) → хранение через `PasswordSafe` API (не в `PersistentStateComponent`)
- **Task text** может содержать sensitive info → не логировать полностью, только metadata

### CLI Execution

- **Path injection prevention:** валидация CLI path перед выполнением
- **Argument escaping:** корректное экранирование через `GeneralCommandLine.withParameters()`
- **Timeout enforcement:** все CLI вызовы с обязательным timeout
- **Stderr capturing:** для информативных error messages без exposure sensitive paths

---

## Localization (i18n)

### Message Bundle

`resources/messages/LgBundle.properties`:
```properties
# Tool Window
toolwindow.stripe.title=LG
toolwindow.control.tab=Control Panel
toolwindow.included.tab=Included Files

# Actions
action.generate.listing.text=Generate Listing
action.generate.context.text=Generate Context
action.send.to.ai.text=Send to AI

# Notifications
notification.success.generated=Listing generated successfully
notification.error.cli.notfound=Listing Generator CLI not found

# Settings
settings.display.name=Listing Generator
settings.cli.path=CLI Path
```

Поддержка русской локализации через `LgBundle_ru.properties`.

### Локализация в коде

```
LgBundle.message("action.generate.listing.text")
```

---

## Performance Optimizations

### Lazy Loading
- Services создаются on-demand при первом обращении
- Catalog data загружается асинхронно при открытии Tool Window
- Tree nodes для Included Files строятся ленивым образом

### Caching
- CLI resolver кэширует resolved path
- Tokenizer catalog кэширует encoders list с TTL
- Catalog service кэширует sections/contexts до invalidation

### Batching
- VFS changes debounced (500ms) перед reload
- Multiple CLI calls выполняются параллельно через `coroutineScope { }`

### Background Execution
- Все CLI вызовы на `Dispatchers.IO`
- UI updates строго на EDT через `withContext(Dispatchers.EDT)`
- Progress reporting через IntelliJ Platform Progress API

---

## Migration Path (VS Code → IntelliJ)

### Соответствие компонентов

| VS Code Extension | IntelliJ Plugin | Реализация |
|-------------------|-----------------|------------|
| Control Panel (webview) | `LgControlPanel` (Swing + UI DSL) | Tool Window tab |
| Included Files Tree (webview tree) | `LgIncludedFilesPanel` (Tree) | Tool Window tab |
| Stats Webview | `LgStatsDialog` (DialogWrapper) | Modal dialog |
| Doctor Webview | `LgDoctorDialog` (DialogWrapper) | Modal dialog |
| `media/ui/` components | `ui/components/` (Swing-based) | Native components |
| State persistence (VSCode API) | `PersistentStateComponent` | Platform API |
| Command registration | Actions + plugin.xml | Platform API |
| Settings (VSCode config) | `Configurable` + UI DSL | Platform API |

### Ключевые отличия

1. **No Webviews:** вместо HTML/CSS/JS используются Swing components и Kotlin UI DSL
2. **No Custom Protocol:** вместо `lg://` URI scheme используется `LightVirtualFile`
3. **Platform Threading:** вместо ручного управления promises используются Kotlin Coroutines с platform-aware dispatchers
4. **Platform State:** вместо VS Code state API используется `PersistentStateComponent`

---

## Зависимости (Gradle)

### Основные

```kotlin
dependencies {
    intellijPlatform {
        // Base platform
        intellijIdeaCommunity("2024.1")
        
        // Optional plugins
        bundledPlugin("Git4Idea")
        
        // Tools
        pluginVerifier()
        testFramework(TestFrameworkType.Platform)
        instrumentationTools()
    }
    
    // Kotlin coroutines (bundled в платформе)
    // JSON parsing (kotlinx.serialization или jackson)
}
```

### Опциональные зависимости

- **Git4Idea** — для Git integration (опционально)
- Никаких внешних HTTP клиентов (используется Java 11+ `HttpClient` для OpenAI API)