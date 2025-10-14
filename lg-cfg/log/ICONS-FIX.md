# Исправление адаптации иконок под темы

## Проблема

Иконки `pluginIcon.svg` и `toolWindow.svg` не адаптировались под светлую и тёмную темы IDE.

## Причина

Использование `currentColor` в SVG не гарантирует правильную адаптацию иконок под темы в IntelliJ Platform. Согласно [официальной документации](https://plugins.jetbrains.com/docs/intellij/icons.html#filenames), платформа требует:

1. **Отдельные файлы для тёмной темы** с суффиксом `_dark.svg`
2. **Специфические цвета для New UI**:
   - Light theme: `#6C707E`
   - Dark theme: `#CED0D6`

## Решение

### 1. Plugin Icon (40×40)

**Создано:**
- `pluginIcon.svg` - для светлой темы (цвет: `#6C707E`)
- `pluginIcon_dark.svg` - для тёмной темы (цвет: `#CED0D6`)

**Изменения:**
- Заменён `currentColor` на конкретные цвета
- Добавлены атрибуты `width="40"` и `height="40"` (required)
- Фон сделан полупрозрачным для лучшей адаптации

### 2. Tool Window Icon (13×13)

**Создано:**
- `toolWindow.svg` - для светлой темы (цвет: `#6C707E`)
- `toolWindow_dark.svg` - для тёмной темы (цвет: `#CED0D6`)

**Изменения:**
- Заменён `currentColor` на конкретные цвета
- Добавлены атрибуты `width="13"` и `height="13"` (required)

## Как работает автоматическая загрузка

IntelliJ Platform автоматически выбирает правильный вариант иконки:

| Условие | Загружаемая иконка |
|---------|-------------------|
| Light Theme | `iconName.svg` |
| Dark Theme | `iconName_dark.svg` |
| HiDPI + Light | `iconName@2x.svg` (если есть) |
| HiDPI + Dark | `iconName@2x_dark.svg` (если есть) |

Платформа использует naming convention для автоматического определения нужного варианта.

## Структура файлов

```
src/main/resources/
├── META-INF/
│   ├── pluginIcon.svg           # Light theme (40×40)
│   └── pluginIcon_dark.svg      # Dark theme (40×40)
└── icons/
    ├── toolWindow.svg           # Light theme (13×13)
    └── toolWindow_dark.svg      # Dark theme (13×13)
```

## Проверка

После пересборки плагина (`./gradlew build`) иконки будут автоматически адаптироваться:
- В светлой теме будут использоваться серые иконки (`#6C707E`)
- В тёмной теме будут использоваться светлые иконки (`#CED0D6`)

## Дополнительно: New UI Support (опционально)

Для полной поддержки New UI (2022.3+) можно добавить:

### Outlined Tool Window Icons

Для New UI рекомендуются outlined иконки размером 20×20 (обычный режим) и 16×16 (compact mode):

```
icons/
├── toolWindow.svg              # 16×16 (старый UI + New UI Compact)
├── toolWindow_dark.svg         # 16×16 dark
├── toolWindow@20x20.svg        # 20×20 (New UI normal)
└── toolWindow@20x20_dark.svg   # 20×20 dark
```

### Icon Mapping

Создать `LgIconMappings.json` и зарегистрировать через `com.intellij.iconMapper` extension point.

**Примечание:** Это опционально и может быть добавлено в будущих фазах если потребуется поддержка New UI.

## Ссылки

- [IntelliJ Platform Icons Documentation](https://plugins.jetbrains.com/docs/intellij/icons.html)
- [Icon Naming Conventions](https://plugins.jetbrains.com/docs/intellij/icons.html#filenames)
- [New UI Icon Colors](https://plugins.jetbrains.com/docs/intellij/icons.html#new-ui-icon-colors)
