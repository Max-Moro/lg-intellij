# Dialogs в IntelliJ Platform

## DialogWrapper

[`DialogWrapper`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/openapi/ui/DialogWrapper.java) — базовый класс для всех диалогов в платформе.

### Минимальный Dialog

```kotlin
import com.intellij.openapi.ui.DialogWrapper
import javax.swing.JComponent

class MyDialog(
    private val project: Project?
) : DialogWrapper(project) {
    
    init {
        title = "My Dialog"
        init() // Обязательно вызвать!
    }
    
    override fun createCenterPanel(): JComponent {
        return JLabel("Dialog content")
    }
}

// Использование
val dialog = MyDialog(project)
if (dialog.showAndGet()) {
    // User clicked OK
} else {
    // User clicked Cancel
}
```

### Dialog с Kotlin UI DSL

```kotlin
class InputDialog(
    private val project: Project?
) : DialogWrapper(project) {
    
    private var name: String = ""
    private var email: String = ""
    
    init {
        title = "Enter Information"
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        return panel {
            row("Name:") {
                textField()
                    .bindText(::name)
                    .focused() // Auto-focus
                    .validationOnInput {
                        if (it.text.isBlank()) {
                            error("Name cannot be empty")
                        } else null
                    }
            }
            
            row("Email:") {
                textField()
                    .bindText(::email)
                    .columns(30)
                    .validationOnApply {
                        if (!it.text.matches(Regex(".+@.+\\..+"))) {
                            error("Invalid email format")
                        } else null
                    }
            }
        }
    }
    
    fun getData(): UserData {
        return UserData(name, email)
    }
}

// Использование
val dialog = InputDialog(project)
if (dialog.showAndGet()) {
    val data = dialog.getData()
    processData(data)
}
```

## Dialog Buttons

### Стандартные кнопки

```kotlin
init {
    title = "Confirmation"
    
    // Кнопки по умолчанию: OK, Cancel
    init()
}

// Или кастомные:
override fun createActions(): Array<Action> {
    return arrayOf(
        okAction,
        cancelAction,
        customAction
    )
}

private val customAction = object : DialogWrapperAction("Custom") {
    override fun doAction(e: ActionEvent) {
        // Custom action
        close(CUSTOM_EXIT_CODE)
    }
}

companion object {
    const val CUSTOM_EXIT_CODE = 100
}
```

### Изменение текста кнопок

```kotlin
init {
    setOKButtonText("Generate")
    setCancelButtonText("Close")
    
    init()
}
```

### Disabled OK button

```kotlin
class MyDialog : DialogWrapper(project) {
    
    private val nameField = JBTextField()
    
    init {
        title = "Input"
        init()
        
        // Validate для OK button
        nameField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                updateOKButton()
            }
        })
        
        updateOKButton()
    }
    
    private fun updateOKButton() {
        isOKActionEnabled = nameField.text.isNotBlank()
    }
    
    override fun createCenterPanel() = panel {
        row("Name:") {
            cell(nameField)
                .align(AlignX.FILL)
        }
    }
}
```

## Validation

### doValidate()

```kotlin
override fun doValidate(): ValidationInfo? {
    if (nameField.text.isBlank()) {
        return ValidationInfo("Name cannot be empty", nameField)
    }
    
    if (portField.text.toIntOrNull() !in 1..65535) {
        return ValidationInfo("Invalid port", portField)
    }
    
    return null // OK
}
```

**Вызывается:** при нажатии OK перед `doOKAction()`.

### Input Validation (live)

```kotlin
override fun createCenterPanel() = panel {
    row("Port:") {
        intTextField(range = 1..65535)
            .validationOnInput {
                val value = it.text.toIntOrNull()
                when {
                    value == null -> error("Must be a number")
                    value !in 1..65535 -> error("Port must be 1-65535")
                    else -> null
                }
            }
    }
}
```

## Dialog Size и Position

### Preferred Size

```kotlin
override fun createCenterPanel(): JComponent {
    return panel {
        // ...
    }.apply {
        preferredSize = JBUI.size(600, 400)
    }
}
```

### Resizable

```kotlin
init {
    title = "Large Dialog"
    isResizable = true // Разрешить resize (default: true)
    init()
}
```

### Initial Position

```kotlin
// Center на screen
dialog.show()

// Относительно component
dialog.showInCenterOf(parentComponent)
```

## Progress в Dialog

