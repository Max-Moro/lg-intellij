# AI Integration Subsystem

Подсистема интеграции с AI-провайдерами для отправки сгенерированного контента.

## Архитектура

### Core Components

#### `AiProvider` (interface)
Базовый интерфейс для всех AI провайдеров:
```kotlin
interface AiProvider {
    val id: String              // Уникальный ID провайдера
    val name: String            // Человекочитаемое имя
    val priority: Int           // Приоритет для auto-detection (0-100)
    
    suspend fun send(content: String)     // Отправка контента
    suspend fun isAvailable(): Boolean    // Проверка доступности
}
```

#### `AiIntegrationService` (Application-level)
Центральный сервис для управления провайдерами:
- Registry паттерн для провайдеров
- Детекция доступных провайдеров
- Отправка контента в выбранный провайдер
- Fallback на clipboard при ошибках

### Base Classes

#### `BaseCliProvider`
Базовый класс для CLI-based провайдеров (например, Claude CLI):
- Проверка наличия CLI команды в PATH
- Создание временных файлов с контентом
- Выполнение команд

#### `BaseExtensionProvider`
Базовый класс для Extension-based провайдеров (JetBrains AI, GitHub Copilot):
- Проверка наличия и активности плагина
- Интеграция через API плагинов

#### `BaseNetworkProvider`
Базовый класс для Network-based провайдеров (OpenAI API):
- Безопасное хранение API токенов через PasswordSafe
- HTTP запросы с таймаутом
- Централизованная обработка ошибок сети

## Registered Providers

### Phase 1: Clipboard (реализован)
- **ID:** `clipboard`
- **Priority:** 10 (lowest, fallback)
- **Always available**
- Копирует контент в буфер обмена

### Phase 2: JetBrains AI Assistant (реализован)
- **ID:** `jetbrains.ai`
- **Priority:** 90 (highest)
- **Plugin ID:** `com.intellij.ml.llm`
- **API**: Использует рефлексию для вызова `ChatSessionHost.createChatSession()` и `ChatSession.send()`
- **Особенности**: Создаёт новую чат-сессию без автоприкрепления файлов

### Phase 3: GitHub Copilot (реализован)
- **ID:** `github.copilot`
- **Priority:** 80
- **Plugin ID:** `github.copilot`
- **API**: Использует рефлексию для вызова `CopilotChatService.query()` с `QueryOptionBuilder`
- **Особенности**: Создаёт новую сессию в режиме "Ask", скрывает приветственное сообщение

### Phase 4: Claude CLI (планируется)
- **ID:** `claude.cli`
- **Priority:** 50
- **CLI Command:** `claude`

### Phase 5: OpenAI API (планируется)
- **ID:** `openai.api`
- **Priority:** 40
- **Endpoint:** `https://api.openai.com/v1/chat/completions`

## Usage

### Register Provider

```kotlin
val service = AiIntegrationService.getInstance()
service.registerProvider(MyCustomProvider())
```

### Send Content

```kotlin
val service = AiIntegrationService.getInstance()

// Direct send
service.sendTo("clipboard", content)

// Send with fallback (recommended for UI)
val success = service.sendWithFallback("jetbrains.ai", content)
```

### Detect Providers

```kotlin
val service = AiIntegrationService.getInstance()

// All available providers (sorted by priority)
val providers = service.detectAvailableProviders()

// Best provider (highest priority)
val best = service.detectBestProvider()
```

## Integration Points

### LgSendToAiAction
Action для отправки сгенерированного контента в AI:
- Определяет target (context или listing) из Control Panel state
- Генерирует контент через LgGenerationService
- Отправляет в провайдер из Settings
- При ошибке предлагает fallback на clipboard

### Control Panel
Кнопка "Send to AI" → вызывает LgSendToAiAction

### Stats Dialog
Кнопка "Send to AI" → вызывает LgSendToAiAction с текущим task text

### Settings
ComboBox для выбора AI provider:
- Динамический список через `detectAvailableProviders()`
- Сохранение в `LgSettingsService.state.aiProvider`

## Error Handling

### AiProviderException
Базовое исключение для ошибок провайдеров:
```kotlin
throw AiProviderException("Failed to send: ${error.message}", error)
```

### Fallback Strategy
При ошибке отправки показывается notification с action "Copy to Clipboard":
```kotlin
try {
    aiService.sendTo(providerId, content)
} catch (e: AiProviderException) {
    // Show error notification with fallback option
    notification.addAction(
        NotificationAction.createSimple("Copy to Clipboard") {
            aiService.sendTo("clipboard", content)
        }
    )
}
```

## Testing

Для тестирования провайдеров:
1. Запустить plugin dev instance: `./gradlew runIde`
2. Открыть проект с `lg-cfg/`
3. Проверить Settings → AI Integration → Provider selection
4. Проверить "Send to AI" в Control Panel
5. Проверить "Send to AI" в Stats Dialog

## Future Enhancements

- [ ] Streaming responses (для диалоговых провайдеров)
- [ ] History сохранённых запросов
- [ ] Batch отправка нескольких секций
- [ ] Custom provider registration через Extension Point
- [ ] AI response preview в IDE

