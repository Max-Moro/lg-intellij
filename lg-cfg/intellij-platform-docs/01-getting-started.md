# Начало работы с разработкой плагинов

## Подготовка окружения

### Требования

Для разработки плагинов IntelliJ Platform вам понадобится:

1. **JDK 17+** — Java Development Kit
   - Рекомендуется использовать **JetBrains Runtime** (JBR), поставляемый с IntelliJ IDEA
   
2. **IntelliJ IDEA** — Community Edition или Ultimate
   - Рекомендуется всегда использовать **последнюю доступную версию**
   - Plugin DevKit plugin должен быть установлен и включён

3. **Gradle** — система сборки
   - Управляется автоматически через Gradle Wrapper
   - Не требует отдельной установки

### Выбор языка программирования

Плагины можно писать на:
- **Kotlin** ✅ (рекомендуется)
- **Java** ✅

**Почему Kotlin предпочтительнее:**
- Более лаконичный и выразительный синтаксис
- Null-safety из коробки
- Kotlin Coroutines для асинхронных операций
- Kotlin UI DSL для создания UI
- IntelliJ Platform активно переходит на Kotlin для новых API

**Ограничения Java:**
- Coroutine-based API недоступны из Java
- Нет доступа к Kotlin UI DSL
- Некоторые новые Extension Points требуют Kotlin

С версии 2024.1+ использование Kotlin становится практически обязательным для использования современных API (coroutines, suspending functions).

## Способы создания плагина

### 1. Через New Project Wizard (рекомендуется для начинающих)

1. Откройте IntelliJ IDEA
2. **File → New → Project**
3. Выберите **IDE Plugin** в левой панели
4. Укажите:
   - **Project name** — имя проекта
   - **Location** — где сохранить
   - **Language** — Kotlin или Java
   - **Build system** — Gradle (единственный вариант)
5. Нажмите **Create**

Wizard создаст минимальный рабочий плагин с необходимыми файлами.

### 2. IntelliJ Platform Plugin Template (рекомендуется для production)

[IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template) — GitHub template repository с:
- Готовой структурой проекта
- GitHub Actions CI workflows
- Автоматическим changelog
- Автоматической публикацией на JetBrains Marketplace
- Best practices из коробки

**Как использовать:**
1. Откройте https://github.com/JetBrains/intellij-platform-plugin-template
2. Нажмите **Use this template** → **Create a new repository**
3. Клонируйте созданный репозиторий
4. Откройте в IntelliJ IDEA

### 3. DevKit проект (устаревший подход)

Старый способ через DevKit ещё поддерживается, но рекомендуется только для:
- **Theme плагинов** (темы оформления)
- Поддержки legacy проектов

Для новых плагинов используйте **Gradle-based** подход.

## Структура плагин-проекта

После создания проекта вы получите следующую структуру:

```
my-plugin/
├── build.gradle.kts          # Gradle build script
├── gradle.properties          # Настройки Gradle
├── settings.gradle.kts        # Gradle settings
├── gradle/
│   └── wrapper/               # Gradle Wrapper
├── src/
│   └── main/
│       ├── kotlin/            # Исходный код (Kotlin)
│       │   └── com/example/
│       │       └── MyAction.kt
│       └── resources/
│           └── META-INF/
│               ├── plugin.xml        # Конфигурация плагина
│               └── pluginIcon.svg    # Иконка плагина
└── README.md
```

### Ключевые файлы

#### `build.gradle.kts`

Gradle build script с настройками плагина:

```kotlin
plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
    id("org.jetbrains.intellij.platform") version "2.0.1"
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.1")
        bundledPlugins("com.intellij.java")
        pluginVerifier()
        instrumentationTools()
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "com.example.myplugin"
        name = "My Plugin"
        version = "1.0.0"
        ideaVersion {
            sinceBuild = "241"
            untilBuild = "243.*"
        }
    }
}
```

**Важные параметры:**
- `intellijIdeaCommunity("2024.1")` — версия IntelliJ Platform
- `sinceBuild` / `untilBuild` — совместимость с версиями IDE
- `bundledPlugins` — зависимости от встроенных плагинов

#### `plugin.xml`

Конфигурационный файл плагина — сердце любого плагина:

```xml
<idea-plugin>
    <id>com.example.myplugin</id>
    <name>My Plugin</name>
    <vendor email="support@example.com" url="https://example.com">
        Example Company
    </vendor>

    <description><![CDATA[
        Short description of your plugin.
        <br/>
        <em>Supports HTML formatting</em>
    ]]></description>

    <!-- Зависимость от платформы (обязательно) -->
    <depends>com.intellij.modules.platform</depends>
    
    <!-- Опциональная зависимость от Java plugin -->
    <depends optional="true" 
             config-file="withJava.xml">
        com.intellij.modules.java
    </depends>

    <extensions defaultExtensionNs="com.intellij">
        <!-- Здесь регистрируются расширения -->
    </extensions>

    <actions>
        <!-- Здесь регистрируются действия -->
    </actions>
</idea-plugin>
```

## Gradle IntelliJ Platform Plugin

Современная разработка плагинов использует **Gradle IntelliJ Platform Plugin** (версия 2.x, актуальная).

Этот плагин управляет:
- **Зависимостями** плагина (базовая IDE + другие плагины)
- **Задачами сборки** (`buildPlugin`, `runIde`, `verifyPlugin`)
- **Публикацией** на JetBrains Marketplace
- **Тестированием** на разных версиях IDE

### Основные Gradle задачи

```bash
# Запустить IDE с плагином (Development instance)
./gradlew runIde

# Собрать плагин (.zip дистрибутив)
./gradlew buildPlugin

# Проверить совместимость с API платформы
./gradlew verifyPlugin

# Запустить тесты
./gradlew test

# Опубликовать на Marketplace
./gradlew publishPlugin
```

## Первый плагин — Hello World Action

Давайте создадим простое действие (Action), которое показывает диалог с сообщением.

### Шаг 1: Создайте Action класс

```kotlin
// src/main/kotlin/com/example/HelloAction.kt
package com.example

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

class HelloAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        Messages.showMessageDialog(
            project,
            "Hello, IntelliJ Platform!",
            "Greeting",
            Messages.getInformationIcon()
        )
    }
    
    override fun update(e: AnActionEvent) {
        // Действие доступно только когда проект открыт
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
```

### Шаг 2: Зарегистрируйте Action в plugin.xml

```xml
<idea-plugin>
    <!-- ... базовая информация ... -->
    
    <actions>
        <action 
            id="com.example.HelloAction"
            class="com.example.HelloAction"
            text="Say Hello"
            description="Shows a greeting dialog"
            icon="AllIcons.Actions.Help">
            
            <add-to-group 
                group-id="ToolsMenu" 
                anchor="first"/>
                
            <keyboard-shortcut 
                keymap="$default" 
                first-keystroke="control alt H"/>
        </action>
    </actions>
</idea-plugin>
```

### Шаг 3: Запустите и протестируйте

```bash
./gradlew runIde
```

Откроется новое окно IntelliJ IDEA (Development Instance) с вашим плагином:
- Откройте любой проект
- **Tools → Say Hello**
- Или нажмите **Ctrl+Alt+H**
- Появится диалог с приветствием

## Development Instance

При разработке плагина используется **Development Instance** — отдельная копия IDE, запускаемая с вашим плагином.

Особенности:
- Изолирована от основной установки IDE
- Собственные настройки и плагины
- Собственный каталог конфигурации
- Можно безопасно экспериментировать

## Debugging плагина

Для отладки плагина:

```bash
# Запуск с debugger
./gradlew runIde --debug-jvm
```

Или через IntelliJ IDEA:
1. Создайте **Run Configuration** типа **Gradle**
2. Tasks: `runIde`
3. Поставьте breakpoints в коде
4. Запустите через **Debug** (Shift+F9)

Debugger подключится к Development Instance и остановится на breakpoints.

## Тестирование плагина

IntelliJ Platform предоставляет мощный фреймворк для тестирования:

```kotlin
class HelloActionTest : BasePlatformTestCase() {
    
    fun testActionShowsDialog() {
        // Arrange
        val action = HelloAction()
        val event = testActionEvent()
        
        // Act & Assert
        action.actionPerformed(event)
        // Проверить что диалог показан...
    }
}
```

Запуск тестов:
```bash
./gradlew test
```

## Build Number Ranges

**Build number** — это номер сборки IDE в формате `XXX.YYYY.ZZ`, например `241.14494.240`.

Формат:
- `XXX` — **branch number** (241 = 2024.1, 242 = 2024.2 и т.д.)
- `YYYY` — **build counter** внутри ветки
- `ZZ` — **patch number** (опционально)

### Указание совместимости

В `plugin.xml` (или через Gradle):

```xml
<idea-version since-build="241" until-build="243.*"/>
```

Это означает:
- **since-build="241"** — плагин работает с 2024.1 и выше
- **until-build="243.*"** — до 2024.3 включительно (все патчи)
- **Без until-build** — совместимость со всеми будущими версиями (не рекомендуется)

## Типы плагинов

