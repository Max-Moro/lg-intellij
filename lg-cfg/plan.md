# Дорожная карта разработки Listing Generator — IntelliJ Platform Plugin

## Общие принципы итеративной разработки

На каждом этапе получается **запускаемое приложение** с постепенно расширяющимся функционалом. Архитектура закладывается сразу (слои Services → UI → Actions), код структурируется с учетом будущих расширений, а нереализованный функционал обозначается **заглушками с уведомлениями**.

**Стратегия минимизации переработок:**
- UI верстается **полностью** на своей фазе (со всеми элементами, но с заглушками на кнопках)
- Services проектируются **с полным API** сразу, но с mock/stub реализацией внутри
- Сложные компоненты (grouped table, autocomplete) откладываются — временно используются простые нативные аналоги с **TODO-комментариями** о будущей замене

---

## 📋 Фаза 0: Персонализация шаблона и первый запуск

### Цель
Адаптировать клонированный `intellij-platform-plugin-template` под проект Listing Generator и убедиться, что плагин собирается и запускается в Development Instance.

### Задачи

1. **Переименование идентификаторов:**
   - `plugin.xml`: обновить `<id>`, `<name>`, `<vendor>`
   - Проверить все персональные названия: `lg.intellij`, `Listing Generator` и так далее
   - Переименовать все классы: `My*` → `Lg*`
     - `MyBundle` → `LgBundle`
     - `MyProjectService` → `LgProjectService` (временный каркас)
     - `MyToolWindowFactory` → `LgToolWindowFactory`

2. **Создание иконки плагина:**
   - `pluginIcon.svg` (40×40, адаптивная под light/dark themes)
   - `lg-icon.svg` для Tool Window (13×13)
   
3. **Обновление `plugin.xml`:**
   - `since-build="241"`, `until-build="243.*"`
   - Description с HTML (кратко о функционале)
   - Базовая зависимость: `com.intellij.modules.platform`

4. **Базовая локализация:**
   - `LgBundle.properties` с ключами для Tool Window, Actions

5. **Первый запуск:**
   - `./gradlew runIde`
   - Проверить: плагин виден в списке, Tool Window появляется (с placeholder контентом), иконки отображаются

### Критерии готовности
✅ `./gradlew runIde` запускается без ошибок  
✅ Плагин виден в **Settings → Plugins**  
✅ Tool Window "Listing Generator" появляется справа с placeholder текстом  
✅ Иконки отображаются корректно в light/dark темах  

### Референсы из VS Code Extension
- `package.json` → для понимания displayName, description
- `media/lg.svg` → референс дизайна иконки
- README.md (верхняя часть) → для текста description

### Документация IntelliJ Platform
- `01-getting-started.md` (полностью)
- `03-plugin-structure.md` (секции: plugin.xml обязательные элементы, иконки)

---

## 📋 Фаза 1: CLI Integration Foundation

### Цель
Создать изолированный слой для взаимодействия с внешним CLI процессом Listing Generator.

### Задачи

1. **Создать структуру пакета `cli/`:**
   ```
   cli/
   ├── CliExecutor.kt       # Выполнение команд
   ├── CliResolver.kt       # Обнаружение executable
   ├── CliException.kt      # Typed exceptions
   └── models/
       └── CliResult.kt     # Sealed class для результатов
   ```

2. **`CliExecutor`:**
   - Suspend функция `execute(args: List<String>): String`
   - Использует `GeneralCommandLine` + `CapturingProcessHandler`
   - Timeout support (120 sec default)
   - Правильный charset (UTF-8)
   - Environment variables: `PYTHONIOENCODING=utf-8`, `PYTHONUTF8=1`
   - stdin support для `--task -`
   - Возврат typed `CliResult<String>` (Success/Failure/Timeout)

3. **`CliResolver`:**
   - Стратегии поиска: explicit path (из Settings) → system PATH → fallback "python -m lg.cli"
   - Кэширование resolved path
   - Метод `invalidateCache()` для вызова при изменении Settings
   - На этой фазе: **stub implementation** возвращает hardcoded "listing-generator" (реальная логика на Фазе 2 после Settings)

4. **Простейший тест:**
   - Action `LgTestCliAction` (временный, не в plugin.xml)
   - Вызов `lg --version` через `CliExecutor`
   - Показ результата через `Messages.showInfoMessage()`
   - Для разработки **без реального CLI**: mock responses в `CliExecutor` (флаг `USE_MOCK = true`)

### Критерии готовности
✅ `CliExecutor.execute()` вызывается и возвращает mock/реальный результат  
✅ Timeout и cancellation работают  
✅ Exceptions корректно typed (CliException, CliTimeoutException)  
✅ Test action показывает версию CLI в диалоге  

### Референсы из VS Code Extension
- `src/cli/CliClient.ts` → общая логика построения аргументов
- `src/cli/CliResolver.ts` → стратегии поиска CLI
- `src/runner/LgProcess.ts` → spawn logic, timeout, stdin handling

### Документация IntelliJ Platform
- `10-external-processes.md` (полностью)
- `12-background-tasks.md` (секции: Kotlin Coroutines, Dispatchers, suspend functions)

---

## 📋 Фаза 2: Settings Infrastructure

### Цель
Создать полностью функциональную Settings страницу с персистентностью, но **без использования** сохранённых значений в остальном коде (пока заглушки).

### Задачи

1. **`services/state/LgSettingsService`:**
   - Application-level Light Service
   - `@State` с storage `lg-settings.xml`, `roamingType = DEFAULT`, `category = TOOLS`
   - `SimplePersistentStateComponent<State>`
   - State fields (все как в архитектуре):
     ```kotlin
     var cliPath by string("")
     var pythonInterpreter by string("")
     var installStrategy by enum(InstallStrategy.MANAGED_VENV)
     var defaultTokenizerLib by string("tiktoken")
     var defaultEncoder by string("cl100k_base")
     var defaultContextLimit by property(128000)
     var aiProvider by enum(AiProvider.CLIPBOARD)
     var openAsEditable by property(false)
     ```
   - `getInstance()` companion method

2. **`settings/LgSettingsConfigurable`:**
   - Наследуется от `BoundConfigurable`
   - Kotlin UI DSL для всех полей
   - Секции (groups):
     - **CLI Configuration**: CLI Path (textFieldWithBrowseButton), Python Interpreter (textFieldWithBrowseButton), Install Strategy (comboBox)
     - **Tokenization Defaults**: Library (comboBox), Encoder (textField), Context Limit (intTextField с validation 1000..2000000)
     - **AI Integration**: Provider (comboBox), OpenAI API Key (passwordField) с "Configure" кнопкой
     - **Editor Behavior**: Open As Editable (checkBox)
   - Validation для context limit, path existence
   - "Reset to Defaults" button

3. **Регистрация в `plugin.xml`:**
   ```xml
   <applicationConfigurable 
       parentId="tools"
       instance="lg.intellij.settings.LgSettingsConfigurable"
       id="lg.intellij.settings"
       key="settings.display.name"
       bundle="messages.LgBundle"/>
   ```

4. **Интеграция с `CliResolver`:**
   - Обновить stub реализацию: использовать `LgSettingsService.getInstance().state.cliPath` если не пустой
   - Listener на изменения Settings → `CliResolver.invalidateCache()`

