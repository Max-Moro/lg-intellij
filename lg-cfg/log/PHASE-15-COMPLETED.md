# Фаза 15: Starter Config Wizard — Завершена ✅

## Реализованные компоненты

### 1. Models
- ✅ **`models/InitResult.kt`** — typed модель для результата `lg init`

### 2. Services
- ✅ **`services/LgInitService.kt`** (Project-level)
  - `listPresets(): List<String>` — загрузка доступных presets
  - `initWithPreset(preset: String, force: Boolean): InitResult` — инициализация с выбранным preset
  - Парсинг JSON responses от CLI
  - Error handling и fallback к "basic" preset

### 3. UI Dialogs
- ✅ **`ui/dialogs/LgInitWizardDialog.kt`**
  - Async loading presets при открытии
  - ComboBox для выбора preset
  - Status label для отображения состояния
  - Conflict resolution через confirmation dialog
  - Retry с `--force` при подтверждении overwrite
  - Открытие `sections.yaml` в editor после успеха
  - Корректный lifecycle management (dispose cancels coroutines)

### 4. Actions
- ✅ **`actions/LgCreateStarterConfigAction.kt`**
  - Открывает `LgInitWizardDialog`
  - Доступна в Control Panel toolbar и Tools menu
  
- ✅ **`actions/LgOpenConfigAction.kt`**
  - Открывает `lg-cfg/sections.yaml` в editor
  - Если не существует → предлагает создать через wizard
  - YesNo dialog для подтверждения
  - Доступна в Control Panel toolbar и Tools menu

### 5. Integration
- ✅ **Control Panel Toolbar** — замена заглушек на реальные Actions
- ✅ **Tools Menu** — регистрация Actions в `plugin.xml`
- ✅ **Localization** — все strings в `LgBundle.properties`

## Тестовые сценарии

### Сценарий 1: Успешная инициализация
1. ✅ Открыть Control Panel
2. ✅ Нажать "Create Starter Config" в toolbar
3. ✅ Дождаться загрузки presets (status: "Loading presets..." → "Ready to initialize")
4. ✅ Выбрать preset из ComboBox (по умолчанию первый)
5. ✅ Нажать OK
6. ✅ CLI выполняется: `lg init --preset basic`
7. ✅ Success notification: "Successfully created N file(s)"
8. ✅ `lg-cfg/sections.yaml` открывается в editor

### Сценарий 2: Conflict Resolution
1. ✅ `lg-cfg/sections.yaml` уже существует
2. ✅ Нажать "Create Starter Config"
3. ✅ Выбрать preset, нажать OK
4. ✅ CLI возвращает: `{ "ok": false, "conflicts": ["sections.yaml"], "message": "..." }`
5. ✅ Warning dialog: "lg-cfg already contains 1 file(s). Overwrite with --force?"
6. ✅ User clicks "Overwrite"
7. ✅ CLI выполняется: `lg init --preset basic --force`
8. ✅ Success notification + sections.yaml открывается

### Сценарий 3: Conflict Cancellation
1. ✅ Аналогично сценарию 2, но user выбирает "Cancel" в warning dialog
2. ✅ Dialog остаётся открытым (не закрывается)
3. ✅ User может выбрать другой preset или закрыть

### Сценарий 4: Open Config (существующий)
1. ✅ `lg-cfg/sections.yaml` существует
2. ✅ Нажать "Open Config" в toolbar
3. ✅ `sections.yaml` открывается в editor

### Сценарий 5: Open Config (не существует)
1. ✅ `lg-cfg/sections.yaml` не существует
2. ✅ Нажать "Open Config"
3. ✅ Question dialog: "lg-cfg/sections.yaml not found. Create starter config?"
4. ✅ User clicks "Create"
5. ✅ `LgInitWizardDialog` открывается
6. ✅ После успеха → sections.yaml открывается

### Сценарий 6: Tools Menu Access
1. ✅ Открыть **Tools → Listing Generator**
2. ✅ Доступны Actions:
   - ✅ Create Starter Config...
   - ✅ Open Config
   - ✅ Generate Listing
   - ✅ Generate Context
   - ✅ Run Diagnostics...

## Критерии готовности

✅ "Create Starter Config" button открывает wizard  
✅ Presets загружаются из CLI асинхронно  
✅ ComboBox заполняется после загрузки  
✅ Инициализация создаёт файлы через `lg init`  
✅ Conflict resolution работает (force overwrite confirmation)  
✅ После успеха → sections.yaml открывается в editor  
✅ "Open Config" открывает существующий или предлагает создать  
✅ Все strings локализованы в LgBundle.properties  
✅ Actions зарегистрированы в plugin.xml  
✅ Control Panel toolbar использует реальные Actions  
✅ Tools menu содержит LG action group  
✅ Никаких linter errors  

## Архитектурные решения

### Async Loading Presets
- Dialog не блокирует UI при загрузке presets
- OK button disabled до завершения загрузки
- Fallback к "basic" preset при ошибке

### Conflict Resolution Flow
- Первая попытка без `--force` (CLI protocol)
- При conflicts → user confirmation через native dialog
- Retry с `--force` только после подтверждения
- Graceful error handling на всех этапах

### VFS Integration
- Использование `refreshAndFindFileByNioFile()` для новых файлов
- Гарантия актуальности VFS snapshot перед открытием в editor
- NioPath API вместо устаревших File APIs

### Lifecycle Management
- CoroutineScope с SupervisorJob в Dialog
- Cancellation при dispose
- No memory leaks from abandoned coroutines

## Следующие шаги (Фаза 16)

Git Integration (опциональная зависимость):
- Интеграция с Git4Idea plugin
- Загрузка списка веток для target branch selector
- Graceful degradation если Git недоступен