### По совместимости с IDE:

**1. Language Plugins**
- Добавляют поддержку языка программирования
- Требуют зависимости `com.intellij.modules.lang`

**2. IntelliJ IDEA Plugins**
- Работают только в IntelliJ IDEA
- Требуют зависимости `com.intellij.modules.java`

**3. Universal Plugins**
- Работают во всех IDE на базе платформы
- Требуют зависимости `com.intellij.modules.platform`

### По функциональности:

- **Tool Integration** — интеграция внешних инструментов
- **Custom Language Support** — поддержка языков
- **Framework Integration** — интеграция фреймворков
- **VCS Integration** — система контроля версий
- **UI Themes** — темы оформления

## Plugin DevKit

**Plugin DevKit** — встроенный плагин IntelliJ IDEA для помощи в разработке плагинов.

Возможности:
- **Инспекции кода** — проверка правильности использования API
- **Live Templates** — шаблоны для быстрого создания кода
- **Gutter Icons** — иконки в редакторе для быстрой навигации к Extension Points
- **Code Insight** — автодополнение для plugin.xml
- **Навигация** — между plugin.xml и классами

Проверьте что Plugin DevKit включён:
**Settings → Plugins → Installed → Plugin DevKit** ✓

## Проверка совместимости

Перед публикацией плагина важно проверить совместимость с разными версиями IDE.

### Plugin Verifier

Gradle задача для автоматической проверки:

```bash
./gradlew verifyPlugin
```

Проверяет:
- Использование deprecated API
- Использование internal API
- Бинарную совместимость
- Missing dependencies

Настройка в `build.gradle.kts`:

```kotlin
intellijPlatform {
    verifyPlugin {
        ides {
            recommended()  // Проверка на рекомендуемых версиях
        }
    }
}
```

## Dynamic Plugins

**Dynamic Plugins** — плагины, которые можно устанавливать/удалять/обновлять **без перезагрузки IDE**.

Это современный стандарт. Все новые плагины должны быть dynamic.

### Требования для dynamic plugin:

1. Не использовать deprecated **Components** (используйте **Services**)
2. Корректно освобождать ресурсы при выгрузке
3. Использовать **dynamic Extension Points** где возможно
4. Избегать утечек памяти

В `plugin.xml`:
```xml
<idea-plugin require-restart="false">
    <!-- ... -->
</idea-plugin>
```

Если `require-restart` не указан, по умолчанию `false` (dynamic).

### Тестирование dynamic behaviour

```bash
# Запустить IDE
./gradlew runIde

# В Development Instance:
# Settings → Plugins → Installed → Ваш плагин
# Попробуйте Disable → Enable (без перезагрузки)
```

## Именование и идентификаторы

### Plugin ID

Уникальный идентификатор плагина:
- Формат: как Java package (`com.example.myplugin`)
- **Должен быть стабильным** — нельзя изменить после публикации
- Используйте только: буквы, цифры, `.`, `-`, `_`

```xml
<id>com.example.listinggenerator</id>
```

### Action IDs

Каждый Action должен иметь уникальный ID:
- Рекомендуется использовать префикс с plugin ID
- Пример: `com.example.myplugin.MyAction`

```xml
<action 
    id="com.example.listinggenerator.GenerateListingAction"
    class="com.example.GenerateListingAction">
</action>
```

### Extension IDs

Extensions тоже могут иметь ID для ссылок:
```xml
<applicationService 
    serviceInterface="com.example.MyService"
    serviceImplementation="com.example.MyServiceImpl"/>
```

## Ресурсы и бандлы

### Resource Bundles (i18n)

Для локализации строк используйте message bundles:

```properties
# src/main/resources/messages/MyBundle.properties
action.hello.text=Say Hello
action.hello.description=Shows a greeting dialog
notification.success=Operation completed successfully
```

Регистрация в `plugin.xml`:
```xml
<resource-bundle>messages.MyBundle</resource-bundle>
```

Использование в коде:
```kotlin
import com.intellij.AbstractBundle
import org.jetbrains.annotations.PropertyKey

object MyBundle {
    private const val BUNDLE = "messages.MyBundle"
    private val bundle = ResourceBundle.getBundle(BUNDLE)
    
    fun message(
        @PropertyKey(resourceBundle = BUNDLE) key: String,
        vararg params: Any
    ): String = AbstractBundle.message(bundle, key, *params)
}

// Использование
val text = MyBundle.message("action.hello.text")
```

### Icons

Иконки для Actions, Tool Windows и т.д.:

```kotlin
// src/main/kotlin/icons/MyIcons.kt
package icons

import com.intellij.openapi.util.IconLoader

object MyIcons {
    @JvmField
    val MyAction = IconLoader.getIcon(
        "/icons/myAction.svg", 
        MyIcons::class.java
    )
}
```

Использование в `plugin.xml`:
```xml
<action icon="icons.MyIcons.MyAction" .../>
```

Или используйте встроенные иконки:
```xml
<action icon="AllIcons.Actions.Execute" .../>
```

## Публикация плагина

### Подготовка к публикации

1. **Обновите plugin.xml:**
   - Заполните `<description>` с HTML разметкой
   - Добавьте `<change-notes>` для текущей версии
   - Проверьте `<vendor>` информацию

2. **Создайте иконку плагина:**
   - `pluginIcon.svg` в `src/main/resources/META-INF/`
   - Размер: 40×40 для обычного display, 80×80 для Retina
   - Формат: SVG (предпочтительно) или PNG

3. **Протестируйте:**
   ```bash
   ./gradlew verifyPlugin
   ./gradlew test
   ```

### Публикация через Gradle

Настройте в `build.gradle.kts`:

```kotlin
intellijPlatform {
    publishing {
        token = System.getenv("PUBLISH_TOKEN")
    }
}
```

Опубликуйте:
```bash
export PUBLISH_TOKEN="your-token-from-marketplace"
./gradlew publishPlugin
```

### Публикация вручную

1. Соберите плагин:
   ```bash
   ./gradlew buildPlugin
   ```
   
2. Получите `.zip` файл из `build/distributions/`

3. Загрузите на https://plugins.jetbrains.com

## Логирование

Используйте встроенный логгер:

```kotlin
import com.intellij.openapi.diagnostic.Logger

class MyService {
    companion object {
        private val LOG = Logger.getInstance(MyService::class.java)
    }
    
    fun doSomething() {
        LOG.info("Starting operation")
        LOG.warn("Warning message")
        LOG.error("Error occurred", exception)
        LOG.debug("Debug info")
    }
}
```

Логи пишутся в `idea.log` файл.

Просмотр логов в IDE:
**Help → Show Log in Explorer/Finder**

## Best Practices

### 1. Используйте Services вместо Components
Components (deprecated) → Services:
```kotlin
// ❌ Старый подход (Components)
class MyComponent : ApplicationComponent

// ✅ Новый подход (Services)
@Service
class MyService
```

### 2. Избегайте блокировки EDT
```kotlin
// ❌ Плохо — блокирует UI
fun loadData() {
    val data = heavyComputation() // Долго
}

// ✅ Хорошо — фоновый поток
fun loadData() {
    CoroutineScope(Dispatchers.IO).launch {
        val data = heavyComputation()
        withContext(Dispatchers.EDT) {
            updateUI(data)
        }
    }
}
```

### 3. Всегда проверяйте validity объектов
```kotlin
if (!file.isValid) {
    return // Файл был удалён
}
```

### 4. Используйте Disposable для cleanup
```kotlin
class MyService : Disposable {
    private val connection = createConnection()
    
    override fun dispose() {
        connection.close()
    }
}
```

### 5. Следуйте threading rules
- **Чтение данных:** любой поток (в read action)
- **Запись данных:** только EDT (в write action через Application.invokeLater)

## Полезные инструменты разработки

### Internal Mode

Активируйте Internal Mode для доступа к дополнительным инструментам:
**Help → Edit Custom Properties**

```properties
idea.is.internal=true
```

После перезагрузки IDE:
- **Tools → Internal Actions** — множество инструментов для разработки
- **Tools → Internal Actions → UI → UI Inspector** — инспектор UI компонентов

### UI Inspector

Для понимания структуры существующих UI компонентов:
1. Активируйте Internal Mode
2. **Tools → Internal Actions → UI → UI Inspector**
3. Наведите на любой UI элемент
4. Увидите класс компонента, свойства, `added-at` (где был создан)

### Plugin DevKit Inspections

Включите все инспекции:
**Settings → Editor → Inspections → Plugin DevKit** — отметьте все галочки

Инспекции помогут:
- Найти неправильное использование API
- Предложить миграцию на современные API
- Обнаружить потенциальные проблемы с performance

## Полезные ссылки

- [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/) — официальная документация
- [IntelliJ Platform Explorer](https://plugins.jetbrains.com/intellij-platform-explorer) — поиск по Extension Points
- [Gradle IntelliJ Plugin](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html) — документация по Gradle плагину
- [Code Samples](https://github.com/JetBrains/intellij-sdk-code-samples) — примеры кода
- [Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template) — шаблон проекта