### Критерии готовности
✅ Settings открываются через **Tools → Listing Generator**  
✅ Все поля редактируются и сохраняются между перезапусками IDE  
✅ Validation работает (error для invalid values)  
✅ Reset to Defaults восстанавливает значения  
✅ CliResolver теперь читает CLI path из Settings  

### Референсы из VS Code Extension
- `package.json` → секция `configuration` (все параметры Settings)
- Никакого UI кода (Settings в VS Code — это JSON schema, в IntelliJ — Kotlin UI DSL)

### Документация IntelliJ Platform
- `07-kotlin-ui-dsl.md` (полностью — критично для верстки Settings)
- `11-settings.md` (полностью)
- `14-persistence.md` (секции: SimplePersistentStateComponent, Storage, Roaming)

---

## 📋 Фаза 3: Tool Window Structure (каркас)

### Цель
Создать структуру Tool Window с двумя вкладками (Control Panel и Included Files), но с **placeholder контентом**. Верстка панелей откладывается на следующие фазы.

### Задачи

1. **`ui/toolwindow/LgToolWindowFactory`:**
   - Implements `ToolWindowFactory`, `DumbAware`
   - `init()`: установка свойств (stripe title, icon)
   - `isApplicableAsync()`: проверка наличия `lg-cfg/` в проекте (suspend function на Dispatchers.IO)
   - `createToolWindowContent()`: создание двух вкладок

2. **Две вкладки (tabs):**
   - **Control Panel** (`ui/toolwindow/LgControlPanel`) — placeholder: `JLabel("Control Panel — Coming Soon")`
   - **Included Files** (`ui/toolwindow/LgIncludedFilesPanel`) — placeholder: `JLabel("Included Files — Coming Soon")`
   - Обе наследуют `SimpleToolWindowPanel(vertical=true, borderless=true)`
   - Closeable: false для обеих

3. **Регистрация в `plugin.xml`:**
   ```xml
   <toolWindow 
       id="Listing Generator"
       anchor="right"
       factoryClass="lg.intellij.ui.toolwindow.LgToolWindowFactory"
       icon="icons.LgIcons.ToolWindow"/>
   ```

4. **`icons/LgIcons.kt`:**
   - Загрузка всех иконок через `IconLoader`
   - `ToolWindow`, `Generate`, `Refresh`, `Settings` и др.

### Критерии готовности
✅ Tool Window "Listing Generator" появляется справа  
✅ Две вкладки: "Control Panel" и "Included Files"  
✅ Каждая вкладка показывает placeholder текст  
✅ Tool Window **не показывается** для проектов без `lg-cfg/`  
✅ Иконка Tool Window видна на полосе  

### Референсы из VS Code Extension
- `package.json` → секция `views` (названия вкладок)
- `media/control.html` → понимание структуры Control Panel (для будущей Фазы 4)
- Никакой UI логики пока не нужен

### Документация IntelliJ Platform
- `06-tool-windows.md` (полностью)
- `02-architecture.md` (секции: DumbAware, Disposable, Service scopes)

---

## 📋 Фаза 4: Control Panel UI (полная верстка)

### Цель
Сверстать **полностью** Control Panel со всеми элементами управления через Kotlin UI DSL. Все кнопки кликабельны, но показывают заглушки ("Not implemented yet" notifications). Данные в селекторах — hardcoded mock списки.

### Задачи

1. **`ui/toolwindow/LgControlPanel` (полная верстка):**
   
   Использовать **Kotlin UI DSL** через `panel { }`:
   
   - **Group "AI Contexts":**
     - Row: Template selector (ComboBox) — mock items: `["default", "api-docs", "review"]`
     - Row: Buttons — "Send to AI", "Generate Context", "Show Context Stats"
     - Task text field (JTextArea с auto-expand или EditorTextField) — placeholder "Describe current task"

   - **Group "Adaptive Settings":**
     - Dynamic rows для mode-sets (пока hardcoded: один mode-set "dev-stage" с modes: planning/development/review)
     - "Configure Tags" button → stub notification
     - Target Branch selector (ComboBox) — visibility через `visibleIf()` когда mode == "review"
     - Mock branches: `["main", "develop", "feature/xyz"]`

   - **Group "Inspect":**
     - Row: Section selector (ComboBox) — mock items: `["all", "core", "tests"]`
     - Row: Buttons — "Show Included", "Generate Listing", "Show Stats"

   - **Group "Tokenization Settings":**
     - Row: Library (ComboBox) — hardcoded: `["tiktoken", "tokenizers", "sentencepiece"]`
     - Row: Encoder (JTextField с placeholder) — mock validation
     - Row: Context Limit (intTextField с range 1000..2000000)

   - **Group "Utilities":**
     - Buttons: "Create Starter Config", "Open Config", "Doctor", "Reset Cache", "Settings"

2. **Button Actions (все через stub):**
   - Каждая кнопка вызывает notification: `"Feature 'X' — Coming in Phase N"`
   - Используется IntelliJ `NotificationGroupManager` для balloon notifications

3. **State binding (mock):**
   - Создать временный `LgPanelStateService` (пустой State)
   - UI компоненты **биндятся** к state properties, но state пока не используется никем

4. **Toolbar для Control Panel:**
   - Actions: Refresh (stub), Settings (открывает Settings через `ShowSettingsUtil`)

### Критерии готовности
✅ Control Panel выглядит **визуально законченным** (как финальный UI)  
✅ Все элементы управления отображаются корректно  
✅ Selectors содержат mock данные и позволяют выбор  
✅ Все кнопки кликабельны и показывают stub notifications  
✅ Task text field работает (можно вводить текст, он сохраняется в session memory)  
✅ Target Branch selector **скрыт** по умолчанию, **показывается** при выборе "review" mode  
✅ UI адаптивен к ширине панели (Kotlin UI DSL layout работает)  
✅ Settings button открывает Settings page  

### Референсы из VS Code Extension
- `media/control.html` (полностью) → структура UI, все элементы
- `media/control.css` → понимание групп, spacing (адаптировать под Kotlin UI DSL gaps)
- `media/control.js` → понимание взаимодействий (visibility conditions)
- `src/views/ControlPanelView.ts` → логика построения данных для UI (пока НЕ реализуем, только понимаем)

### Документация IntelliJ Platform
- `07-kotlin-ui-dsl.md` (полностью — основа верстки)
- `05-ui-components.md` (секции: ComboBox, TextField, Buttons, Checkboxes)
- `13-notifications.md` (секции: создание notifications, types, actions)

---

## 📋 Фаза 5: Catalog Services (загрузка данных)

### Цель
Реализовать сервисы для загрузки списков sections/contexts/mode-sets/tag-sets/encoders из CLI и **заменить** mock данные в Control Panel на реальные.

### Задачи

1. **`services/catalog/LgCatalogService` (Project-level):**
   - Injected: `Project`, `CoroutineScope`
   - Properties (StateFlow):
     ```kotlin
     val sections: StateFlow<List<String>>
     val contexts: StateFlow<List<String>>
     val modeSets: StateFlow<ModeSetsList>
     val tagSets: StateFlow<TagSetsList>
     ```
   - Методы:
     ```kotlin
     suspend fun loadAll()
     suspend fun reload()
     private suspend fun loadSections()
     private suspend fun loadContexts()
     private suspend fun loadModeSets()
     private suspend fun loadTagSets()
     ```
   - Использует `CliExecutor.execute(["list", "sections"])` и т.д.
   - Парсинг JSON через `kotlinx.serialization` или `jackson`
   - Parallel loading всех списков через `coroutineScope { launch { } }`

