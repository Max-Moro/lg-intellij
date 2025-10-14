## ✅ Фаза 2: Settings Infrastructure — ЗАВЕРШЕНА

### Реализовано:

1. **`LgSettingsService`** (Application-level)
   - State с полями: `cliPath`, `pythonInterpreter`, `installStrategy`, `aiProvider`, `openAsEditable`
   - Использует `SimplePersistentStateComponent` для автоматической персистентности
   - Storage: `lg-settings.xml` в application config directory
   - Roaming enabled (синхронизация между машинами)

2. **`LgSettingsConfigurable`**
   - Полная UI через Kotlin UI DSL
   - 3 группы настроек:
     - **CLI Configuration**: explicit paths, install strategy
     - **AI Integration**: provider selection, OpenAI key stub
     - **Editor Behavior**: open as editable checkbox
   - Validation для critical fields
   - "Reset to Defaults" button
   - Auto-save через `BoundConfigurable`

3. **`CliResolver` интеграция**
   - Полная реализация resolution chain (идентична VS Code Extension):
     1. Explicit path from Settings
     2. System strategy with configured Python
     3. Search in PATH
     4. Python module with configured interpreter
     5. Auto-detected Python fallback
   - Кэширование resolved path
   - Автоматическая invalidation через listener

4. **`LgSettingsChangeListener`**
   - Message Bus интеграция
   - Auto-invalidation CLI resolver cache при изменении настроек

5. **Локализация**
   - Все UI strings в LgBundle.properties

### Критерии готовности:
✅ Settings открываются через **Tools → Listing Generator**  
✅ Все поля редактируются и сохраняются между перезапусками IDE  
✅ CliResolver использует CLI path из Settings  
✅ Проект успешно собирается без ошибок

### Файлы:
- LgSettingsService.kt
- LgSettingsConfigurable.kt
- CliResolver.kt (обновлён)
- CliExecutor.kt (обновлён для Python module invocation)
- LgSettingsChangeListener.kt
- LgBundle.properties (обновлён)
- plugin.xml (обновлён)