### DialogWrapperAction с progress

```kotlin
private val processAction = object : DialogWrapperAction("Process") {
    override fun doAction(e: ActionEvent) {
        // Disable button
        isEnabled = false
        
        object : Task.Backgroundable(
            myProject,
            "Processing...",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                processData(indicator)
                
                ApplicationManager.getApplication().invokeLater {
                    close(OK_EXIT_CODE)
                }
            }
            
            override fun onCancel() {
                isEnabled = true
            }
        }.queue()
    }
}
```

## Messages Dialogs (simple)

Для простых диалогов используйте `Messages`:

```kotlin
import com.intellij.openapi.ui.Messages

// Information
Messages.showInfoMessage(
    project,
    "Operation completed successfully",
    "Success"
)

// Warning
Messages.showWarningDialog(
    project,
    "This action cannot be undone",
    "Warning"
)

// Error
Messages.showErrorDialog(
    project,
    "Failed to process file: ${error.message}",
    "Error"
)

// Yes/No
val result = Messages.showYesNoDialog(
    project,
    "Delete file?",
    "Confirm Deletion",
    Messages.getQuestionIcon()
)

if (result == Messages.YES) {
    deleteFile()
}

// Yes/No/Cancel
val result = Messages.showYesNoCancelDialog(
    project,
    "Save changes?",
    "Confirm",
    "Save",
    "Don't Save",
    "Cancel",
    Messages.getQuestionIcon()
)

when (result) {
    Messages.YES -> saveAndClose()
    Messages.NO -> closeWithoutSaving()
    Messages.CANCEL -> return
}
```

## Input Dialogs

```kotlin
// Simple input
val name = Messages.showInputDialog(
    project,
    "Enter name:",
    "Input",
    Messages.getQuestionIcon()
)

if (name != null) {
    processName(name)
}

// С валидацией
val input = Messages.showInputDialog(
    project,
    "Enter port (1-65535):",
    "Port",
    Messages.getQuestionIcon(),
    "8080", // initial value
    object : InputValidator {
        override fun checkInput(inputString: String): Boolean {
            return inputString.toIntOrNull() in 1..65535
        }
        
        override fun canClose(inputString: String): Boolean {
            return checkInput(inputString)
        }
    }
)
```

## Multi-step Wizards

```kotlin
class MyWizard(private val project: Project) : DialogWrapper(project) {
    
    private var currentStep = 0
    private val steps = listOf(
        Step1Panel(),
        Step2Panel(),
        Step3Panel()
    )
    
    init {
        title = "Setup Wizard"
        init()
        updateButtons()
    }
    
    override fun createCenterPanel(): JComponent {
        return steps[currentStep]
    }
    
    override fun createActions(): Array<Action> {
        return arrayOf(
            backAction,
            nextAction,
            cancelAction
        )
    }
    
    private val backAction = object : DialogWrapperAction("< Back") {
        override fun doAction(e: ActionEvent) {
            if (currentStep > 0) {
                currentStep--
                updateContent()
                updateButtons()
            }
        }
    }
    
    private val nextAction = object : DialogWrapperAction("Next >") {
        override fun doAction(e: ActionEvent) {
            if (currentStep < steps.size - 1) {
                currentStep++
                updateContent()
                updateButtons()
            } else {
                // Last step — finish
                close(OK_EXIT_CODE)
            }
        }
    }
    
    private fun updateContent() {
        contentPane.removeAll()
        contentPane.add(steps[currentStep])
        contentPane.revalidate()
        contentPane.repaint()
    }
    
    private fun updateButtons() {
        backAction.isEnabled = currentStep > 0
        nextAction.putValue(Action.NAME, 
            if (currentStep == steps.size - 1) "Finish" else "Next >"
        )
    }
}

class Step1Panel : JPanel() {
    init {
        add(JLabel("Step 1: Introduction"))
    }
}
```

## Result Dialog (показать результаты)