2. **`services/catalog/TokenizerCatalogService` (Application-level):**
   - `StateFlow<List<String>>` для libraries
   - `suspend fun getEncoders(lib: String): List<EncoderEntry>`
   - Кэширование encoders list (TTL 1 hour или до invalidation)
   - Методы: `getLibraries()`, `getEncoders()`, `invalidate()`

3. **`models/` data classes:**
   - `ModeSetsList`, `ModeSet`, `Mode` (по JSON schema CLI)
   - `TagSetsList`, `TagSet`, `Tag`
   - `EncoderEntry(name: String, cached: Boolean)`

4. **Обновление `LgControlPanel`:**
   - Удалить hardcoded mock списки
   - В `init { }` запустить корутину: `scope.launch { catalogService.loadAll() }`
   - Подписаться на StateFlow: 
     ```kotlin
     scope.launch {
         catalogService.sections.collect { sections ->
             withContext(Dispatchers.EDT) {
                 updateSectionsUI(sections)
             }
         }
     }
     ```
   - Аналогично для contexts, modeSets, tagSets, encoders

5. **VFS Listener для auto-reload:**
   - `listeners/LgConfigFileListener` (Project-level)
   - Слушает `BulkFileListener` для изменений в `lg-cfg/`
   - Debounce (500ms)
   - Вызов `catalogService.reload()`

6. **Refresh Action (реализация):**
   - `actions/LgRefreshCatalogsAction`
   - Вызов `catalogService.

   - `actions/LgRefreshCatalogsAction`
   - Вызов `catalogService.reload()` с Progress indicator
   - Notification при успехе/ошибке
   - Зарегистрировать в Control Panel toolbar

### Критерии готовности
✅ Control Panel показывает **реальные** sections/contexts из проекта (если есть lg-cfg/)  
✅ Mode-sets и tag-sets загружаются и отображаются динамически  
✅ Encoders list загружается для выбранной библиотеки токенизации  
✅ При изменении файлов в `lg-cfg/` (через внешний редактор) → автоматический reload через ~500ms  
✅ Refresh button принудительно перезагружает все списки  
✅ Если CLI недоступен → Error notification с предложением Configure Settings  

### Референсы из VS Code Extension
- `src/services/CatalogService.ts` (полностью) → API методов, JSON parsing
- `src/views/ControlPanelView.ts` → секции `pushListsAndState()`, обработка message "data"
- `media/control.js` → логика обновления UI при получении данных (адаптировать под Flow collectors)

### Документация IntelliJ Platform
- `04-services.md` (секции: Light Services, CoroutineScope injection, StateFlow patterns)
- `12-background-tasks.md` (секции: Coroutines, parallel execution, withContext)
- `09-vfs.md` (секции: BulkFileListener, VFS events)

---

## 📋 Фаза 6: State Management Services

### Цель
Создать полнофункциональные state services для сохранения состояния UI между сеансами и связать их с Control Panel для **реальной персистентности**.

### Задачи

1. **`services/state/LgPanelStateService` (Project-level):**
   - Storage: `StoragePathMacros.WORKSPACE_FILE` (не коммитится)
   - State fields:
     ```kotlin
     var selectedSection by string("all")
     var selectedTemplate by string("")
     var tokenizerLib by string("") // Empty = use default from app settings
     var encoder by string("")
     var contextLimit by property(0) // 0 = use default
     var modes by map<String, String>() // modeset -> mode
     var tags by stringSet()
     var taskText by string("")
     var targetBranch by string("")
     ```
   - Методы для получения **effective** values (с fallback на application defaults):
     ```kotlin
     fun getEffectiveTokenizerLib(): String
     fun getEffectiveEncoder(): String  
     fun getEffectiveContextLimit(): Int
     ```

2. **`services/state/LgWorkspaceStateService` (Project-level):**
   - Storage: workspace file
   - State:
     ```kotlin
     var includedFilesViewMode by enum(ViewMode.TREE)
     var toolWindowTabIndex by property(0)
     ```

3. **Интеграция с `LgControlPanel`:**
   - При инициализации: восстановить значения из `LgPanelStateService`
   - При изменении (ComboBox.addActionListener, TextField document listener) → сохранение в state
   - Two-way binding через debounced listeners (для text fields)

4. **Default values logic:**
   - Если `state.tokenizerLib.isBlank()` → использовать `LgSettingsService.state.defaultTokenizerLib`
   - При первом открытии Control Panel → заполнить из app defaults
   - UI показывает effective values

### Критерии готовности
✅ Выбранные section/template сохраняются между reopens Tool Window  
✅ Tokenization parameters сохраняются  
✅ Modes и tags сохраняются  
✅ Task text сохраняется  
✅ Defaults из Application Settings применяются если project state пустой  
✅ View mode для Included Files сохраняется  

### Референсы из VS Code Extension
- `src/views/ControlPanelView.ts` → секции `getState()`, `setState()`, state management
- `media/control.js` → обработка `data-state-key`, change events
- Логика восстановления состояния из `vscode.getState()`

### Документация IntelliJ Platform
- `14-persistence.md` (секции: SimplePersistentStateComponent, State delegates, workspace storage)
- `07-kotlin-ui-dsl.md` (секции: data binding, onChanged callbacks)

---

## 📋 Фаза 7: Generation Services Foundation

### Цель
Реализовать сервисы для генерации listings и contexts, но результаты пока отображаются через простой **modal dialog** с `JTextArea` (не LightVirtualFile). Это позволит протестировать всю цепочку без сложной интеграции с редактором.

### Задачи

1. **`services/generation/LgGenerationService` (Project-level):**
   - Методы:
     ```kotlin
     suspend fun generateListing(section: String): String
     suspend fun generateContext(template: String): String
     ```
   - Построение CLI args из `LgPanelStateService` state
   - Mapping modes/tags → `--mode`, `--tags` args
   - Task text handling: если не пустой → `--task -` + stdin
   - Target branch → `--target-branch` arg
   - Использует `CliExecutor.execute()` или `executeWithStdin()`

2. **`utils/CliArgsBuilder`:**
   - Helper для построения аргументов:
     ```kotlin
     fun buildRenderArgs(
         target: String,
         params: GenerationParams
     ): Pair<List<String>, String?>  // (args, stdinData)
     ```
   - Централизованная логика для избежания дублирования

3. **`actions/LgGenerateListingAction` (реализация):**
   - `actionPerformed()`:
     ```kotlin
     val section = panelState.state.selectedSection
     withBackgroundProgress(project, "Generating listing...") {
         val output = generationService.generateListing(section)
         withContext(Dispatchers.EDT) {
             showOutputDialog(output, "Listing — $section")
         }
     }
     ```
   - `update()`: enabled только если project != null

4. **`actions/LgGenerateContextAction` (реализация):**
   - Аналогично listing, но для context
   - Проверка: template не пустой в `update()`

5. **Временный helper `showOutputDialog()`:**
   - Simple `DialogWrapper` с `JTextArea` в `JScrollPane`
   - Read-only, monospace font
   - "Copy to Clipboard" button
   - **TODO комментарий:** "Phase 8: replace with LightVirtualFile in editor"

### Критерии готовности
✅ "Generate Listing" → показывает результат в modal dialog  
✅ "Generate Context" → показывает результат в modal dialog  
✅ Task text передаётся через stdin если заполнен  
✅ Modes и tags применяются в CLI args  
✅ Target branch передаётся если выбран review mode  
✅ Progress indicator показывается во время генерации  
✅ Errors обрабатываются через error notifications  

### Референсы из VS Code Extension
- `src/services/ListingService.ts` → `runListing()` логика построения params
- `src/services/ContextService.ts` → `runContext()` логика
- `src/cli/CliClient.ts` → `buildCliArgs()` функция (основа для CliArgsBuilder)
- `src/views/ControlPanelView.ts` → handlers для generate buttons

### Документация IntelliJ Platform
- `12-background-tasks.md` (секции: withBackgroundProgress, progress reporting)
- `18-dialogs.md` (секции: DialogWrapper basics, JTextArea в dialogs)

---

## 📋 Фаза 8: Virtual File Integration (Editor Display)

### Цель
Заменить modal dialog на отображение результатов в **редакторе** с syntax highlighting через `LightVirtualFile` или temporary files (в зависимости от Settings).

### Задачи

1. **`vfs/LgVirtualFileService` (Project-level):**
   - Методы:
     ```kotlin
     fun openListing(content: String, filename: String)
     fun openContext(content: String, filename: String)
     private fun createVirtualFile(content: String, filename: String): VirtualFile
     private fun createTempFile(content: String, filename: String): VirtualFile
     ```
   - Логика:
     - Если `settings.state.openAsEditable == false` → `LightVirtualFile` (read-only)
     - Иначе → temp file на диске в system temp dir
   - FileType определение по extension (.md, .yaml, .json)
   - Открытие через `FileEditorManager.openFile(file, focus=true)`

2. **Обновление Generation Actions:**
   - Заменить `showOutputDialog()` на `virtualFileService.openListing()`
   - Удалить временный dialog helper

3. **Filename generation:**
   - Listing: `"Listing — {section}.md"`
   - Context: `"Context — {template}.md"`
   - Sanitize имён файлов (убрать `/`, `:` и др. unsafe chars)

### Критерии готовности
✅ Generated content открывается в **editor tab** с Markdown syntax highlighting  
✅ Read-only mode: файл нельзя редактировать (LightVirtualFile)  
✅ Editable mode: temp файл на диске, можно редактировать  
✅ Переключение режима через Settings работает  

### Референсы из VS Code Extension
- `src/views/VirtualDocProvider.ts` (полностью) → логика выбора режима, sanitization
- Понимание `lg://` URI scheme (в IntelliJ заменяется на LightVirtualFile)

