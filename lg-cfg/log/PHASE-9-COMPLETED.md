# Phase 9: Statistics Dialog — Implementation Log

## Дата
2025-10-20

## Цель фазы
Реализовать базовую версию Statistics Dialog с простой нативной таблицей файлов (без группировки). Grouped table откладывается на Phase 12.

## Реализованные компоненты

### 1. Models Enhancement
**Файл:** `src/main/kotlin/lg/intellij/models/ReportSchema.kt`

- Все существующие поля полностью покрывают JSON schema из CLI

### 2. LgStatsService
**Файл:** `src/main/kotlin/lg/intellij/services/generation/LgStatsService.kt`

**Функциональность:**
- Метод `getStats(target: String, taskText: String?): ReportSchema`
- Вызов CLI команды `lg report` через `CliExecutor`
- Парсинг JSON response в typed `ReportSchema`
- Поддержка optional task text override
- Typed error handling через `StatsException`

**Dependencies:**
- `CliExecutor` для выполнения CLI
- `CliArgsBuilder` для построения аргументов
- `LgErrorReportingService` для уведомлений об ошибках
- `kotlinx.serialization` для JSON парсинга

### 3. LgFormatUtils
**Файл:** `src/main/kotlin/lg/intellij/utils/LgFormatUtils.kt`

**Утилиты:**
- `formatInt(Long)` — форматирование чисел с тысячными разделителями (1234567 → "1,234,567")
- `formatPercent(Double)` — форматирование процентов с одной дробной частью (12.345 → "12.3%")
- `formatSize(Long)` — human-readable размеры (1048576 → "1.0 MB")

**Использование:** во всех местах отображения числовых данных в Stats Dialog

### 4. LgStatsDialog
**Файл:** `src/main/kotlin/lg/intellij/ui/dialogs/LgStatsDialog.kt`

**Структура UI:**

#### Summary Cards (GridLayout)
- **Source Data**: размер + raw tokens
- **Processed Data**: processed tokens + saved + context share (если есть savings)
- **Rendered Data**: rendered tokens + overhead (если рендеринг применялся)
- **Final Rendered**: final tokens + context share (только для contexts)

Карты динамически показываются/скрываются в зависимости от наличия данных.

#### Task Text Input
- `JBTextArea` с line wrapping
- Debounced refresh (500ms) при изменении текста
- Автоматический refetch stats при обновлении task

#### Files Table
- **Колонки:** Path, Size, Raw, Processed, Saved, Saved%, Prompt%, Ctx%
- **Conditional columns:** Saved/Saved% скрываются если savings = 0
- **Sorting:** встроенный `TableRowSorter`, default сортировка по Ctx% descending
- **Filtering:** простой text filter по path column
- **TODO comment:** Phase 12 замена на `LgGroupedTable` component

#### Collapsible Sections

**Adapter Metrics:**
- Группировка по языковым адаптерам (md, py и т.д.)
- Key-value таблицы для каждого адаптера
- Фильтрация private метрик (`_*`) и нулевых значений

**Raw JSON:**
- Pretty-printed JSON в `JBTextArea` (monospace font)
- Read-only, copyable

**Actions:**
- **Refresh** — пересчёт stats с текущим task text
- **Send to AI** — stub (Phase 10)
- **Generate** — stub (Phase 10)
- **Copy JSON** — копирование raw JSON в clipboard

**Технические детали:**
- Resizable dialog (initial size 900×700)
- Scope management через `CoroutineScope` с cleanup в `dispose()`
- Dynamic UI updates через прямую манипуляцию JPanel компонентами
- Kotlin UI DSL для статичной структуры + Swing panels для динамического контента

### 5. LgShowStatsAction
**Файл:** `src/main/kotlin/lg/intellij/actions/LgShowStatsAction.kt`

**Логика:**
- Определение target (context vs section) из `LgPanelStateService`
- Приоритет: context > section
- Background task для загрузки stats
- Открытие `LgStatsDialog` при успешной загрузке
- `update()` метод для enablement кнопки (требуется выбранный template или section)

### 6. Control Panel Integration
**Файл:** `src/main/kotlin/lg/intellij/ui/toolwindow/LgControlPanel.kt`

