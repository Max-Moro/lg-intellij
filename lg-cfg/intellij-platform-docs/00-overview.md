# Обзор IntelliJ Platform

## Что такое IntelliJ Platform

**IntelliJ Platform** — это не готовый продукт, а фреймворк для построения IDE (интегрированных сред разработки). Платформа используется JetBrains для создания собственных IDE, таких как:

- **IntelliJ IDEA** (Community и Ultimate)
- **PyCharm**
- **WebStorm**
- **PhpStorm**
- **CLion**
- **GoLand**
- **Rider**
- **DataGrip**
- **RustRover**
- и другие...

Также на базе IntelliJ Platform построены сторонние IDE, например **Android Studio** от Google.

## Open Source

IntelliJ Platform является Open Source проектом под лицензией Apache 2.0. Исходный код размещён на GitHub в репозитории [`JetBrains/intellij-community`](https://github.com/JetBrains/intellij-community).

**Важно:** отдельного репозитория "IntelliJ Platform" не существует. Платформа представляет собой практически полное совпадение с **IntelliJ IDEA Community Edition** — бесплатной версией IntelliJ IDEA.

Версия платформы определяется версией соответствующего релиза IntelliJ IDEA Community Edition. Например, для плагина под IntelliJ IDEA 2019.1.1 (build #191.6707.61) нужно использовать тот же номер сборки из репозитория `intellij-community`.

## Основные возможности платформы

IntelliJ Platform предоставляет всю необходимую инфраструктуру для создания rich IDE с поддержкой различных языков программирования:

### 1. Компонентная архитектура
- **Кросс-платформенность** (Windows, macOS, Linux)
- **JVM-based** приложение с мощным UI toolkit
- Система **Extension Points** и **Extensions** для расширяемости

### 2. UI Toolkit
- **Tool Windows** (боковые панели)
- **Tree Views** с быстрым поиском
- **Lists** с фильтрацией
- **Popup меню** и **Dialogs**
- **Toolbars** и **Actions**
- **Notifications** (неблокирующие уведомления)

### 3. Редактор кода
- Полнофункциональный **текстовый редактор**
- **Syntax highlighting** (подсветка синтаксиса)
- **Code folding** (сворачивание блоков кода)
- **Code completion** (автодополнение)
- **Code editing features** (рефакторинги, навигация и т.д.)
- Встроенный **image editor**

### 4. Project Model и Build System
- Абстракция **проектной модели** (Project, Module, SDK)
- Интеграция с **build системами** (Gradle, Maven и др.)
- **VCS integration** (Git, Mercurial и др.)

### 5. Debugging Infrastructure
- Языково-агностический **debugger**
- **Breakpoints** (в том числе advanced)
- **Call stacks**
- **Watch windows**
- **Expression evaluation**

### 6. Program Structure Interface (PSI)

**Самая мощная возможность платформы** — PSI (Program Structure Interface).

PSI — это набор функциональности для:
- **Парсинга файлов**
- Построения **синтаксических и семантических моделей** кода
- Создания **индексов** на основе этих данных

PSI используется для реализации:
- **Навигации** (по файлам, типам, символам)
- **Code completion** (автодополнение)
- **Find Usages** (поиск использований)
- **Code Inspections** (инспекции кода)
- **Quick Fixes** (быстрые исправления)
- **Refactorings** (рефакторинги)
- И многих других возможностей

Платформа включает парсеры и PSI модели для многих языков. Благодаря расширяемости можно добавить поддержку новых языков через плагины.

## Плагины

Продукты на базе IntelliJ Platform являются **расширяемыми приложениями**. Платформа полностью поддерживает систему плагинов.

### JetBrains Marketplace

JetBrains поддерживает [JetBrains Marketplace](https://plugins.jetbrains.com) — централизованную площадку для распространения плагинов. Плагины могут поддерживать один или несколько продуктов на базе платформы.

Также возможно распространять плагины через собственные **custom plugin repositories**.

### Возможности плагинов

Плагины могут расширять платформу множеством способов:
- Добавление простых **пунктов меню**
- Добавление полной поддержки **нового языка программирования**
- Интеграция **build систем**
- Реализация **debugger** для языка
- Добавление **инспекций кода**
- Создание **tool windows**
- И многое другое...

Многие существующие возможности IntelliJ Platform реализованы именно как плагины, которые можно включать или исключать в зависимости от потребностей конкретного продукта.

## Ключевые архитектурные компоненты

### Component-Driven Architecture

IntelliJ Platform построена на компонентной архитектуре с чёткими точками расширения:

- **Extension Points (EP)** — точки расширения, объявляемые платформой или другими плагинами
- **Extensions** — реализации, регистрируемые плагинами для расширения функциональности
- **Services** — компоненты с жизненным циклом на уровне Application, Project или Module
- **Actions** — команды, доступные через меню, toolbar, keyboard shortcuts

### Асинхронная природа

Платформа является **многопоточной средой**:
- **EDT (Event Dispatch Thread)** — поток для UI операций и записи данных
- **BGT (Background Threads)** — потоки для длительных операций и чтения данных
- **Read-Write Lock** — механизм синхронизации доступа к данным
- **Kotlin Coroutines** — современный подход к асинхронности (с 2024.1+)

### Virtual File System (VFS)

VFS — абстракция над файловой системой:
- Универсальный API для работы с файлами (на диске, в архивах, на HTTP-серверах и т.д.)
- **Snapshot-based** — поддержка снимка файловой системы в памяти
- **Event-driven** — уведомления об изменениях файлов
- **Persistent metadata** — возможность хранить дополнительные данные о файлах

## Целевая аудитория документации

Эта документация предназначена для разработчиков, создающих плагины для IntelliJ Platform на **Kotlin** (рекомендуемый язык). Также возможна разработка на Java, но некоторые современные API (например, Kotlin Coroutines) требуют использования Kotlin.

## Требования к разработчику

Для успешной разработки плагинов требуется:
- Опыт разработки на **Kotlin** или **Java**
- Понимание **Swing** (библиотека для UI)
- Знакомство с **Gradle** (система сборки)
- Базовое понимание **многопоточности** в JVM
- Желательно опыт работы с **IntelliJ IDEA** как пользователя

## Альтернативы плагинам

Не всегда необходимо создавать полноценный плагин. Существуют альтернативные решения:
- **Live Templates** — шаблоны кода
- **File Templates** — шаблоны файлов
- **Structural Search and Replace** — структурный поиск и замена
- **External Tools** — интеграция внешних инструментов
- **Макросы** — записываемые последовательности действий

Если задачу можно решить этими средствами, стоит рассмотреть их в первую очередь, так как они не требуют разработки и поддержки плагина.

## Rider — особый случай

JetBrains **Rider** использует IntelliJ Platform иначе, чем другие IDE:
- IntelliJ Platform обеспечивает **UI** (редакторы, tool windows, debugging UI)
- **ReSharper** (C#/.NET анализатор) работает как отдельный процесс
- PSI модель для C# создаётся в ReSharper, а не в IntelliJ Platform

Это означает, что плагин для Rider часто состоит из двух частей:
- **Frontend** (IntelliJ) — для UI
- **Backend** (ReSharper) — для анализа и работы с PSI

Многие плагины могут работать только с ReSharper backend, а Rider автоматически отображает результаты инспекций и автодополнения.

## Структура документации

Эта документация организована следующим образом:

1. **Базовые концепции** — архитектура, структура плагина, жизненный цикл
2. **UI компоненты** — Tool Windows, Actions, Dialogs, Kotlin UI DSL
3. **Работа с проектами** — VFS, PSI, Project Model
4. **Фоновые задачи** — Threading, Coroutines, Background Processes
5. **Настройки и конфигурация** — Settings, Persistence
6. **Примеры и best practices** — реальные примеры из открытых плагинов

## Полезные ссылки

- [IntelliJ Platform SDK Docs](https://plugins.jetbrains.com/docs/intellij/) — официальная документация
- [JetBrains Marketplace](https://plugins.jetbrains.com) — магазин плагинов
- [intellij-community](https://github.com/JetBrains/intellij-community) — исходный код платформы
- [intellij-plugins](https://github.com/JetBrains/intellij-plugins) — официальные плагины
- [intellij-sdk-code-samples](https://github.com/JetBrains/intellij-sdk-code-samples) — примеры кода