### Документация IntelliJ Platform
- `09-vfs.md` (секции: VirtualFile, LightVirtualFile, FileType)
- `20-editor-api.md` (секции: FileEditorManager, opening files)

---

## 📋 Фаза 9: Statistics Dialog (базовая версия)

### Цель
Реализовать `LgStatsDialog` для отображения детальной статистики с **простой нативной таблицей** (без группировки). Grouped table откладывается на Фазу 12.

### Задачи

1. **`services/generation/LgStatsService` (Project-level):**
   - Метод:
     ```kotlin
     suspend fun getStats(target: String): ReportSchema
     ```
   - Вызов `lg report ctx:... / sec:...` через `CliExecutor`
   - Парсинг JSON в typed `ReportSchema` data class

2. **`models/ReportSchema` и nested classes:**
   - По JSON schema из CLI
   - `ReportSchema`, `TotalsData`, `FileRow`, `ContextBlock`

3. **`ui/dialogs/LgStatsDialog`:**
   - Наследуется от `DialogWrapper`
   - Resizable, initial size 800×600
   - Структура через Kotlin UI DSL:
     
     - **Top section:** Summary cards (groups)
       - Source Data (size, tokens raw)
       - Processed Data (tokens processed, saved)
       - Rendered Data (если есть)
       - Final Rendered (для contexts)
     
     - **Middle section:** Task text field (JTextArea, editable)
       - onChange → debounced refresh stats
     
     - **Main section:** Files table
       - **Временно:** простой `JBTable` с columns: Path, Size, Raw, Processed, Saved, Saved%, Prompt%, Ctx%
       - **TODO комментарий:** "Phase 12: replace with LgGroupedTable component"
       - Sorting по колонкам (встроенный `TableRowSorter`)
       - Filter text field (простой текстовый фильтр по path)
     
     - **Bottom section (collapsible):**
       - Adapter Metrics (key-value pairs в простой таблице)
       - Raw JSON viewer (JTextArea, read-only, monospace)

4. **Actions в dialog toolbar:**
   - **Refresh** — пересчитать stats с текущим task text
   - **Send to AI** — stub notification "Phase 10"
   - **Generate** — stub notification "Phase 10"
   - **Copy JSON** — реализовать (в clipboard)

5. **`actions/LgShowStatsAction` (реализация):**
   - Определение target: context или section (в зависимости от заполненности в Control Panel)
   - Вызов `statsService.getStats()`
   - Открытие `LgStatsDialog`

### Критерии готовности
✅ "Show Stats" / "Show Context Stats" buttons открывают stats dialog  
✅ Summary cards отображают все метрики  
✅ Task text field редактируемый, изменения триггерят refresh  
✅ Таблица файлов показывает все данные, сортируется по колонкам  
✅ Фильтр по path работает  
✅ Adapter metrics и raw JSON показываются в collapsible sections  
✅ Copy JSON копирует полный JSON в clipboard  
✅ Dialog responsive и resizable  

### Референсы из VS Code Extension
- `media/stats.html` (полностью) → структура UI
- `media/stats.css` → styling reference (адаптировать под Swing constraints)
- `media/stats.js` → логика render, карты, таблицы
- `src/views/StatsWebview.ts` → построение данных, refetch logic

### Документация IntelliJ Platform
- `18-dialogs.md` (полностью)
- `05-ui-components.md` (секции: JBTable, JTextArea, JScrollPane)
- `07-kotlin-ui-dsl.md` (секции: groups, collapsible groups, scrollCell)

---

## 📋 Фаза 10: AI Integration Services

### Цель
Реализовать `AiIntegrationService` и базовые провайдеры (Clipboard, GitHub Copilot, Cursor), интегрировать "Send to AI" functionality во всех местах.

### Задачи

1. **`services/ai/AiIntegrationService` (Application-level):**
   - Registry паттерн для провайдеров: `Map<String, AiProvider>`
   - Методы:
     ```kotlin
     fun registerProvider(provider: AiProvider)
     suspend fun detectBestProvider(): String
     suspend fun sendTo(providerId: String, content: String)
     fun getAvailableProviders(): List<String>
     ```
   - Auto-detection при plugin startup
   - Fallback на clipboard если ничего не детектится

2. **`services/ai/AiProvider` interface:**
   ```kotlin
   interface AiProvider {
       val id: String
       val name: String
       suspend fun send(content: String)
   }
   ```

3. **Базовые провайдеры:**
   
   - **`ClipboardProvider`:**
     - `vscode.env.clipboard.writeText()` → `CopyPasteManager.getInstance().setContents()`
     - Success notification
   
   - **`CopilotProvider`:**
     - Проверка наличия GitHub Copilot extension через `ExtensionPointName`
     - Вызов команды (если есть public API) или fallback на clipboard + открытие Copilot Chat
   
   - **`CursorProvider`:**
     - Проверка Cursor IDE через доступные commands (`vscode.commands.getCommands()` аналог)
     - Вызов Cursor Composer команды или fallback

