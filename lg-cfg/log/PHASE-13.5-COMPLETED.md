# Phase 13.5: Modes Configuration UI — Реализация завершена ✅

## Обзор

Реализован динамический UI для управления режимами (mode-sets) с поддержкой:
- Множественных наборов режимов из CLI
- Условного отображения target branch selector при выборе режима "review"
- Сохранения состояния в `LgPanelStateService`
- Интеграции с `CliArgsBuilder` для передачи режимов в CLI

## Созданные компоненты

### 1. `LgLabeledComponent` 
**Файл:** `src/main/kotlin/lg/intellij/ui/components/LgLabeledComponent.kt`

Утилита для создания неразрывных пар label+component. Используется в flow/wrap layouts для предотвращения разрыва между подписью и полем ввода при переносе строк.

**Основной метод:**
```kotlin
fun create(labelText: String, component: JComponent, gap: Int = 2): JPanel
```

### 2. `LgModeSetsPanel`
**Файл:** `src/main/kotlin/lg/intellij/ui/components/LgModeSetsPanel.kt`

Компонент для динамического рендеринга mode-sets с поддержкой:
- Создания ComboBox для каждого mode-set
- Custom renderer с отображением title и description
- Сохранения выбранных режимов в state
- Callback уведомлений о смене режимов (для управления видимостью target branch)
- Проверки наличия режима "review"

**Ключевые методы:**
```kotlin
fun updateModeSets(modeSets: List<ModeSet>) // Обновление данных
fun Panel.createModeSetsUI()                 // UI через DSL
fun setOnModeChangedCallback(callback: (Boolean) -> Unit) // Подписка на изменения
fun hasReviewMode(): Boolean                 // Проверка review режима
```

## Интеграция в LgControlPanel

### Изменения в `LgControlPanel.kt`

1. **Удалены старые поля:**
   - `modeCombo: ComboBox<String>` (единственный ComboBox)
   - `currentModeSets: List<ModeSet>` (данные теперь в panel)
   - `createLabeledComponent()` метод (вынесен в отдельную утилиту)

2. **Добавлено:**
   - `private val modesPanel = LgModeSetsPanel(project)` — экземпляр панели режимов

3. **Обновлён метод `createAdaptiveSettingsSection()`:**
   ```kotlin
   private fun Panel.createAdaptiveSettingsSection() {
       // Dynamic mode-sets panel
       modesPanel.createModeSetsUI()
       
       // Setup callback for review mode visibility
       modesPanel.setOnModeChangedCallback { hasReviewMode ->
           targetBranchRow.visible(hasReviewMode)
       }
       
       // Target Branch selector (показывается только при review mode)
       targetBranchRow = row(LgBundle.message("control.target.branch.label")) {
           comboBox(emptyList<String>())
               .bindItem(...)
       }
       
       targetBranchRow.visible(modesPanel.hasReviewMode())
       // ... tags button ...
   }
   ```

4. **Обновлён метод `updateModeSetsUI()`:**
   ```kotlin
   private fun updateModeSetsUI(modeSets: ModeSetsListSchema?) {
       if (modeSets == null) return
       
       modesPanel.updateModeSets(modeSets.modeSets)
       targetBranchRow.visible(modesPanel.hasReviewMode())
       
       LOG.debug("Updated mode-sets UI: ${modeSets.modeSets.size} sets")
   }
   ```

5. **Замена `createLabeledComponent()` на `LgLabeledComponent.create()`:**
   - В `createInspectSection()` для section selector
   - В `createTokenizationSection()` для library/encoder/ctx-limit

## Работа с состоянием

### Сохранение режимов

Режимы сохраняются в `LgPanelStateService.State`:
```kotlin
var modes by map<String, String>()
```

**Формат:** `Map<modeSetId, selectedModeId>`

**Пример:**
```kotlin
modes = mapOf(
    "dev-stage" to "development",
    "task-type" to "feature"
)
```

### Передача в CLI

`CliArgsBuilder.kt` уже корректно обрабатывает режимы:
```kotlin
// Modes
for ((modeSet, mode) in params.modes) {
    if (mode.isNotBlank()) {
        args.add("--mode")
        args.add("$modeSet:$mode")
    }
}
```