```kotlin
class ResultsDialog(
    private val project: Project,
    private val result: GenerationResult
) : DialogWrapper(project) {
    
    init {
        title = "Generation Results"
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        return panel {
            group("Statistics") {
                row("Files processed:") {
                    label(result.filesCount.toString())
                }
                row("Tokens:") {
                    label(result.tokensCount.toString())
                }
                row("Size:") {
                    label(formatSize(result.sizeBytes))
                }
            }
            
            row {
                val textArea = JTextArea(result.content, 20, 80).apply {
                    isEditable = false
                    font = JBFont.create(Font.MONOSPACED, 12)
                }
                
                scrollCell(textArea)
                    .align(Align.FILL)
            }.resizableRow()
        }
    }
    
    override fun createActions(): Array<Action> {
        return arrayOf(
            copyAction,
            okAction
        )
    }
    
    private val copyAction = object : DialogWrapperAction("Copy to Clipboard") {
        override fun doAction(e: ActionEvent) {
            CopyPasteManager.getInstance()
                .setContents(StringSelection(result.content))
            
            Messages.showInfoMessage(
                "Copied to clipboard",
                "Success"
            )
        }
    }
}
```

## Error Dialog

```kotlin
fun showErrorDialog(project: Project, error: Exception) {
    object : DialogWrapper(project) {
        init {
            title = "Error"
            init()
        }
        
        override fun createCenterPanel(): JComponent {
            return panel {
                row {
                    label("An error occurred:")
                        .bold()
                }
                
                row {
                    val errorText = buildString {
                        appendLine(error.message)
                        if (error is CliException) {
                            appendLine()
                            appendLine("CLI Output:")
                            appendLine(error.stderr)
                        }
                    }
                    
                    val textArea = JTextArea(errorText, 10, 60).apply {
                        isEditable = false
                        lineWrap = true
                        wrapStyleWord = true
                    }
                    
                    scrollCell(textArea)
                        .align(Align.FILL)
                }.resizableRow()
            }
        }
        
        override fun createActions() = arrayOf(
            okAction,
            copyErrorAction
        )
        
        private val copyErrorAction = object : DialogWrapperAction("Copy Error") {
            override fun doAction(e: ActionEvent) {
                val fullError = buildString {
                    appendLine(error.message)
                    appendLine()
                    appendLine(error.stackTraceToString())
                }
                
                CopyPasteManager.getInstance()
                    .setContents(StringSelection(fullError))
            }
        }
    }.show()
}
```

## List Selection Dialog

```kotlin
fun selectSection(project: Project, sections: List<String>): String? {
    val dialog = object : DialogWrapper(project) {
        
        private val list = JBList(sections).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            selectedIndex = 0
        }
        
        init {
            title = "Select Section"
            init()
        }
        
        override fun createCenterPanel() = JBScrollPane(list)
        
        fun getSelectedSection(): String? {
            return list.selectedValue
        }
    }
    
    return if (dialog.showAndGet()) {
        dialog.getSelectedSection()
    } else {
        null
    }
}

// Использование
val section = selectSection(project, listOf("all", "core", "tests"))
if (section != null) {
    generateListing(section)
}
```

## Async Loading в Dialog

```kotlin
class AsyncDialog(private val project: Project) : DialogWrapper(project) {
    
    private val dataPanel = JPanel(BorderLayout())
    private val scope = CoroutineScope(SupervisorJob())
    
    init {
        title = "Loading Data"
        init()
        loadDataAsync()
    }
    
    override fun createCenterPanel(): JComponent {
        // Сначала loading state
        dataPanel.add(
            JLabel("Loading...", AllIcons.Process.Step_1, SwingConstants.CENTER),
            BorderLayout.CENTER
        )
        return dataPanel
    }
    
    private fun loadDataAsync() {
        scope.launch {
            val data = withContext(Dispatchers.IO) {
                loadData()
            }
            
            withContext(Dispatchers.EDT) {
                displayData(data)
            }
        }
    }
    
    private fun displayData(data: List<String>) {
        dataPanel.removeAll()
        dataPanel.add(JBList(data), BorderLayout.CENTER)
        dataPanel.revalidate()
        dataPanel.repaint()
        
        // Enable OK после загрузки
        isOKActionEnabled = true
    }
    
    override fun dispose() {
        scope.cancel()
        super.dispose()
    }
}
```

## Help Button

```kotlin
init {
    title = "Configuration"
    setHelpId("lg.configuration.help") // Help topic ID
    init()
}

// Или custom help action
override fun createActions(): Array<Action> {
    return arrayOf(
        okAction,
        cancelAction,
        helpAction
    )
}

override fun doHelpAction() {
    BrowserUtil.browse("https://example.com/docs/configuration")
}
```

## Preferred Focus

```kotlin
class MyDialog(project: Project?) : DialogWrapper(project) {
    
    private val nameField = JBTextField()
    
    override fun createCenterPanel() = panel {
        row("Name:") {
            cell(nameField)
        }
    }
    
    override fun getPreferredFocusedComponent() = nameField
}
```