4. **`actions/LgSendToAiAction` (реализация):**
   - Определение: context или listing (в зависимости от Control Panel state)
   - Генерация через `LgGenerationService`
   - Отправка через `AiIntegrationService.sendTo(providerId, content)`
   - Provider ID из `LgSettingsService.state.aiProvider`
   - Error handling: notification с fallback "Copy to Clipboard"

5. **Интеграция в Stats Dialog:**
   - "Send to AI" button → вызов `AiIntegrationService` с текущим content
   - "Generate" button → генерация + открытие в editor

6. **Provider Selection UI в Settings:**
   - ComboBox в AI Integration секции
   - Динамический список через `getAvailableProviders()`

### Критерии готовности
✅ Clipboard provider работает (копирует и показывает notification)  
✅ Copilot provider детектится если extension установлен  
✅ Cursor provider детектится в Cursor IDE  
✅ "Send to AI" в Control Panel работает  
✅ "Send to AI" в Stats Dialog работает  
✅ Provider выбирается через Settings  
✅ Best provider выбирается автоматически при первом запуске  

### Референсы из VS Code Extension
- `src/services/ai/` (вся директория) → архитектура, базовые классы, провайдеры
- `src/extension.ts` → логика detectBestProvider
- `src/views/ControlPanelView.ts` → handler `onSendToAI()`

### Документация IntelliJ Platform
- `02-architecture.md` (секции: Extension Points, Service Locator, Message Bus)
- `13-notifications.md` (секции: notifications с actions, error handling)

---

## 📋 Фаза 11: Included Files Tree View

### Цель
Реализовать функциональное дерево файлов во второй вкладке Tool Window с переключением flat/tree режимов.

### Задачи

1. **`ui/toolwindow/LgIncludedFilesPanel` (полная реализация):**
   - Наследуется от `SimpleToolWindowPanel`
   - Использует `Tree` component с `DefaultTreeModel`
   - Два режима:
     - **Flat:** все файлы как flat list с полными путями
     - **Tree:** hierarchical структура по директориям
   
2. **Tree building logic:**
   - Метод `buildFlatTree(paths: List<String>)`
   - Метод `buildHierarchicalTree(paths: List<String>)`
   - Построение иерархии через map-based подход (как в VS Code)

3. **`ui/renderers/FileTreeCellRenderer`:**
   - `ColoredTreeCellRenderer`
   - Files: file type icon + name
   - Directories: folder icon + name (bold)

4. **Tree interactions:**
   - Double-click → открытие файла в editor через `FileEditorManager`
   - Context menu: Open File, Copy Path, Refresh
   - SpeedSearch (встроенный в Tree)

5. **`actions/LgShowIncludedFilesAction` (реализация):**
   - Вызов `lg report sec:...` для получения списка файлов
   - Парсинг `files[]` массива из `ReportSchema`
   - Обновление `LgIncludedFilesPanel.setPaths()`
   - Auto-switch на вкладку "Included Files"

6. **`actions/LgToggleTreeViewModeAction` (реализация):**
   - Toggle между FLAT и TREE
   - Сохранение в `LgWorkspaceStateService`
   - Перестроение дерева
   - Добавить в toolbar вкладки

7. **Toolbar для Included Files tab:**
   - Refresh, Toggle View Mode

### Критерии готовности
✅ "Show Included" загружает и отображает файлы в дереве  
✅ Flat mode: все файлы в плоском списке  
✅ Tree mode: hierarchical folders  
✅ Toggle view mode работает и сохраняется  
✅ Double-click открывает файл в редакторе  
✅ Context menu с Open/Copy Path работает  
✅ Auto-switch на вкладку при вызове "Show Included"  

### Референсы из VS Code Extension
- `src/views/IncludedTree.ts` (полностью) → логика построения tree/flat, toggle
- Понимание data flow при "Show Included" button click

### Документация IntelliJ Platform
- `17-tree-views.md` (полностью)
- `09-vfs.md` (секции: VirtualFile navigation, opening files)
- `08-actions.md` (секции: ToggleAction)

---

## 📋 Фаза 12: Grouped Table Component

### Цель
Разработать **переиспользуемый компонент** `LgGroupedTable` с фильтрацией, сортировкой и иерархической группировкой (как в VS Code Stats webview) и интегрировать в Stats Dialog.

### Задачи

1. **`ui/components/LgGroupedTable`:**
   - Наследуется от `JPanel` с `BorderLayout`
   - Содержит:
     - Toolbar: grouping level control (buttons `←` `N` `→` или slider), filter text field
     - `JBTable` с custom `TableModel`
   
   - **Grouping logic:**
     - Level 1..N: группировка по N сегментам пути
     - Level ∞: flat (без группировки)
     - Group rows: aggregated values (sum для numeric columns)
     - Indent для file rows под группами (через custom renderer)
   
   - **Filtering:**
     - Text filter по path или extension
     - Debounced (300ms)
   
   - **Sorting:**
     - Clickable column headers
     - Ascending/descending toggle
     - При группировке: сортировка групп, затем файлов внутри

2. **`GroupedTableModel`:**
   - Custom `AbstractTableModel`
   - Методы: `setData()`, `setGroupLevel()`, `setFilter()`, `setSorting()`
   - Rebuild rows при любом изменении параметров

3. **Интеграция в `LgStatsDialog`:**
   - Заменить простой `JBTable` на `LgGroupedTable`
   - Передать columns config и data из `ReportSchema.files`
   - Удалить TODO комментарий

### Критерии готовности
✅ Grouped table отображается в Stats Dialog  
✅ Grouping level control работает (1..N..∞)  
✅ Группы показывают aggregated values  
✅ Sorting работает для всех колонок  
✅ Filter по path/extension работает  
✅ UI responsive и не лагает на 100+ файлах  

### Референсы из VS Code Extension
- `media/ui/components/grouped-table/` (вся директория) → полная спецификация компонента
- `media/stats.js` → использование grouped table, columns config
- Логика нормализации путей, построения групп

### Документация IntelliJ Platform
- `05-ui-components.md` (секции: JBTable, custom TableModel, TableRowSorter)
- `07-kotlin-ui-dsl.md` (секции: custom components, cell())

---

## 📋 Фаза 13: Tags Configuration UI

### Цель
Реализовать UI для выбора тегов (аналог tags panel в VS Code) как **modal dialog** вместо overlay panel.

### Задачи

1. **`ui/dialogs/LgTagsDialog`:**
   - `DialogWrapper` с title "Configure Tags"
   - Resizable, размер 500×600
   - Содержимое через Kotlin UI DSL:
     - Для каждого tag-set: collapsible group с checkboxes
     - Checkbox для каждого тега в set
     - Description под checkbox (если есть)
   
2. **Data binding:**
   - При открытии: восстановить checked state из `LgPanelStateService.state.tags`
   - При Apply: сохранить выбранные теги в state
   - Return: `Set<String>` выбранных тегов

3. **`actions/LgConfigureTagsAction` (реализация для "Configure Tags" button):**
   - Открытие `LgTagsDialog`
   - Обновление `LgPanelStateService.state.tags`

4. **Visual feedback в Control Panel:**
   - Если теги выбраны: показать badge/indicator рядом с "Configure Tags" button (опционально)
   - Или subtitle под button: "5 tags selected"

### Критерии готовности
✅ "Configure Tags" button открывает modal dialog  
✅ Tag-sets отображаются в collapsible groups  
✅ Checkboxes синхронизированы с state  
✅ Выбранные теги сохраняются  
✅ Dialog responsive  