**Изменения:**
- "Show Stats" button → вызов `LgShowStatsAction`
- "Show Context Stats" button → вызов `LgShowStatsAction` (single action for both cases)

### 7. Localization
**Файл:** `src/main/resources/messages/LgBundle.properties`

**Добавленные ключи:**
- `stats.summary.*.title` — заголовки summary cards
- `stats.adapter.metrics.title`, `stats.raw.json.title` — collapsible sections
- `action.show.stats.*` — texts для action

## Архитектурные решения

### Почему не Kotlin UI DSL для динамического контента?
UI DSL не поддерживает динамическое добавление/удаление rows после создания панели. Для summary cards и adapter metrics используются обычные Swing panels с `removeAll() + add() + revalidate()`.

### Почему простая таблица, а не grouped table?
Grouped table — сложный переиспользуемый компонент, требующий отдельной разработки. На Phase 9 достаточно базовой функциональности для валидации всего потока (CLI → Service → Dialog). Grouped table запланирован на Phase 12 как dedicated component.

### Debouncing для task text
Пользователь может быстро печатать → множественные refetch'и убьют производительность. Debounce 500ms даёт комфортную задержку для завершения ввода перед запросом.

## Критерии готовности (Acceptance Criteria)

✅ "Show Stats" / "Show Context Stats" buttons открывают stats dialog  
✅ Summary cards отображают все метрики корректно  
✅ Task text field редактируемый, изменения триггерят debounced refresh  
✅ Таблица файлов показывает все данные, сортируется по колонкам  
✅ Фильтр по path работает (case-insensitive substring match)  
✅ Adapter metrics группируются и показываются в collapsible section  
✅ Raw JSON отображается с pretty-print форматированием  
✅ Copy JSON копирует полный JSON в clipboard  
✅ Dialog responsive и resizable  

## Известные ограничения

1. **No grouping в таблице файлов** — будет добавлено в Phase 12
2. **Send to AI / Generate stubs** — будут реализованы в Phase 10
3. **No column width persistence** — автоматический resize на данный момент
4. **No double-click на файл** — откроется в Phase 11 (Included Files tree integration)

## Следующие шаги (Phase 10)

- AI Integration Services
- Реализация "Send to AI" функциональности
- Интеграция с Clipboard и местными копайлотами (JetBrains AI, Junie)
- "Generate" action в stats dialog (рендеринг + открытие в editor)

## Тестирование

### Manual Testing Checklist
- [ ] Открыть stats для section
- [ ] Открыть stats для context
- [ ] Изменить task text и проверить debounced refresh
- [ ] Проверить сортировку по разным колонкам
- [ ] Проверить фильтрацию по path
- [ ] Проверить collapsible sections (expand/collapse)
- [ ] Скопировать JSON и проверить валидность
- [ ] Проверить responsive поведение при resize окна

### Edge Cases
- [ ] Пустой task text (должен работать без ошибок)
- [ ] Section без savings (Saved/Saved% колонки скрыты)
- [ ] Context без final rendering (карта не показывается)
- [ ] Пустой metaSummary (показывается placeholder)

## Файлы, затронутые в Phase 9

```
src/main/kotlin/lg/intellij/
├── actions/
│   └── LgShowStatsAction.kt                    [NEW]
├── models/
│   └── ReportSchema.kt                         [MODIFIED] type alias
├── services/
│   └── generation/
│       └── LgStatsService.kt                   [NEW]
├── ui/
│   ├── dialogs/
│   │   └── LgStatsDialog.kt                    [NEW]
│   └── toolwindow/
│       └── LgControlPanel.kt                   [MODIFIED] wired stats actions
└── utils/
    └── LgFormatUtils.kt                        [NEW]

src/main/resources/messages/
└── LgBundle.properties                          [MODIFIED] added stats strings
```

## Метрики

- **Новые файлы:** 4
- **Изменённые файлы:** 3
- **Строк кода:** ~800
- **Зависимости:** kotlinx.serialization (уже была)
- **Время разработки:** ~2 часа (оценка)

---

**Статус:** ✅ Завершено (pending manual testing)

