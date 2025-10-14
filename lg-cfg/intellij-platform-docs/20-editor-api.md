# Editor API (краткий обзор)

## Обзор

**Editor API** предоставляет доступ к редактору кода в IDE.

**Для LG plugin:** минимальное использование — открытие generated файлов с syntax highlighting.

## FileEditorManager

Главный API для работы с редактором.

### Открытие файла

```kotlin
import com.intellij.openapi.fileEditor.FileEditorManager

// Открыть файл
val editorManager = FileEditorManager.getInstance(project)
editorManager.openFile(virtualFile, true) // true = focus

// Открыть с позицией
OpenFileDescriptor(project, virtualFile, line, column)
    .navigate(true)

// Открыть в новом окне
editorManager.openFile(virtualFile, true, true)
```

### Получение текущего Editor

```kotlin
// Из Action
val editor = e.getData(CommonDataKeys.EDITOR)

// Текущий активный editor
val editor = FileEditorManager.getInstance(project)
    .selectedTextEditor

// Все открытые editors для файла
val editors = FileEditorManager.getInstance(project)
    .getAllEditors(virtualFile)
```

## Editor

```kotlin
import com.intellij.openapi.editor.Editor

val editor: Editor = ...

// Document
val document = editor.document

// Caret (курсор)
val caretModel = editor.caretModel
val offset = caretModel.offset
val line = caretModel.logicalPosition.line
val column = caretModel.logicalPosition.column

// Selection
val selectionModel = editor.selectionModel
val selectedText = selectionModel.selectedText
val hasSelection = selectionModel.hasSelection()

// Scrolling
val scrollingModel = editor.scrollingModel
scrollingModel.scrollToCaret(ScrollType.CENTER)

// Settings
val settings = editor.settings
settings.isLineNumbersShown = true
```

## Document

```kotlin
import com.intellij.openapi.editor.Document

val document: Document = editor.document

// Текст
val text = document.text
val lineCount = document.lineCount

// Modification (в write action!)
writeAction {
    document.setText("New content")
    document.insertString(0, "// Header\n")
    document.deleteString(0, 10)
    document.replaceString(0, 5, "New")
}

// Line operations
val lineStart = document.getLineStartOffset(lineNumber)
val lineEnd = document.getLineEndOffset(lineNumber)
val lineText = document.text.substring(lineStart, lineEnd)
```

## LightVirtualFile (для preview)

Для отображения generated контента:

```kotlin
import com.intellij.testFramework.LightVirtualFile

fun showPreview(project: Project, content: String, filename: String) {
    // Создать virtual file в памяти
    val virtualFile = LightVirtualFile(filename, content)
    
    // Определить file type для syntax highlighting
    val fileType = FileTypeManager.getInstance()
        .getFileTypeByFileName(filename)
    
    virtualFile.fileType = fileType
    virtualFile.isWritable = false // Read-only
    
    // Открыть в редакторе
    FileEditorManager.getInstance(project)
        .openFile(virtualFile, true)
}

// Использование
showPreview(project, generatedContext, "context-default.md")
```

## Syntax Highlighting

Для Markdown/YAML preview с highlighting:

```kotlin
fun createPreviewFile(content: String, language: String): VirtualFile {
    val fileType = when (language) {
        "markdown" -> FileTypeManager.getInstance()
            .getFileTypeByExtension("md")
        "yaml" -> FileTypeManager.getInstance()
            .getFileTypeByExtension("yaml")
        "json" -> FileTypeManager.getInstance()
            .getStdFileType("JSON")
        else -> PlainTextFileType.INSTANCE
    }
    
    return LightVirtualFile("preview.$language", fileType, content).apply {
        isWritable = false
    }
}
```

## EditorTextField (для UI)

Встраиваемый редактор в UI:

```kotlin
import com.intellij.ui.EditorTextField

val editorField = EditorTextField(
    "initial content",
    project,
    FileTypeManager.getInstance().getFileTypeByExtension("py")
).apply {
    setOneLineMode(false) // Multi-line
    isViewer = true       // Read-only
}

// Использование в panel
panel {
    row {
        scrollCell(editorField)
            .align(Align.FILL)
    }.resizableRow()
}
```

## Для LG Plugin (minimal usage)

### Открытие результатов

```kotlin
@Service
class LgResultsService {
    
    fun showResult(
        project: Project,
        content: String,
        filename: String,
        editable: Boolean = false
    ) {
        val settings = LgSettingsService.getInstance()
        
        if (settings.state.openAsEditable) {
            showAsTempFile(project, content, filename)
        } else {
            showAsVirtual(project, content, filename, editable)
        }
    }
    
    private fun showAsVirtual(
        project: Project,
        content: String,
        filename: String,
        editable: Boolean
    ) {
        val fileType = FileTypeManager.getInstance()
            .getFileTypeByFileName(filename)
        
        val virtualFile = LightVirtualFile(filename, fileType, content).apply {
            isWritable = editable
        }
        
        FileEditorManager.getInstance(project)
            .openFile(virtualFile, true)
    }
    
    private fun showAsTempFile(
        project: Project,
        content: String,
        filename: String
    ) {
        val tempDir = Files.createTempDirectory("lg").toFile()
        val tempFile = File(tempDir, filename)
        tempFile.writeText(content)
        
        val virtualFile = LocalFileSystem.getInstance()
            .refreshAndFindFileByIoFile(tempFile)!!
        
        FileEditorManager.getInstance(project)
            .openFile(virtualFile, true)
    }
}
```

## Best Practices

### 1. LightVirtualFile для read-only preview

```kotlin
// ✅ Для preview (не сохраняется на диск)
val preview = LightVirtualFile("preview.md", content)
preview.isWritable = false

// ❌ Temporary file на диске (если не нужен)
val temp = File.createTempFile("preview", ".md")
```

### 2. Правильный FileType для highlighting

```kotlin
// ✅ По расширению
val fileType = FileTypeManager.getInstance()
    .getFileTypeByFileName("file.md")

// ✅ По content (если нет имени)
val fileType = FileTypeManager.getInstance()
    .getFileTypeByContent(content)
```

### 3. Read-only для generated content

```kotlin
// ✅ Предотвращает случайное редактирование
virtualFile.isWritable = false
```