## Modal vs Non-Modal

```kotlin
// Modal (default) — блокирует parent window
val dialog = MyDialog(project)
dialog.show()

// Non-modal — не блокирует
val dialog = MyDialog(project)
dialog.isModal = false
dialog.show()
```

## DialogBuilder (упрощённый)

Для простых диалогов:

```kotlin
import com.intellij.openapi.ui.DialogBuilder

val builder = DialogBuilder(project)
builder.setTitle("Simple Dialog")

val panel = JPanel().apply {
    add(JLabel("Content here"))
}

builder.setCenterPanel(panel)
builder.addOkAction()
builder.addCancelAction()

val ok = builder.show()
if (ok == 0) {
    // OK clicked
}
```

## Best Practices

### 1. Всегда вызывайте init()

```kotlin
// ✅ Правильно
init {
    title = "My Dialog"
    init() // ← ОБЯЗАТЕЛЬНО
}

// ❌ Упадёт
init {
    title = "My Dialog"
    // Забыли init()!
}
```

### 2. Используйте Kotlin UI DSL

```kotlin
// ✅ Декларативно и чисто
override fun createCenterPanel() = panel {
    row("Name:") { textField() }
}

// ❌ Императивный boilerplate
override fun createCenterPanel(): JComponent {
    val panel = JPanel(GridBagLayout())
    val c = GridBagConstraints()
    // 20 lines of layout code...
}
```

### 3. Валидация через UI DSL

```kotlin
// ✅ Встроенная валидация
row("Email:") {
    textField()
        .validationOnApply {
            if (!it.text.matches(emailRegex)) {
                error("Invalid email")
            } else null
        }
}

// ❌ Ручная валидация в doValidate
override fun doValidate(): ValidationInfo? {
    if (!emailField.text.matches(emailRegex)) {
        return ValidationInfo("Invalid email", emailField)
    }
    return null
}
```

### 4. Dispose resources

```kotlin
class MyDialog : DialogWrapper(project) {
    
    private val scope = CoroutineScope(SupervisorJob())
    
    // ...
    
    override fun dispose() {
        scope.cancel()
        super.dispose()
    }
}
```

## Примеры для LG Plugin

### Select Template Dialog

```kotlin
class SelectTemplateDialog(
    private val project: Project,
    private val templates: List<String>
) : DialogWrapper(project) {
    
    private val list = JBList(templates).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        if (templates.isNotEmpty()) {
            selectedIndex = 0
        }
        
        // Double-click = OK
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    doOKAction()
                }
            }
        })
    }
    
    init {
        title = "Select Template"
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        return JBScrollPane(list).apply {
            preferredSize = JBUI.size(400, 300)
        }
    }
    
    fun getSelectedTemplate(): String? {
        return list.selectedValue
    }
}
```

### Stats Preview Dialog

```kotlin
class StatsDialog(
    private val project: Project,
    private val stats: ReportSchema
) : DialogWrapper(project) {
    
    init {
        title = "Statistics — ${stats.target}"
        isResizable = true
        init()
    }
    
    override fun createCenterPanel() = panel {
        
        group("Summary") {
            row("Files:") {
                label(stats.files.size.toString())
            }
            row("Total Tokens:") {
                label(formatNumber(stats.total.tokensProcessed))
            }
            row("Context Share:") {
                label("${stats.total.ctxShare}%")
            }
        }
        
        separator()
        
        row {
            val tableModel = createTableModel(stats.files)
            val table = JBTable(tableModel)
            
            scrollCell(table)
                .align(Align.FILL)
        }.resizableRow()
    }
    
    override fun createActions() = arrayOf(
        copyAction,
        okAction
    )
    
    private val copyAction = object : DialogWrapperAction("Copy Stats") {
        override fun doAction(e: ActionEvent) {
            val text = formatStatsAsText(stats)
            CopyPasteManager.getInstance()
                .setContents(StringSelection(text))
        }
    }
    
    private fun createTableModel(files: List<FileRow>): TableModel {
        val data = files.map { f ->
            arrayOf(f.path, f.sizeBytes, f.tokensProcessed)
        }.toTypedArray()
        
        val columns = arrayOf("Path", "Size", "Tokens")
        
        return DefaultTableModel(data, columns)
    }
}
```
