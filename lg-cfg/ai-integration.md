# AI Integration Services в IntelliJ Platform Plugin для Listing Generator

По сути инструмент LG в конечном счете формирует некоторые финальный строковый контент — результат команды `lg render …`. Сейчас мы в **IntelliJ Plugin** просто визуализируем этот строковый контент в виде временного файла. Было бы удобно поддерживать некоторую систему провайдеров, которые бы позволяли отправлять строковый контент сразу в некоторую среду работы с AI-агентом.

Аналогичные возможности у нас уже реализованы в **VS Code Extension**. Необходимо создать подобную подсистему провайдеров и для **IntelliJ Plugin**.

## Фаза 1. Реализация подсистемы AI-провайдеров и базовых классов провайдеров

Базовые классы провайдеров полностью аналогичны по смыслу **VS Code Extension**. Отсутствует `BaseForkProvider`, потому что сторонни компании обычно не форкают **IntelliJ Platform**, а создают для нее плагины (в отличие от Vs Code).

На данном этапе также можно создать конкретный универсальный провайдер — clipboard.

### Архитектура AI Integration

- Центральный сервис для AI провайдеров
- Registry паттерн для провайдеров
- Единая точка отправки контента в AI
- Методы: `detectProviders()`, `sendTo(providerId, content)`, `getAvailableProviders()`

#### `services/ai/base/*` (базовые классы провайдеров)
- Общий интерфейс `AiProvider`
- Базовые классы: `CliBasedProvider`, `ApiBasedProvider`, `ExtensionBasedProvider`

#### `services/ai/providers/*` (пакет с провайдерами)
- Реализации для каждого AI provider
- Graceful degradation если провайдер недоступен

### Референсы из VS Code Extension
- `src/services/ai/` (вся директория) → архитектура, базовые классы, провайдеры
- `src/extension.ts` → логика detectBestProvider
- `src/views/ControlPanelView.ts` → handler `onSendToAI()`

### Документация IntelliJ Platform
- `02-architecture.md` (секции: Extension Points, Service Locator, Message Bus)
- `13-notifications.md` (секции: notifications с actions, error handling)

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

3. **Базовые классы провайдеров и сразу готовый `ClipboardProvider`**

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
✅ "Send to AI" в Control Panel работает  
✅ "Send to AI" в Stats Dialog работает  
✅ Provider выбирается через Settings  

### Прочее

В отличие от VS Code нам не нужна детекция доступных провайдеров при старте аддона LG. Мы по умолчанию будем выбирать точно сразу доступный `clipboard`. Но при открытии настроек аддона `LgSettingsConfigurable` конечно же для комбобокса `row(LgBundle.message("settings.ai.provider.label"))` нужно предлагать не случайный набор провайдеров, а точно доступный (сдетектированный в текущем окружении).

Для реализации `BaseNetworkProvider` необходимо подобрать `HttpClient`. Если готового номинального решения нет в самой **IntelliJ Platform**, то для начала для простоты использовать `Java 11+ HttpClient`.

Для хранения ключей (секретов) для доступа к сетевому API лучше использовать `PasswordSafe` (описано в `lg-cfg/intellij-platform-docs/14-persistence.md`).

## Фаза 2. Реализация родного для **IntelliJ Platform** местного копайлота

Как `ExtensionBasedProvider` реализовать местные копайлот — **JetBrains AI Assistant**.

Для лучшей бесшовной интеграции изучить документацию по данному аддону и исходный код (если есть).

## Фаза 3. Реализация интеграции с плагином **GitHub Copilot**

Как `ExtensionBasedProvider` реализовать интеграцию с популярным расширением — **GitHub Copilot**.

Для лучшей бесшовной интеграции изучить документацию по данному аддону и исходный код (если есть).

## Фаза 4. Claude Code CLI

Как `CliBasedProvider` реализовать интеграцию с **Claude Code CLI** (инструмент от anthropic).

## Фаза 5. OpenAI API Provider (Network Integration)

Реализовать OpenAI API provider с безопасным хранением API ключа и HTTP интеграцией.