### Референсы из VS Code Extension
- `media/control.html` → секция tags panel (структура)
- `media/control.css` → `.tags-panel`, `.tag-set` стили (референс layout)
- `media/control.js` → `populateTagSets()`, `onTagChange()` логика

### Документация IntelliJ Platform
- `18-dialogs.md` (секции: DialogWrapper, resizable dialogs)
- `07-kotlin-ui-dsl.md` (секции: collapsibleGroup, checkboxes, groups)

---

## 📋 Фаза 13.5: Modes Configuration UI

### Цель
Реализовать UI для управления режимами (аналог `mode-sets-container` и `target-branch-container` в VS Code).

### Дополнительные детали

Наборы режимов не фиксированы и поступают из CLI динамически.  Смотреть уже сгенерированный класс `ModeSetsListSchema.kt` по JSON-схеме.

Сейчас в `lg/intellij/ui/toolwindow/LgControlPanel.kt` уже есть некоторая временная заглушка (и временная логика), чтобы наметить место в панели для управления режимами. Но чтобы не загромождать `LgControlPanel.kt` лучше всю логику построения UI для режимов вынести в отдельный файл. Но при этом нужно понимать, что визуально интерфейс не должен быть в виде отдельного диалогового окна, он по прежнему должен встраиваться в раздел "Adaptive Settings" основной контрольной панели.

Как и в **VS Code Extension** комбобокс для выбора целевой VCS ветки появляется, только если выбрать "review" режим в любом из наборов.

Так как мы выносим UI для управления режимами в отдельный модуль из `LgControlPanel.kt`, то нам для лучшего встраивания может пригодиться доступ к формированию неразрывной подписи к компонентам ввода. Поэтому метод `createLabeledComponent` (non-breakable label+component pair) можно также вынести, как внутреннюю библиотечную UI.

Настроенные режимы должны сохраняться в `lg/intellij/services/state/LgPanelStateService.kt`, а затем передаваться в построитель `lg/intellij/services/generation/CliArgsBuilder.kt` для фактического использования.

### Референсы из VS Code Extension
- `media/control.html`
- `media/control.css`
- `media/control.js`

### Документация IntelliJ Platform
- `05-ui-components.md`
- `07-kotlin-ui-dsl.md`

---

## 📋 Фаза 14: Doctor Diagnostics

### Цель
Реализовать diagnostics функционал: `lg diag` вызов, отображение результатов, bundle generation, cache reset.

### Задачи

1. **`services/LgDiagnosticsService` (Project-level):**
   - Методы:
     ```kotlin
     suspend fun runDiagnostics(): DiagReport
     suspend fun rebuildCache(): DiagReport
     suspend fun buildBundle(): Pair<DiagReport, String?> // (report, bundlePath)
     ```
   - Вызовы: `lg diag`, `lg diag --rebuild-cache`, `lg diag --bundle`
   - Парсинг stderr для bundlePath (regex matching)

2. **`models/DiagReport` и nested classes:**
   - По JSON schema
   - `DiagReport`, `DiagConfig`, `DiagCache`, `DiagCheck`, `DiagEnv`

3. **`ui/dialogs/LgDoctorDialog`:**
   - `DialogWrapper`, resizable
   - Структура:
     - Summary cards: Config, Cache, Contexts, Environment (как в VS Code)
     - Checks table (name, status icon, details)
     - Config details table
     - Cache details table
     - Applied migrations list (если есть)
     - Raw JSON viewer (collapsible)
   
   - Toolbar:
     - **Refresh** → re-run diag
     - **Rebuild Cache** → `diag --rebuild-cache`
     - **Build Bundle** → `diag --bundle` + notification с path
     - **Copy JSON**

4. **`actions/LgRunDoctorAction` (реализация):**
   - Вызов `diagnosticsService.runDiagnostics()` с progress
   - Открытие `LgDoctorDialog`

5. **`actions/LgResetCacheAction` (реализация):**
   - Confirmation dialog: "Reset LG cache?"
   - Вызов `diagnosticsService.rebuildCache()`
   - Success notification

### Критерии готовности
✅ "Doctor" button открывает диагностический dialog  
✅ Все секции отображаются с реальными данными  
✅ Checks table с иконками статуса (✔️⚠️❌)  
✅ Refresh/Rebuild/Bundle buttons работают  
✅ Bundle path показывается в notification  
✅ "Reset Cache" в Control Panel работает с confirmation  

### Референсы из VS Code Extension
- `src/services/DoctorService.ts` (полностью) → API методов
- `media/doctor.html` + `doctor.js` (полностью) → UI структура
- `src/views/DoctorWebview.ts` → integration logic

### Документация IntelliJ Platform
- `18-dialogs.md` (секции: complex dialogs, tables in dialogs)
- `05-ui-components.md` (секции: JBTable, cards layout)

---

## 📋 Фаза 15: Starter Config Wizard

### Цель
Реализовать wizard для создания `lg-cfg/` через `lg init` с preset selection и conflict resolution.

### Задачи

1. **`services/LgInitService` (Project-level):**
   - Методы:
     ```kotlin
     suspend fun listPresets(): List<String>
     suspend fun initWithPreset(preset: String, force: Boolean): InitResult
     ```
   - Вызовы: `lg init --list-presets`, `lg init --preset X [--force]`
   - Парсинг JSON response с `ok`, `conflicts[]`, `error`

2. **`models/InitResult`:**
   ```kotlin
   data class InitResult(
       val ok: Boolean,
       val conflicts: List<String> = emptyList(),
       val error: String? = null
   )
   ```

3. **`ui/dialogs/LgInitWizardDialog`:**
   - Simple `DialogWrapper` (не multi-step, упрощённый)
   - Содержимое:
     - Label: "Select preset for lg-cfg initialization"
     - ComboBox с presets (загружается асинхронно при открытии)
     - Description текущего preset (опционально)
   - Apply logic:
     - Вызов `initService.initWithPreset()`
     - Если conflicts → показать warning dialog: "Overwrite N files?"
     - Если Yes → повторить с `force=true`
   - Success → открыть `lg-cfg/sections.yaml` в editor

4. **`actions/LgCreateStarterConfigAction` (реализация):**
   - Открытие `LgInitWizardDialog`

5. **`actions/LgOpenConfigAction` (реализация):**
   - Попытка открыть `lg-cfg/sections.yaml`
   - Если не существует → предложение создать через wizard

### Критерии готовности
✅ "Create Starter Config" button открывает wizard  
✅ Presets загружаются из CLI  
✅ Инициализация создаёт файлы  
✅ Conflict resolution работает (force overwrite)  
✅ После успеха → sections.yaml открывается в editor  
✅ "Open Config" открывает существующий или предлагает создать  

### Референсы из VS Code Extension
- `src/starter/StarterConfig.ts` (полностью) → логика wizard, conflict handling
- Понимание `lg init` protocol

### Документация IntelliJ Platform
- `18-dialogs.md` (секции: basic dialogs, async loading in dialogs)
- `09-vfs.md` (секции: creating files, opening in editor)

---

## 📋 Фаза 16: Git Integration (Optional Dependency)

### Цель
Добавить опциональную интеграцию с Git для загрузки списка веток (для target branch selector).

### Задачи

1. **Добавить зависимость в `build.gradle.kts`:**
   ```kotlin
   bundledPlugin("Git4Idea")
   ```