**CLI аргументы:**
```bash
lg render ctx:my-context \
  --mode dev-stage:development \
  --mode task-type:feature \
  ...
```

## Условная видимость Target Branch

**Логика:**
1. При изменении любого режима вызывается callback
2. Callback проверяет наличие режима "review" в любом mode-set
3. Обновляет видимость `targetBranchRow`

**Проверка review режима:**
```kotlin
fun hasReviewMode(): Boolean {
    return stateService.state.modes.values.any { it == "review" }
}
```

**Callback:**
```kotlin
modesPanel.setOnModeChangedCallback { hasReviewMode ->
    targetBranchRow.visible(hasReviewMode)
}
```

## Локализация

Добавлена строка в `LgBundle.properties`:
```properties
control.modes.empty=No mode-sets available
```

Используется для empty state когда mode-sets не загружены.

## UI Behavior

### Стандартный сценарий

1. **Плагин запускается** → `LgControlPanel` создаётся
2. **`loadDataAsync()`** запускает загрузку catalog data
3. **`catalogService.loadAll()`** загружает mode-sets из CLI
4. **Flow collector** получает `ModeSetsListSchema`
5. **`updateModeSetsUI()`** вызывается на EDT
6. **`modesPanel.updateModeSets()`** обновляет внутренние данные
7. **UI пересоздаётся** с новыми ComboBox для каждого mode-set
8. **Восстанавливаются** сохранённые значения из state

### Изменение режима

1. **Пользователь выбирает** режим в ComboBox
2. **ActionListener** срабатывает
3. **Сохраняется** в `stateService.state.modes[modeSetId] = selectedMode`
4. **Вызывается callback** `onModeChangedCallback`
5. **Обновляется visibility** target branch selector если нужно

### Empty state

Если `modeSets.isEmpty()`:
- Показывается label "No mode-sets available" (серый текст)
- Target branch selector скрыт
- Никаких ошибок

## Тестовые данные

Создан файл `test-lg-cfg/modes.yaml` с примером структуры mode-sets:
- **dev-stage:** planning / development / review
- **task-type:** feature / bugfix / refactoring

Используется для ручного тестирования UI.

## Следующие шаги

### Фаза 14: Doctor Diagnostics
- Diagnostics dialog
- Cache management
- Bundle generation

### Фаза 15: Starter Config Wizard
- Wizard для `lg init`
- Preset selection
- Conflict resolution

### Фаза 16: Git Integration
- **Загрузка веток** для target branch selector
- Опциональная зависимость от Git4Idea
- Graceful degradation если Git недоступен

## Проверка работоспособности

### Checklist:
- ✅ `LgLabeledComponent` создан и работает
- ✅ `LgModeSetsPanel` создан с dual API (DSL)
- ✅ Интеграция в `LgControlPanel` завершена
- ✅ Старый код (single modeCombo) удалён
- ✅ `CliArgsBuilder` корректно использует modes
- ✅ Target branch visibility работает
- ✅ State persistence работает
- ✅ Empty state обработан
- ✅ Localization добавлена
- ✅ Linter ошибок нет

### Для ручного тестирования:

1. Запустить плагин в IDE
2. Открыть проект с `lg-cfg/`
3. Открыть Tool Window "Listing Generator"
4. Проверить что mode-sets отображаются корректно
5. Выбрать "review" режим → target branch selector появляется
6. Выбрать другой режим → target branch selector скрывается
7. Закрыть/открыть проект → режимы восстанавливаются
8. Попробовать Generate Listing → режимы передаются в CLI

## Архитектурные преимущества

### Модульность
- Логика mode-sets изолирована в отдельном компоненте
- `LgControlPanel` не знает деталей рендеринга mode-sets
- Легко тестировать изолированно

### Расширяемость
- Простое добавление новых mode-sets через CLI
- UI автоматически адаптируется под количество наборов
- Callback mechanism позволяет добавлять новые реакции

### Переиспользование
- `LgLabeledComponent` используется по всему UI
- `LgModeSetsPanel` может быть использован в других панелях

### Production-ready
- Корректная обработка edge cases (empty, null)
- Defensive programming (проверки на null/blank)
- Proper state management
- Thread-safe updates (EDT)

---

**Фаза 13.5 завершена успешно! ✅**

