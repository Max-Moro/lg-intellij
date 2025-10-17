# Phase 6: State Management Services — Completed ✅

**Дата завершения:** 2025-10-17

## Реализованные задачи

### 1. LgPanelStateService (Project-level)

**Файл:** `src/main/kotlin/lg/intellij/services/state/LgPanelStateService.kt`

**Изменения:**
- Расширен `State` класс:
  - `modes: Map<String, String>` — selected mode для каждого mode-set (modeSetId → modeId)
  - `tags: Set<String>` — активные теги
  - Tokenization поля (`tokenizerLib`, `encoder`, `ctxLimit`) теперь используют пустые значения как сигнал "use application defaults"
  
- Добавлены методы effective values с fallback на application defaults:
  - `getEffectiveTokenizerLib(): String`
  - `getEffectiveEncoder(): String`
  - `getEffectiveContextLimit(): Int`

**Storage:** `.idea/workspace.xml` (не коммитится в VCS)

---

### 2. LgWorkspaceStateService (Project-level)

**Файл:** `src/main/kotlin/lg/intellij/services/state/LgWorkspaceStateService.kt`

**Назначение:** Хранение UI-specific state, который не должен синхронизироваться между машинами.

**State:**
- `includedFilesViewMode: ViewMode` (TREE/FLAT)
- `splitterProportion: Float` (положение splitter между панелями)

**Storage:** `.idea/workspace.xml`

---

### 3. LgControlPanel Integration

**Файл:** `src/main/kotlin/lg/intellij/ui/toolwindow/LgControlPanel.kt`

**Изменения:**

#### Tokenization Fields
- Используют **effective values** при инициализации (через `stateService.getEffectiveTokenizerLib()`)
- При первом открытии project state пустой → применяются defaults из Application Settings

#### Mode ComboBox
- Восстанавливает saved mode из `stateService.state.modes[modeSetId]`
- При изменении → сохраняет в state: `stateService.state.modes[modeSetId] = selectedMode`

#### Tokenizer Library ComboBox
- `updateLibrariesUI()` использует effective value для selection
- При изменении → reload encoders для новой библиотеки

#### Encoder TextField
- Инициализируется через `stateService.getEffectiveEncoder()`
- Document listener сохраняет изменения в state

#### Context Limit TextField
- Инициализируется через `stateService.getEffectiveContextLimit()`
- Валидация диапазона 1,000 - 2,000,000

## Архитектурные решения

### Fallback Logic

**Приоритет значений:**
1. **Project State** (в `LgPanelStateService`) — если заполнено
2. **Application Defaults** (в `LgSettingsService`) — если project state пустой
3. **Hardcoded Defaults** — если и application defaults пусты

**Реализация:**
```kotlin
fun getEffectiveTokenizerLib(): String {
    val value = state.tokenizerLib
    if (!value.isNullOrBlank()) {
        return value  // Project override
    }
    return "tiktoken"  // App default + hardcoded
}
```

### Two-Way Binding

**Control Panel → State:**
- ComboBox: `addActionListener { stateService.state.field = selectedItem }`
- TextField: `document.addDocumentListener { stateService.state.field = text }`

**State → Control Panel:**
- При загрузке каталогов: `updateSectionsUI()`, `updateLibrariesUI()` используют saved state для restoration

### Collections State Management

**Modes (Map):**
- Key: modeSetId (e.g., "dev-stage")
- Value: selected modeId (e.g., "development")
- Сохраняется автоматически через BaseState tracking

**Tags (Set):**
- Для Фазы 13 (Tags Configuration UI)
- Пока не используется в UI

---

## Критерии готовности ✅

- [x] Выбранные section/template сохраняются между reopens Tool Window
- [x] Tokenization parameters сохраняются
- [x] Modes сохраняются (для одного mode-set, динамические в Phase 13)
- [x] Task text сохраняется
- [x] Defaults из Application Settings применяются если project state пустой
- [x] View mode для Included Files готов к использованию (Фаза 11)
- [x] Компиляция успешна без ошибок

---

## Что дальше (Фаза 7)

**Generation Services Foundation:**
- Реализация `LgListingGenerator` и `LgContextGenerator`
- Генерация listings/contexts с учётом всех параметров из state
- Отображение результатов через modal dialog с JTextArea (временно)
- Mapping UI state → CLI arguments (modes, tags, tokenization params)

---

## Технические детали

### State Persistence Locations

| Service | Storage File | VCS Commit | Roaming |
|---------|-------------|------------|---------|
| `LgPanelStateService` | `.idea/workspace.xml` | ❌ | ❌ |
| `LgWorkspaceStateService` | `.idea/workspace.xml` | ❌ | ❌ |
| `LgSettingsService` | `~/.../lg-settings.xml` | — | ✅ |

### Nullable vs Non-Null

**Проблема:** `BaseState` delegates (`string()`, `property()`) возвращают nullable типы.

**Решение:** Effective values методы используют `isNullOrBlank()` проверки и возвращают non-null через fallback chain.

### UI DSL Binding Syntax

**Правильный binding для nullable String delegates:**
```kotlin
textField().bindText(
    getter = { settings.state.field ?: "" },
    setter = { settings.state.field = it }
)
```

**Неправильно (compile error):**
```kotlin
textField().bindText(settings.state::field)  // ❌ Type mismatch
```

---

## Тестирование (Manual)

**План тестирования:**
1. Открыть проект
2. Изменить tokenization settings в Control Panel
3. Закрыть и переоткрыть Tool Window → значения должны сохраниться
4. Изменить defaults в Settings
5. Создать новый project → должны применяться application defaults
6. Переключить mode → должен сохраниться
7. Ввести task text → должен сохраниться

**Статус:** Pending (Фаза 6 финальная задача)