2. **`plugin.xml`:**
   ```xml
   <depends optional="true" config-file="withGit.xml">
       Git4Idea
   </depends>
   ```

3. **`withGit.xml`:**
   ```xml
   <idea-plugin>
       <!-- Git-specific extensions, если понадобятся -->
   </idea-plugin>
   ```

4. **`services/git/LgGitService` (Project-level):**
   - Методы:
     ```kotlin
     fun isGitAvailable(): Boolean
     suspend fun getBranches(): List<String>
     fun getCurrentBranch(): String?
     ```
   - Использует `GitRepositoryManager`, `GitRepository`
   - Graceful degradation: если Git plugin нет → return emptyList

5. **Интеграция в `LgCatalogService`:**
   - Добавить `val branches: StateFlow<List<String>>`
   - Загрузка в `loadAll()` через `gitService.getBranches()`
   - При отсутствии Git → empty list (target branch selector скрывается автоматически)

6. **Обновление Control Panel:**
   - Target Branch ComboBox заполняется из `catalogService.branches`
   - Если branches пустой → ComboBox показывает "No Git repository" (disabled)

### Критерии готовности
✅ В проектах с Git → branches загружаются  
✅ Target Branch selector показывает реальные ветки  
✅ Текущая ветка выбрана по умолчанию  
✅ В проектах без Git → selector показывает "No branches available" и disabled  
✅ При отсутствии Git plugin → graceful degradation (пустой список)  

### Референсы из VS Code Extension
- `src/services/GitService.ts` (полностью) → API для Git integration
- `src/views/ControlPanelView.ts` → секция `fetchBranches()`, `populateBranches()`

### Документация IntelliJ Platform
- `19-git-integration.md` (полностью)
- `02-architecture.md` (секции: optional dependencies)

---

## 📋 Фаза 17: TextCompletionField

### Цель
Замена поля `encoderField` на более продвинутый `TextCompletionField`. Аналог кастомного компонента Autocomplete в VS Code Extension.

### Задачи

В **IntelliJ Platform** нет необходимости создавать свой кастомный компонент автосаджеста. Платформа сразу поставляться с нужным макрокомпонентом, расположенным в `com.intellij.openapi.externalSystem.service.ui.completion.*`.

У нас сейчас, к сожалению, нет выгруженной компактной документации по `TextCompletionField`.

Поэтому перед основной работой:

- Необходимо изучить документацию и примеры по использованию `TextCompletionField` (при помощи инструмента Context7 или WEB поиска).
- Подробнее изучить сам исходный код данного макрокомпонента в `cloned/intellij-community/platform/external-system-impl/src/com/intellij/openapi/externalSystem/service/ui/completion/`.
- Найти и изучить примеры использования `TextCompletionField` в выкачанных исходниках платформы и других плагинов `cloned/*`.

После полного понимая работы этого макрокомпонента, произвести итоговую интеграцию "Control Panel":

- Encoder field: заменить на использование макрокомпонента `TextCompletionField`.
- Suggestions из `TokenizerCatalogService.getEncoders(currentLib)`.
- Auto-reload suggestions при смене library.
- Отправка выбранного энкодера (в том числе и возможного произвольного варианта) в `LgPanelStateService` для дальнейшего использования в плагине.

### Критерии готовности
✅ Encoder field показывает autocomplete popup при typing  
✅ Custom encoder values можно вводить
✅ Данные выбранного энкодера передаются в CLI

### Референсы из VS Code Extension
- `media/ui/components/autosuggest/` (вся директория) → спецификация autocomplete
- `media/control.js` → как происходит работа с полем `encoderAutosuggest`
- `src/views/ControlPanelView.ts` → как происходит работа с загруженными encoders (перезагрузка от смены библиотек)

---

## 📋 Фаза 18: Actions Shortcuts & Menu Integration

### Цель
Добавить keyboard shortcuts для главных Actions и создать Action Group в Tools menu.

### Задачи

1. **Обновление `plugin.xml`:**
   
   ```xml
   <actions>
       <group id="LgMainGroup" 
              text="Listing Generator" 
              popup="true"
              icon="icons.LgIcons.Group">
           
           <action id="LgGenerateListing" 
                   class="lg.intellij.actions.LgGenerateListingAction"
                   text="Generate Listing"
                   icon="icons.LgIcons.Generate">
               <keyboard-shortcut 
                   keymap="$default" 
                   first-keystroke="control alt L"/>
           </action>
           
           <action id="LgGenerateContext"
                   class="lg.intellij.actions.LgGenerateContextAction"
                   text="Generate Context"
                   icon="icons.LgIcons.Generate">
               <keyboard-shortcut 
                   keymap="$default" 
                   first-keystroke="control alt C"/>
           </action>
           
           <action id="LgSendToAi"
                   class="lg.intellij.actions.LgSendToAiAction"
                   text="Send to AI">
               <keyboard-shortcut 
                   keymap="$default" 
                   first-keystroke="control alt A"/>
           </action>
           
           <separator/>
           
           <action id="LgShowStats" class="..."/>
           <action id="LgShowIncluded" class="..."/>
           
           <separator/>
           
           <action id="LgCreateConfig" class="..."/>
           <action id="LgOpenConfig" class="..."/>
           <action id="LgRunDoctor" class="..."/>
           
           <add-to-group group-id="ToolsMenu" anchor="last"/>
       </group>
   </actions>
   ```

2. **Override text для разных places:**
   - Main menu: короткие названия
   - Toolbar: иконки + optional текст
   - Find Action: полные описания

3. **Локализация всех Actions:**
   - Обновить `LgBundle.properties` с action.* ключами

### Критерии готовности
✅ **Tools → Listing Generator** submenu со всеми actions  
✅ Keyboard shortcuts работают:  
   - Ctrl+Alt+L → Generate Listing  
   - Ctrl+Alt+C → Generate Context  
   - Ctrl+Alt+A → Send to AI  
✅ Find Action (Ctrl+Shift+A) находит все LG actions  
✅ Локализация работает (русский bundle опционально)  

### Референсы из VS Code Extension
- `package.json` → секция `commands` (названия команд)
- Понимание keybindings (адаптировать под IntelliJ modifiers)

### Документация IntelliJ Platform
- `08-actions.md` (полностью — ключевая для этой фазы)
- `03-plugin-structure.md` (секции: Actions registration, keyboard shortcuts, localization)

---

## 📋 Фаза 19: OpenAI API Provider (Network Integration)

### Цель
Реализовать OpenAI API provider с безопасным хранением API ключа и HTTP интеграцией.

### Задачи

1. **Dependency на PasswordSafe API:**
   - Уже включён в platform, дополнительных зависимостей не требуется

2. **`services/ai/providers/OpenAiProvider`:**
   - Implements `AiProvider`
   - Методы:
     ```kotlin
     suspend fun send(content: String)
     private suspend fun getApiKey(): String
     private suspend fun sendRequest(content: String, apiKey: String)
     ```
   - Использует Java 11+ `HttpClient`
   - Endpoint: `https://api.openai.com/v1/chat/completions`
   - Request body: `{"model": "gpt-4o", "messages": [{"role": "user", "content": "..."}]}`
   - Timeout: 30 sec
   - Error handling: HTTP errors, timeout, invalid key

