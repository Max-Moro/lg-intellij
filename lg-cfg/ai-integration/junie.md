# Интеграция с Junie, the AI coding agent by JetBrains

## Обзор

JunieProvider обеспечивает интеграцию Listing Generator с Junie — AI coding agent от JetBrains. Провайдер использует рефлексию для работы с Junie API без compile-time зависимостей.

## Архитектура

### Базовый класс

`JunieProvider` наследует `BaseExtensionProvider`, что обеспечивает:
- Автоматическую проверку наличия и активности плагина `org.jetbrains.junie`
- Корректную обработку ошибок при отсутствии плагина
- Унифицированный интерфейс для integration service

### Приоритет

**Priority: 70** (ниже чем JetBrains AI Assistant и GitHub Copilot, но выше CLI)

Обоснование:
- Junie специализирован на конкретных задачах (merge, refactoring), а не на общем диалоге
- JetBrains AI Assistant более универсален для контекстных запросов
- GitHub Copilot имеет лучшую интеграцию с редактором для кода

## API Integration

### Используемые классы (через рефлексию)

#### TaskService
```java
interface TaskService {
    suspend fun start(
        chainId: TaskChainId,
        context: ExplicitTaskContext,
        previousTasksInfo: PreviousTasksInfoProxy?,
        continuation: Continuation
    ): TaskId
}
```

Получение instance:
```kotlin
val taskService = TaskService.Companion.getInstance(project)
```

#### TaskChainId
```java
class TaskChainId(val id: UUID)
```

Создание:
```kotlin
val chainId = TaskChainId.Companion.new() // Генерирует новый UUID
```

#### ExplicitTaskContext
```java
class ExplicitTaskContext(
    val type: IssueType,              // ISSUE или CHAT
    val description: String,           // Текст задачи
    val explicitlySelectedContextFiles: List<Path> // Явно выбранные файлы
)
```

Создание для LG:
```kotlin
ExplicitTaskContext(
    type = IssueType.ISSUE,
    description = content, // Сгенерированный контекст
    explicitlySelectedContextFiles = emptyList() // Junie сам выберет файлы
)
```

#### IssueType
```java
enum class IssueType {
    ISSUE,  // Формальная задача (используется LG)
    CHAT    // Свободный диалог
}
```

## Процесс отправки контента

### 1. Получение TaskService
```kotlin
val taskServiceInterface = Class.forName(
    "com.intellij.ml.llm.matterhorn.junie.shared.tasks.TaskService",
    true,
    classLoader
)

val companionField = taskServiceInterface.getDeclaredField("Companion")
val companion = companionField.get(null)
val getInstanceMethod = companion::class.java.getMethod("getInstance", Project::class.java)

val taskService = getInstanceMethod.invoke(companion, getCurrentProject())
```

### 2. Создание TaskChainId
```kotlin
val taskChainIdClass = Class.forName(
    "com.intellij.ml.llm.matterhorn.junie.core.shared.tasks.TaskChainId",
    true,
    classLoader
)

val companionField = taskChainIdClass.getDeclaredField("Companion")
val companion = companionField.get(null)
val newMethod = companion::class.java.getMethod("new")

val taskChainId = newMethod.invoke(companion)
```

### 3. Создание ExplicitTaskContext
```kotlin
val explicitTaskContextClass = Class.forName(
    "com.intellij.ml.llm.matterhorn.ej.api.ExplicitTaskContext",
    true,
    classLoader
)

val issueTypeClass = Class.forName(
    "com.intellij.ml.llm.matterhorn.ej.api.IssueType",
    true,
    classLoader
)
val issueTypeField = issueTypeClass.getDeclaredField("ISSUE")
val issueType = issueTypeField.get(null)

val constructor = explicitTaskContextClass.getConstructor(
    issueTypeClass,
    String::class.java,
    List::class.java
)

val taskContext = constructor.newInstance(
    issueType,
    description,
    emptyList<Any>()
)
```

### 4. Вызов start() (suspend function)
```kotlin
kotlinx.coroutines.suspendCancellableCoroutine<Any> { continuation ->
    try {
        val startMethod = taskServiceInterface.declaredMethods.first {
            it.name == "start" && it.parameterCount == 4
        }
        
        startMethod.invoke(
            taskService,
            taskChainId,
            taskContext,
            null, // previousTasksInfo
            continuation
        )
    } catch (e: Exception) {
        continuation.resumeWith(Result.failure(e))
    }
}
```

## Особенности реализации

### 1. Без явного выбора файлов

