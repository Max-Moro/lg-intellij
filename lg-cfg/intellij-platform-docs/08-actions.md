# Actions в IntelliJ Platform

## Что такое Action

**Action** — это команда, доступная пользователю через:
- **Меню** (File, Edit, Tools и т.д.)
- **Toolbar** (кнопки на панели инструментов)
- **Context menu** (правый клик)
- **Keyboard shortcuts** (горячие клавиши)
- **Find Action** (Ctrl+Shift+A)

Примеры стандартных Actions:
- File → Open File
- Edit → Copy/Paste
- Tools → Generate JavaDoc
- Help → About

## Базовый класс AnAction

Все actions наследуются от [`AnAction`](https://github.com/JetBrains/intellij-community/blob/master/platform/editor-ui-api/src/com/intellij/openapi/actionSystem/AnAction.java).

### Минимальная реализация

```kotlin
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class MyAction : AnAction() {
    
    // Вызывается при выполнении action
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // Выполнить операцию
        Messages.showInfoMessage(
            project,
            "Action executed!",
            "My Action"
        )
    }
}
```

### Полная реализация

```kotlin
import com.intellij.openapi.actionSystem.*

class GenerateListingAction : AnAction() {
    
    // Основная логика action
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.PSI_FILE)
        
        // Выполнить генерацию
        generateListing(project, editor, file)
    }
    
    // Обновление состояния (enabled/visible)
    override fun update(e: AnActionEvent) {
        val project = e.project
        val presentation = e.presentation
        
        // Доступно только когда проект открыт
        presentation.isEnabledAndVisible = project != null
        
        // Обновить текст в зависимости от контекста
        val hasSelection = e.getData(CommonDataKeys.EDITOR)
            ?.selectionModel
            ?.hasSelection() == true
        
        if (hasSelection) {
            presentation.text = "Generate from Selection"
        } else {
            presentation.text = "Generate from File"
        }
    }
    
    // На каком потоке вызывать update() (2022.3+)
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT // Рекомендуется
    }
}
```

## Ключевые методы AnAction

### 1. actionPerformed() (обязательный)

**Назначение:** выполнение основной логики  
**Thread:** EDT  
**Требования:** можно выполнять долгие операции (в background thread)

```kotlin
override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    
    // Долгая операция в background
    object : Task.Backgroundable(
        project,
        "Generating listing...",
        true
    ) {
        override fun run(indicator: ProgressIndicator) {
            val result = generateListing(indicator)
            
            ApplicationManager.getApplication().invokeLater {
                showResults(result)
            }
        }
    }.queue()
}
```

### 2. update() (рекомендуется)

**Назначение:** обновление состояния action (enabled/visible/text)  
**Thread:** BGT или EDT (зависит от `getActionUpdateThread()`)  
**Требования:** должен быть **очень быстрым** (< 1ms желательно)

```kotlin
override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    val project = e.project
    
    // Быстрые проверки
    presentation.isEnabledAndVisible = project != null && hasLgConfig(project)
    
    // ❌ НЕЛЬЗЯ: долгие операции
    // val files = scanAllFiles() // Медленно!
    
    // ❌ НЕЛЬЗЯ: доступ к файловой системе
    // val exists = File("/path").exists()
    
    // ✅ МОЖНО: проверка selection, открытых файлов
    val hasSelection = e.getData(CommonDataKeys.EDITOR)
        ?.selectionModel
        ?.hasSelection() == true
}
```

### 3. getActionUpdateThread() (обязательный с 2022.3)

**Назначение:** указывает на каком потоке вызывать `update()`

```kotlin
override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT // Рекомендуется
}
```

**Варианты:**
- `BGT` (Background Thread) — **рекомендуется**
  - Гарантирует read access к PSI/VFS
  - Не блокирует EDT
  - **Нельзя** трогать Swing components напрямую
  
- `EDT` (Event Dispatch Thread)
  - Доступ к Swing components
  - **Нельзя** читать PSI/VFS напрямую (без read action)
  - Может блокировать UI

**Best practice:** используйте `BGT` и получайте данные через `DataContext`.

## AnActionEvent и DataContext

`AnActionEvent` предоставляет контекст выполнения action.

### Основные методы AnActionEvent

```kotlin
override fun actionPerformed(e: AnActionEvent) {
    // Project
    val project: Project? = e.project
    
    // Presentation (для update)
    val presentation: Presentation = e.presentation
    
    // Place (где вызван action)
    val place: String = e.place // "MainMenu", "EditorPopup", etc.
    
    // Input event (keyboard/mouse)
    val inputEvent: InputEvent? = e.inputEvent
    
    // Data context
    val editor = e.getData(CommonDataKeys.EDITOR)
    val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
    val psiFile = e.getData(CommonDataKeys.PSI_FILE)
}
```

### CommonDataKeys

Стандартные ключи для получения данных:

```kotlin
import com.intellij.openapi.actionSystem.CommonDataKeys

// Project
val project = e.getData(CommonDataKeys.PROJECT)

// Editor
val editor = e.getData(CommonDataKeys.EDITOR)
val caret = e.getData(CommonDataKeys.CARET)

// Files
val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
val virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
val psiFile = e.getData(CommonDataKeys.PSI_FILE)

// Selection
val psiElement = e.getData(CommonDataKeys.PSI_ELEMENT)
val selectedText = editor?.selectionModel?.selectedText

// Navigation
val navigatable = e.getData(CommonDataKeys.NAVIGATABLE)
```

### PlatformDataKeys

Дополнительные ключи для UI:

```kotlin
import com.intellij.openapi.actionSystem.PlatformDataKeys

// Tool Window
val toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW)

// Content (вкладка tool window)
val content = e.getData(PlatformDataKeys.CONTENT_MANAGER)

// Last focused component
val component = e.getData(PlatformDataKeys.CONTEXT_COMPONENT)
```

## Регистрация Actions

### В plugin.xml

```xml
<actions>
    <action 
        id="com.example.lg.GenerateListing"
        class="com.example.lg.actions.GenerateListingAction"
        text="Generate Listing"
        description="Generate code listing from current file"
        icon="icons.LgIcons.Generate">
        
        <!-- В Tools меню -->
        <add-to-group 
            group-id="ToolsMenu" 
            anchor="last"/>
        
        <!-- В Editor context menu -->
        <add-to-group 
            group-id="EditorPopupMenu" 
            anchor="first"/>
        
        <!-- Keyboard shortcut -->
        <keyboard-shortcut 
            keymap="$default" 
            first-keystroke="control alt G"/>
    </action>
</actions>
```

### Programmatically (из кода)

```kotlin
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup

// Зарегистрировать action
val actionManager = ActionManager.getInstance()
val action = MyAction()
actionManager.registerAction("com.example.MyAction", action)

// Добавить в группу
val toolsMenu = actionManager.getAction("ToolsMenu") as DefaultActionGroup
toolsMenu.add(action)
```

**Важно:** programmatic registration требуется редко. Используйте `plugin.xml` когда возможно.

## Action Groups

Action Group — контейнер для actions, формирующий меню или submenu.

### Simple Group

```xml
<group 
    id="com.example.lg.MainGroup"
    text="Listing Generator"
    popup="true"
    icon="icons.LgIcons.Group">
    
    <action id="com.example.lg.Generate" class="..."/>
    <action id="com.example.lg.ShowStats" class="..."/>
    <separator/>
    <action id="com.example.lg.Settings" class="..."/>
    
    <add-to-group 
        group-id="ToolsMenu" 
        anchor="last"/>
</group>
```

Атрибуты:
- `popup="true"` — показать как **submenu** (раскрывающееся меню)
- `popup="false"` — показать **inline** с separators
- `compact="true"` — скрыть disabled actions
- `searchable="false"` — не показывать в Find Action

### Dynamic Action Group

Group с actions, добавляемыми в runtime:

```kotlin
import com.intellij.openapi.actionSystem.DefaultActionGroup

class RecentFilesGroup : DefaultActionGroup() {
    
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val project = e?.project ?: return emptyArray()
        
        // Динамически создать actions
        val recentFiles = getRecentFiles(project)
        
        return recentFiles.map { file ->
            object : AnAction(file.name, "Open ${file.name}", file.fileType.icon) {
                override fun actionPerformed(e: AnActionEvent) {
                    FileEditorManager.getInstance(project).openFile(file, true)
                }
                
                override fun getActionUpdateThread() = ActionUpdateThread.BGT
            }
        }.toTypedArray()
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
    
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
```

Регистрация:
```xml
<group 
    id="com.example.RecentFiles"
    class="com.example.RecentFilesGroup"
    text="Recent Files"
    popup="true">
    <add-to-group group-id="FileMenu"/>
</group>
```

## Toggle Actions (переключатели)

Для checkbox-style actions используйте `ToggleAction`:

```kotlin
import com.intellij.openapi.actionSystem.ToggleAction

class ToggleViewModeAction : ToggleAction(
    "Tree View",
    "Toggle between tree and flat view",
    AllIcons.Actions.ListFiles
) {
    
    override fun isSelected(e: AnActionEvent): Boolean {
        val project = e.project ?: return false
        val state = project.service<ViewState>()
        return state.isTreeView
    }
    
    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val project = e.project ?: return
        val viewState = project.service<ViewState>()
        viewState.isTreeView = state
        
        // Обновить UI
        updateView(project, state)
    }
    
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
```

В UI:
- Меню: ✓ галочка когда selected
- Toolbar: pressed состояние кнопки

### DumbAwareToggleAction

Для toggle actions доступных при индексации:

```kotlin
import com.intellij.openapi.project.DumbAwareToggleAction

class MyToggleAction : DumbAwareToggleAction() {
    // Доступно даже при индексации
}
```

## Места размещения Actions

### Стандартные группы (ActionPlaces)

```kotlin
import com.intellij.openapi.actionSystem.ActionPlaces

// Main menu
ActionPlaces.MAIN_MENU

// Editor popup (right-click в редакторе)
ActionPlaces.EDITOR_POPUP

// Main toolbar
ActionPlaces.MAIN_TOOLBAR

// Navigation bar
ActionPlaces.NAVIGATION_BAR_TOOLBAR

// Project view popup
ActionPlaces.PROJECT_VIEW_POPUP

// Tool window toolbar
ActionPlaces.TOOLWINDOW_TITLE
```

### Anchor (позиция в группе)

```xml
<add-to-group 
    group-id="ToolsMenu" 
    anchor="first"/>      <!-- Первым -->

<add-to-group 
    group-id="ToolsMenu" 
    anchor="last"/>       <!-- Последним -->

<add-to-group 
    group-id="ToolsMenu" 
    anchor="before"
    relative-to-action="GenerateJavadoc"/>  <!-- Перед -->

<add-to-group 
    group-id="ToolsMenu" 
    anchor="after"
    relative-to-action="GenerateJavadoc"/>  <!-- После -->
```

### Стандартные группы меню

```xml
<!-- File menu -->
<add-to-group group-id="FileMenu"/>

<!-- Edit menu -->
<add-to-group group-id="EditMenu"/>

<!-- View menu -->
<add-to-group group-id="ViewMenu"/>

<!-- Navigate menu -->
<add-to-group group-id="GoToMenu"/>

<!-- Code menu -->
<add-to-group group-id="CodeMenu"/>

<!-- Refactor menu -->
<add-to-group group-id="RefactoringMenu"/>

<!-- Build menu -->
<add-to-group group-id="BuildMenu"/>

<!-- Run menu -->
<add-to-group group-id="RunMenu"/>

<!-- Tools menu -->
<add-to-group group-id="ToolsMenu"/>

<!-- VCS menu -->
<add-to-group group-id="VcsGroups"/>

<!-- Window menu -->
<add-to-group group-id="WindowMenu"/>

<!-- Help menu -->
<add-to-group group-id="HelpMenu"/>
```

Полный список: [`PlatformActions.xml`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-resources/src/idea/PlatformActions.xml)

## Keyboard Shortcuts

### Базовый shortcut

```xml
<keyboard-shortcut 
    keymap="$default"
    first-keystroke="control alt G"/>
```

**Keymaps:**
- `$default` — все keymaps
- `Mac OS X` — только macOS
- `Windows` — только Windows
- `Default for XWin` — только Linux

### Chord shortcuts (два нажатия)

```xml
<keyboard-shortcut 
    keymap="$default"
    first-keystroke="control alt G"
    second-keystroke="C"/>
```

Пользователь нажимает: **Ctrl+Alt+G**, затем **C**

### Modifiers

- `control` — Ctrl (Windows/Linux) или Cmd (macOS)
- `alt` — Alt (Windows/Linux) или Option (macOS)
- `shift` — Shift
- `meta` — Cmd (macOS) или Win (Windows)

```xml
<!-- Ctrl+Shift+A -->
<keyboard-shortcut 
    keymap="$default"
    first-keystroke="control shift A"/>

<!-- Alt+Enter -->
<keyboard-shortcut 
    keymap="$default"
    first-keystroke="alt ENTER"/>

<!-- Shift+F10 -->
<keyboard-shortcut 
    keymap="$default"
    first-keystroke="shift F10"/>
```

### Platform-specific shortcuts

```xml
<!-- Ctrl+G на Windows/Linux, Cmd+G на Mac -->
<keyboard-shortcut 
    keymap="$default"
    first-keystroke="control G"/>

<!-- Разные shortcuts для разных платформ -->
<keyboard-shortcut 
    keymap="Mac OS X"
    first-keystroke="meta G"/>
    
<keyboard-shortcut 
    keymap="Windows"
    first-keystroke="control G"/>
```

### Remove/Replace shortcuts

```xml
<!-- Удалить shortcut -->
<keyboard-shortcut 
    keymap="Mac OS X"
    first-keystroke="control G"
    remove="true"/>

<!-- Заменить все shortcuts -->
<keyboard-shortcut 
    keymap="Mac OS X"
    first-keystroke="meta G"
    replace-all="true"/>
```

### Mouse Shortcuts

```xml
<mouse-shortcut 
    keymap="$default"
    keystroke="control button3 doubleClick"/>
```

Buttons:
- `button1` — левая кнопка
- `button2` — средняя кнопка
- `button3` — правая кнопка
- `doubleClick` — двойной клик

## Presentation (представление action)

`Presentation` — объект с информацией об отображении action.

### Основные свойства

```kotlin
override fun update(e: AnActionEvent) {
    val p = e.presentation
    
    // Текст
    p.text = "Generate Listing"
    p.description = "Generate code listing from current file"
    
    // Enabled/Visible
    p.isEnabled = true
    p.isVisible = true
    p.isEnabledAndVisible = true // Оба сразу
    
    // Иконка
    p.icon = AllIcons.Actions.Execute
    
    // Template presentation (не менять в runtime)
    val template = templatePresentation
}
```

### Условное отображение

```kotlin
override fun update(e: AnActionEvent) {
    val project = e.project
    val editor = e.getData(CommonDataKeys.EDITOR)
    
    e.presentation.isEnabledAndVisible = 
        project != null && editor != null
}
```

### Динамический текст

```kotlin
override fun update(e: AnActionEvent) {
    val hasSelection = e.getData(CommonDataKeys.EDITOR)
        ?.selectionModel
        ?.hasSelection() == true
    
    e.presentation.text = if (hasSelection) {
        "Generate from Selection"
    } else {
        "Generate from File"
    }
}
```

### Override Text (контекстный текст)

В `plugin.xml`:

```xml
<action 
    id="com.example.MyAction"
    class="..."
    text="Long Action Name: Do Something">
    
    <!-- Короткий текст для меню -->
    <override-text 
        place="MainMenu" 
        text="Do Something"/>
    
    <!-- Для toolbar используем текст из MainMenu -->
    <override-text 
        place="MainToolbar" 
        use-text-of-place="MainMenu"/>
</action>
```

## Полезные базовые классы Actions

### DumbAwareAction

Action доступен во время индексации:

```kotlin
import com.intellij.openapi.project.DumbAwareAction

class MyDumbAwareAction : DumbAwareAction(
    "My Action",
    "Description",
    AllIcons.Actions.Execute
) {
    
    override fun actionPerformed(e: AnActionEvent) {
        // Работает даже при индексации
        // Но НЕ используйте indexes (Find Usages и т.д.)
    }
    
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
```

**Когда использовать:**
- Actions не требующие PSI indexes
- Simple UI operations
- File system operations (VFS)

### ToggleAction

См. [Toggle Actions](#toggle-actions-переключатели)

### EmptyAction (placeholder)

Для резервирования Action ID:

```xml
<!-- Резервируем ID для runtime action -->
<action 
    id="com.example.RuntimeAction"
    class="com.intellij.openapi.actionSystem.EmptyAction"/>
```

Затем в runtime:

```kotlin
val action = MyRuntimeAction()
ActionManager.getInstance()
    .replaceAction("com.example.RuntimeAction", action)
```

## Creating Toolbars

### Toolbar из Action Group

```kotlin
import com.intellij.openapi.actionSystem.*

fun createToolbar(targetComponent: JComponent): JComponent {
    val actionGroup = DefaultActionGroup().apply {
        add(GenerateAction())
        add(RefreshAction())
        addSeparator()
        add(SettingsAction())
    }
    
    val toolbar = ActionManager.getInstance()
        .createActionToolbar(
            "LgToolbar",      // Place ID (для logging)
            actionGroup,       // Actions
            true               // horizontal (false = vertical)
        )
    
    // ВАЖНО: установить target component
    toolbar.targetComponent = targetComponent
    
    return toolbar.component
}
```

**Почему targetComponent важен:**
- Actions получают `DataContext` из target component
- Без него context будет от текущего фокуса в IDE

### Toolbar в Tool Window

```kotlin
class MyToolWindowPanel : SimpleToolWindowPanel(true, true) {
    
    init {
        toolbar = createToolbar()
        setContent(createContent())
    }
    
    private fun createToolbar(): JComponent {
        val group = DefaultActionGroup().apply {
            add(RefreshAction(this@MyToolWindowPanel))
            add(ClearAction())
        }
        
        val toolbar = ActionManager.getInstance()
            .createActionToolbar("MyToolWindow", group, true)
        
        toolbar.targetComponent = this // this = SimpleToolWindowPanel
        
        return toolbar.component
    }
}
```

## Popup Menus

### Context Menu в компоненте

```kotlin
import com.intellij.openapi.actionSystem.ActionPopupMenu

val tree = Tree(model)

tree.addMouseListener(object : MouseAdapter() {
    override fun mousePressed(e: MouseEvent) {
        if (e.isPopupTrigger) showPopup(e)
    }
    
    override fun mouseReleased(e: MouseEvent) {
        if (e.isPopupTrigger) showPopup(e)
    }
    
    private fun showPopup(e: MouseEvent) {
        // Выбрать элемент под курсором
        val path = tree.getPathForLocation(e.x, e.y)
        tree.selectionPath = path
        
        val actionGroup = DefaultActionGroup().apply {
            add(OpenFileAction())
            add(CopyPathAction())
            addSeparator()
            add(RefreshAction())
        }
        
        val popupMenu = ActionManager.getInstance()
            .createActionPopupMenu("TreePopup", actionGroup)
        
        popupMenu.component.show(e.component, e.x, e.y)
    }
})
```

### Popup Menu через Action

```kotlin
import com.intellij.openapi.ui.popup.JBPopupFactory

class ShowMenuAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val actionGroup = DefaultActionGroup().apply {
            add(Action1())
            add(Action2())
            add(Action3())
        }
        
        val popup = JBPopupFactory.getInstance()
            .createActionGroupPopup(
                "Choose Action",                    // Title
                actionGroup,                         // Actions
                e.dataContext,                       // Context
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, // Search
                true                                 // Show numbers (1, 2, 3)
            )
        
        popup.showInBestPositionFor(e.dataContext)
    }
    
    override fun getActionUpdateThread() = ActionUpdateThread.EDT
}
```

## Separators

### В группах

```xml
<group id="com.example.MyGroup" popup="true">
    <action id="action1" class="..."/>
    <action id="action2" class="..."/>
    
    <separator/>
    
    <action id="action3" class="..."/>
</group>
```

### С текстом

```xml
<separator text="Section 2"/>
```

### Programmatic separator

```kotlin
val group = DefaultActionGroup().apply {
    add(Action1())
    add(Action2())
    addSeparator()
    add(Action3())
    addSeparator("Advanced")
    add(Action4())
}
```

## Action State (состояние)

### Иконка в зависимости от состояния

```kotlin
override fun update(e: AnActionEvent) {
    val isRunning = isTaskRunning()
    
    e.presentation.icon = if (isRunning) {
        AllIcons.Process.Step_1  // Running
    } else {
        AllIcons.Actions.Execute  // Idle
    }
    
    e.presentation.text = if (isRunning) {
        "Stop Task"
    } else {
        "Start Task"
    }
}
```

### Disabled state с причиной

```kotlin
override fun update(e: AnActionEvent) {
    val project = e.project
    
    when {
        project == null -> {
            e.presentation.isEnabledAndVisible = false
        }
        !hasLgConfig(project) -> {
            e.presentation.isEnabled = false
            e.presentation.text = "Generate Listing (lg-cfg not found)"
        }
        else -> {
            e.presentation.isEnabled = true
        }
    }
}
```

## Action Context (передача данных)

### DataProvider для custom data

Если ваш component предоставляет данные для actions:

```kotlin
import com.intellij.openapi.actionSystem.DataProvider

class MyPanel : JPanel(), DataProvider {
    
    private var currentFile: VirtualFile? = null
    
    override fun getData(dataId: String): Any? {
        return when {
            CommonDataKeys.VIRTUAL_FILE.`is`(dataId) -> currentFile
            CommonDataKeys.PROJECT.`is`(dataId) -> project
            else -> null
        }
    }
}
```

Теперь actions в toolbar этого panel получат данные:

```kotlin
class MyAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        // file будет взят из MyPanel.getData()
    }
}
```

### Custom DataKeys

```kotlin
import com.intellij.openapi.actionSystem.DataKey

object LgDataKeys {
    val SELECTED_SECTION = DataKey.create<String>("lg.selectedSection")
    val CURRENT_CONTEXT = DataKey.create<LgContext>("lg.currentContext")
}

// В DataProvider
override fun getData(dataId: String): Any? {
    return when {
        LgDataKeys.SELECTED_SECTION.`is`(dataId) -> 
            getSelectedSection()
        LgDataKeys.CURRENT_CONTEXT.`is`(dataId) -> 
            getCurrentContext()
        else -> null
    }
}

// В Action
override fun actionPerformed(e: AnActionEvent) {
    val section = e.getData(LgDataKeys.SELECTED_SECTION)
    val context = e.getData(LgDataKeys.CURRENT_CONTEXT)
}
```

## Action Localization (локализация)

### Через resource bundle

```xml
<resource-bundle>messages.LgBundle</resource-bundle>

<actions>
    <!-- Без text атрибута -->
    <action 
        id="com.example.lg.Generate"
        class="...">
        <add-to-group group-id="ToolsMenu"/>
    </action>
</actions>
```

```properties
# messages/LgBundle.properties (English)
action.com.example.lg.Generate.text=Generate Listing
action.com.example.lg.Generate.description=Generate code listing from current file

# messages/LgBundle_ru.properties (Russian)
action.com.example.lg.Generate.text=Сгенерировать листинг
action.com.example.lg.Generate.description=Генерация листинга из текущего файла
```

### Dedicated Actions Bundle

```xml
<actions resource-bundle="messages.ActionsBundle">
    <action id="..." class="..."/>
    <action id="..." class="..."/>
</actions>
```

## Advanced Patterns

### Action с Progress

```kotlin
class GenerateAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        object : Task.Backgroundable(
            project,
            "Generating...",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Loading config..."
                indicator.fraction = 0.1
                val config = loadConfig()
                
                indicator.text = "Processing files..."
                indicator.fraction = 0.5
                val files = processFiles(config, indicator)
                
                indicator.text = "Rendering..."
                indicator.fraction = 0.9
                val result = render(files)
                
                ApplicationManager.getApplication().invokeLater {
                    showResult(project, result)
                }
            }
        }.queue()
    }
    
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
```

### Action с модальным диалогом

```kotlin
class ConfigureAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        val dialog = ConfigurationDialog(project)
        if (dialog.showAndGet()) {
            val config = dialog.getConfiguration()
            applyConfiguration(project, config)
        }
    }
    
    override fun getActionUpdateThread() = ActionUpdateThread.EDT
}
```

### Action с уведомлением

```kotlin
class SaveAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        try {
            saveData(project)
            
            NotificationGroupManager.getInstance()
                .getNotificationGroup("LG Notifications")
                .createNotification(
                    "Data saved successfully",
                    NotificationType.INFORMATION
                )
                .notify(project)
                
        } catch (ex: Exception) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("LG Notifications")
                .createNotification(
                    "Failed to save data",
                    ex.message ?: "Unknown error",
                    NotificationType.ERROR
                )
                .notify(project)
        }
    }
    
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
```

## Testing Actions

### Базовый тест

```kotlin
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MyActionTest : BasePlatformTestCase() {
    
    fun testActionEnabled() {
        val action = MyAction()
        val event = testActionEvent()
        
        action.update(event)
        
        assertTrue(event.presentation.isEnabled)
        assertTrue(event.presentation.isVisible)
    }
    
    fun testActionPerformed() {
        val action = MyAction()
        val event = testActionEvent()
        
        action.actionPerformed(event)
        
        // Verify results
        assertNotNull(getResult())
    }
    
    private fun testActionEvent(): AnActionEvent {
        return AnActionEvent.createFromDataContext(
            ActionPlaces.UNKNOWN,
            null,
            createDataContext()
        )
    }
    
    private fun createDataContext(): DataContext {
        return SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.EDITOR, myFixture.editor)
            .build()
    }
}
```

## Best Practices

### 1. Нет полей в AnAction

```kotlin
// ❌ УТЕЧКА ПАМЯТИ
class MyAction : AnAction() {
    private val project: Project? = null
    private val cachedData: Data? = null
}

// ✅ ПРАВИЛЬНО
class MyAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project // Получить здесь
    }
}
```

### 2. update() должен быть быстрым

```kotlin
// ❌ ПЛОХО
override fun update(e: AnActionEvent) {
    val files = scanDirectory() // Долго!
    e.presentation.isEnabled = files.isNotEmpty()
}

// ✅ ХОРОШО
override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = project != null
}
```

### 3. Используйте ActionUpdateThread.BGT

```kotlin
// ✅ Рекомендуется
override fun getActionUpdateThread() = ActionUpdateThread.BGT

// ❌ Только если нужен Swing access
override fun getActionUpdateThread() = ActionUpdateThread.EDT
```

### 4. Проверяйте null

```kotlin
override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    
    // Теперь безопасно использовать
}
```

### 5. Используйте осмысленные ID

```kotlin
// ❌ Плохо
<action id="action1" .../>

// ✅ Хорошо
<action id="com.example.lg.actions.GenerateListingAction" .../>
```

## Action Shortcuts Cheat Sheet

| Shortcut | Действие |
|----------|----------|
| Ctrl+Shift+A | Find Action (поиск actions) |
| Ctrl+Shift+P | Find Action by ID |
| Settings → Keymap | Управление shortcuts |

### Поиск существующих Action IDs

**UI Inspector:**
1. Internal Mode
2. Tools → Internal Actions → UI → UI Inspector
3. Навести на меню/кнопку
4. Увидеть Action ID