3. **Интеграция PasswordSafe:**
   ```kotlin
   private suspend fun getApiKey(): String {
       val attributes = CredentialAttributes(
           serviceName = "lg.intellij.openai.apiKey"
       )
       
       return PasswordSafe.instance.getPassword(attributes)
           ?: throw ApiKeyNotFoundException()
   }
   
   fun saveApiKey(key: String) {
       val attributes = CredentialAttributes(...)
       PasswordSafe.instance.set(attributes, Credentials(null, key))
   }
   ```

4. **Settings UI для API Key:**
   - Обновить `LgSettingsConfigurable`: passwordField для OpenAI key
   - "Test Connection" button (опционально)
   - Link на https://platform.openai.com/api-keys

5. **Регистрация provider:**
   - В `AiIntegrationService.init()`: `registerProvider(OpenAiProvider())`
   - Detection: проверка наличия сохранённого ключа

### Критерии готовности
✅ OpenAI provider появляется в списке available providers (если ключ сохранён)  
✅ API key сохраняется через PasswordSafe  
✅ Send to OpenAI отправляет HTTP request  
✅ Success notification показывается  
✅ Errors обрабатываются (invalid key, network error, timeout)  

### Референсы из VS Code Extension
- `src/services/ai/providers/openai/provider.ts` (полностью) → HTTP request structure
- `src/services/ai/base/BaseNetworkProvider.ts` → базовый паттерн для network providers

### Документация IntelliJ Platform
- Нет специальной документации для HTTP в предоставленных файлах
- Использовать Java 11+ HttpClient documentation (вне IntelliJ Platform docs)
- `14-persistence.md` (секции: PasswordSafe для sensitive data)

---

## 📋 Фаза 20: Polish, Testing & Documentation

### Цель
Финальная полировка UI, написание тестов, подготовка к публикации.

### Задачи

1. **UI Polish:**
   - Проверка всех диалогов и панелей в light/dark/high-contrast темах
   - Accessibility: keyboard navigation, screen reader labels
   - Consistent spacing через `JBUI.Borders` и `JBUI.insets()`
   - Icon consistency

2. **Unit Tests:**
   - `CliExecutorTest` — mock process, timeout, cancellation
   - `LgCatalogServiceTest` — mock CLI responses, JSON parsing
   - `LgGenerationServiceTest` — args building, stdin handling
   - `SettingsPersistenceTest` — state save/load

3. **Integration Tests:**
   - `ToolWindowTest` — visibility, tab creation
   - `StatsDialogTest` — data display, table operations
   - `ActionsTest` — enablement conditions, context data

4. **Documentation:**
   - `README.md` — installation, features, usage
   - `CHANGELOG.md` — версионирование
   - Inline code documentation (KDoc для public API)

5. **Plugin Verification:**
   - `./gradlew verifyPlugin` — проверка API compatibility
   - `./gradlew verifyPluginConfiguration`
   - Тестирование на разных версиях IDE (2024.1, 2024.2, 2024.3)

6. **Performance:**
   - Profiling с IntelliJ Platform Profiler
   - Lazy loading всех тяжёлых ресурсов
   - Cancellation всех background tasks

7. **Локализация:**
   - Полный `LgBundle_ru.properties` (русская локализация)

### Критерии готовности
✅ Все unit тесты проходят  
✅ Plugin Verifier не показывает критичных warnings  
✅ UI работает корректно во всех темах  
✅ Нет утечек памяти (проверка через Memory Profiler)  
✅ README.md актуален и полон  
✅ Plugin готов к публикации на JetBrains Marketplace  

### Референсы из VS Code Extension
- `README.md` → структура документации, описание features
- Весь проект — для финальной сверки функциональности

### Документация IntelliJ Platform
- `15-testing.md` (полностью)
- `01-getting-started.md` (секции: Plugin Verification, Publishing)

---

## 📊 Сводная таблица по фазам

| Фаза | Название | Ключевые компоненты | Референсы VS Code | Docs IntelliJ |
|------|----------|---------------------|-------------------|---------------|
| **0** | Персонализация | plugin.xml, icons, bundle | package.json, media/lg.svg | 01, 03 |
| **1** | CLI Foundation | CliExecutor, CliResolver | cli/, runner/ | 10, 12 |
| **2** | Settings | LgSettingsService, Configurable | package.json config | 07, 11, 14 |
| **3** | Tool Window Skeleton | Factory, двекладки placeholders | package.json views | 06, 02 |
| **4** | Control Panel UI | Полная верстка через UI DSL | control.html/css/js | 07, 05, 13 |
| **5** | Catalog Services | LgCatalogService, StateFlow | CatalogService.ts | 04, 12, 09 |
| **6** | State Management | Panel/Workspace state services | ControlPanelView.ts state | 14, 07 |
| **7** | Generation Services | LG generation, args builder | Listing/ContextService.ts | 12, 18 |
| **8** | Virtual File Display | LightVirtualFile, editor opening | VirtualDocProvider.ts | 09, 20 |
| **9** | Stats Dialog Basic | Dialog с простой таблицей | stats.html/js, StatsWebview.ts | 18, 05, 07 |
| **10** | AI Integration | AiIntegrationService, провайдеры | services/ai/ | 02, 13 |
| **11** | Included Files Tree | Tree view, flat/tree toggle | IncludedTree.ts | 17, 09, 08 |
| **12** | Grouped Table | Переиспользуемый компонент | grouped-table/ | 05, 07 |
| **13** | Tags Dialog | Modal для tag selection | control tags panel | 18, 07 |
| **14** | Doctor | Diagnostics dialog | DoctorService.ts, doctor.* | 18, 05 |
| **15** | Init Wizard | lg-cfg creation wizard | StarterConfig.ts | 18, 09 |
| **16** | Git Integration | Branch loading (optional) | GitService.ts | 19, 02 |
| **17** | Advanced UI | Autocomplete, polish | autosuggest/, chat-input/ | 05 |
| **18** | Shortcuts & Menu | Keyboard bindings, Tools menu | package.json commands | 08, 03 |
| **19** | OpenAI Provider | Network integration, PasswordSafe | openai/provider.ts | 14 |
| **20** | Testing & Docs | Tests, README, publish | Весь проект | 15, 01 |

---

## 🎯 Итоговые рекомендации

### Приоритизация фаз

**Must-have для MVP (фазы 0-11):**
- Покрывают 80% функционала
- Позволяют генерировать listings/contexts и отправлять в AI
- Базовая статистика и navigation

**Nice-to-have (фазы 12-19):**
- Улучшают UX
- Расширяют AI integration
- Advanced features

**Polish (фаза 20):**
- Production-ready качество

### Рекомендации по разработке

1. **Строго следовать порядку фаз** — каждая опирается на предыдущие
2. **Коммитить после каждой фазы** с descriptive message
3. **Тестировать в Development Instance** после каждой фазы
4. **Не пропускать TODO комментарии** — они critical для фаз, где откладываем реализацию
5. **Использовать Kotlin idioms** — data classes, sealed classes, delegation, coroutines
6. **Следовать IntelliJ naming conventions** — `Lg` префикс для всех классов

### Потенциальные риски

- **Фаза 12 (Grouped Table):** самый сложный custom компонент — может потребовать больше времени
- **Фаза 16 (Git):** опциональная зависимость требует осторожности с API compatibility
- **Фаза 19 (OpenAI):** network integration требует тщательной обработки ошибок

### Альтернативные подходы

Если grouped table окажется избыточно сложным — можно **пропустить Фазу 12** и оставить простую таблицу навсегда. Группировка — это nice-to-have, не must-have.