LG отправляет **только текст задачи** (сгенерированный контекст) без указания конкретных файлов:
```kotlin
explicitlySelectedContextFiles = emptyList()
```

**Обоснование:**
- Junie сам проанализирует проект и выберет релевантные файлы
- LG уже включил весь необходимый контекст в description
- Позволяет Junie использовать свои механизмы контекстного поиска

### 2. IssueType.ISSUE

Используется тип **ISSUE** (а не CHAT):
- ISSUE — формальная задача с чётким description
- Подходит для структурированного контекста от LG
- Junie воспринимает это как задачу, требующую конкретных действий

### 3. Новая task chain для каждого запроса

Каждый вызов `sendToExtension()` создаёт новый `TaskChainId`:
- Избегается путаница с предыдущими задачами
- Каждый контекст обрабатывается независимо
- Junie UI показывает отдельную карточку задачи

### 4. Без previousTasksInfo

Параметр `previousTasksInfo` всегда `null`:
- LG не отслеживает историю задач Junie
- Каждый запрос — самодостаточный контекст
- Упрощает интеграцию

## Пользовательский опыт

### Что видит пользователь

1. **Control Panel → "Send to AI"**
   - Генерируется контекст (listing или context)
   - Отправляется в Junie через TaskService

2. **Junie UI**
   - Появляется новая task card
   - Description содержит весь сгенерированный контекст
   - Junie начинает анализ и предлагает решения

3. **Workflow**
   ```
   [LG Control Panel]
        ↓ Generate Context
   [Markdown Content]
        ↓ Send to AI
   [JunieProvider]
        ↓ TaskService.start()
   [Junie Task Card]
        ↓ Analysis
   [Suggested Changes]
   ```

## Error Handling

### Плагин не установлен
- `BaseExtensionProvider.isAvailable()` → `false`
- Провайдер не появляется в списке доступных
- При явной попытке отправки → `AiProviderException`

### Ошибка рефлексии
- `ClassNotFoundException` → логируется и пробрасывается как `AiProviderException`
- `NoSuchMethodException` → то же
- Пользователь видит notification с предложением скопировать в clipboard

### Ошибка выполнения task
- Exception от `TaskService.start()` → пробрасывается через continuation
- `AiProviderException` → показывается notification

## Тестирование

### Manual Testing

1. **Установить Junie plugin**
   ```
   Settings → Plugins → Marketplace → "Junie"
   ```

2. **Сгенерировать контекст**
   - Открыть LG Tool Window
   - Выбрать template или section
   - Нажать "Send to AI"

3. **Проверить Junie UI**
   - Должна появиться новая task
   - Description = сгенерированный контекст

### Проверка приоритета

```kotlin
// Settings → AI Provider
// При auto-detect должен выбраться в порядке:
// 1. JetBrains AI (90)
// 2. GitHub Copilot (80)
// 3. Junie (70)  ← если первые два недоступны
// 4. Clipboard (10)
```

### Проверка availability

```kotlin
val aiService = AiIntegrationService.getInstance()
val available = aiService.detectAvailableProviders()

// Junie должен быть в списке только если плагин установлен и активен
assert("jetbrains.junie" in available == isJuniePluginEnabled)
```

## Возможные улучшения

### 1. Поддержка Task History
Отслеживать предыдущие задачи и передавать `PreviousTasksInfoProxy`:
```kotlin
start(
    chainId,
    context,
    previousTasksInfo = taskHistory.getLastN(5)
)
```

### 2. Явный выбор файлов
Если LG знает список файлов в секции:
```kotlin
ExplicitTaskContext(
    type = IssueType.ISSUE,
    description = content,
    explicitlySelectedContextFiles = includedFiles.map { it.toPath() }
)
```

### 3. Режим CHAT
Для "свободных" запросов использовать `IssueType.CHAT`:
```kotlin
val type = if (isFormalTask) IssueType.ISSUE else IssueType.CHAT
```

### 4. Наблюдение за прогрессом
Использовать `TaskService.observeTaskChain()`:
```kotlin
val liveChain = taskService.observeTaskChain(chainId)
liveChain.collect { state ->
    // Показывать прогресс в LG UI
}
```

## Заключение

JunieProvider обеспечивает **простую и надёжную** интеграцию с Junie:
- Минимальная зависимость от API (только публичные интерфейсы)
- Полная изоляция через рефлексию
- Оптимальный приоритет для специализированных задач
- Готов к расширению для более сложных сценариев

Интеграция **полностью реализована** и готова к использованию.

