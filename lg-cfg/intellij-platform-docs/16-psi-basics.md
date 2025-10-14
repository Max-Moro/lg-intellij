# PSI — Program Structure Interface

## Обзор

**PSI (Program Structure Interface)** — набор API для парсинга файлов и работы со структурой кода.

PSI обеспечивает:
- ✅ Синтаксический разбор файлов
- ✅ Построение AST (Abstract Syntax Tree)
- ✅ Семантический анализ кода
- ✅ Навигацию по структуре
- ✅ Модификацию кода
- ✅ Индексацию для быстрого поиска

**Примеры использования PSI:**
- Code Completion
- Find Usages
- Go to Definition
- Refactorings
- Code Inspections
- Syntax Highlighting

## PsiFile

[`PsiFile`](https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/psi/PsiFile.java) — корневой элемент PSI дерева для файла.

### Получение PsiFile

```kotlin
import com.intellij.psi.*

// Из VirtualFile
val psiFile = PsiManager.getInstance(project)
    .findFile(virtualFile)

// Из Document
val psiFile = PsiDocumentManager.getInstance(project)
    .getPsiFile(document)

// Из Editor
val psiFile = PsiDocumentManager.getInstance(project)
    .getPsiFile(editor.document)

// Из AnActionEvent
val psiFile = e.getData(CommonDataKeys.PSI_FILE)
```

### Основные свойства

```kotlin
val psiFile: PsiFile = ...

// Базовая информация
val name = psiFile.name                      // "file.py"
val text = psiFile.text                      // Весь текст файла
val language = psiFile.language              // Language.findLanguageByID("Python")
val fileType = psiFile.fileType              // PythonFileType
val virtualFile = psiFile.virtualFile        // VirtualFile

// Project
val project = psiFile.project

// Validity
val isValid = psiFile.isValid
val isPhysical = psiFile.isPhysical         // Файл на диске (не in-memory)

// View Provider
val viewProvider = psiFile.viewProvider
```

### Language-specific PsiFile

```kotlin
// Java
if (psiFile is PsiJavaFile) {
    val packageName = psiFile.packageName
    val classes = psiFile.classes
    val imports = psiFile.importList
}

// Kotlin
if (psiFile is KtFile) {
    val packageDirective = psiFile.packageDirective
    val declarations = psiFile.declarations
}

// Python
if (psiFile is PyFile) {
    val topLevelClasses = psiFile.topLevelClasses
    val topLevelFunctions = psiFile.topLevelFunctions
}
```

## PsiElement

[`PsiElement`](https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/psi/PsiElement.java) — базовый класс для всех элементов PSI дерева.

### Навигация по дереву

```kotlin
val element: PsiElement = ...

// Родитель
val parent = element.parent

// Дети
val children = element.children           // Array<PsiElement>
val firstChild = element.firstChild
val lastChild = element.lastChild

// Siblings
val nextSibling = element.nextSibling
val prevSibling = element.prevSibling

// Текст
val text = element.text
val textRange = element.textRange         // Позиция в файле
val textOffset = element.textOffset       // Начало в файле
```

### Поиск элементов

```kotlin
// Найти элемент по offset
val element = psiFile.findElementAt(offset)

// Найти первый элемент типа
val method = PsiTreeUtil.findChildOfType(
    psiFile,
    PsiMethod::class.java
)

// Найти все элементы типа
val allMethods = PsiTreeUtil.findChildrenOfType(
    psiFile,
    PsiMethod::class.java
)

// Найти parent конкретного типа
val containingClass = PsiTreeUtil.getParentOfType(
    element,
    PsiClass::class.java
)
```

### Фильтрация

```kotlin
// Собрать все классы
val classes = PsiTreeUtil.collectElementsOfType(
    psiFile,
    PsiClass::class.java
)

// С фильтром
val publicMethods = PsiTreeUtil.findChildrenOfType(
    psiClass,
    PsiMethod::class.java
).filter { it.hasModifierProperty(PsiModifier.PUBLIC) }
```

## Visitor Pattern

Для обхода PSI дерева используйте Visitor:

```kotlin
class MyPsiVisitor : PsiRecursiveElementVisitor() {
    
    val foundElements = mutableListOf<PsiElement>()
    
    override fun visitElement(element: PsiElement) {
        super.visitElement(element)
        
        // Custom logic
        if (element is PsiMethod) {
            foundElements.add(element)
        }
    }
}

// Использование
val visitor = MyPsiVisitor()
psiFile.accept(visitor)

val methods = visitor.foundElements
```

## Модификация PSI

**Всегда в write action на EDT!**

```kotlin
import com.intellij.psi.PsiFileFactory

ApplicationManager.getApplication().invokeLater {
    writeAction {
        // Создать новый файл
        val newFile = PsiFileFactory.getInstance(project)
            .createFileFromText(
                "generated.py",
                PythonFileType.INSTANCE,
                "def hello():\n    pass"
            )
        
        // Добавить в директорию
        val directory = psiFile.containingDirectory
        directory.add(newFile)
        
        // Удалить элемент
        element.delete()
        
        // Заменить элемент
        element.replace(newElement)
    }
}
```

## PSI для LG Plugin

Для LG plugin PSI **не критичен** — работаем в основном с file paths.

### Когда может пригодиться

1. **Syntax highlighting** для generated code
2. **Navigate to definition** из generated listing
3. **Code folding** в preview

### Простой пример

```kotlin
fun getFileLanguage(file: VirtualFile, project: Project): Language? {
    return readAction {
        val psiFile = PsiManager.getInstance(project).findFile(file)
        psiFile?.language
    }
}

fun getFileStructure(file: VirtualFile, project: Project): String {
    return readAction {
        val psiFile = PsiManager.getInstance(project).findFile(file)
            ?: return@readAction "Unknown structure"
        
        buildString {
            appendLine("File: ${psiFile.name}")
            appendLine("Language: ${psiFile.language.id}")
            appendLine("Size: ${psiFile.textLength} chars")
        }
    }
}
```

## Важные правила для PSI

### 1. Всегда в read action (BGT)

```kotlin
// ✅ Правильно
val text = readAction {
    psiFile.text
}

// ❌ Неправильно (без read action на BGT)
val text = psiFile.text // Может упасть!
```

### 2. Проверяйте validity

```kotlin
if (!psiFile.isValid) {
    return // Файл был удалён
}

val text = readAction {
    if (!psiFile.isValid) return@readAction null
    psiFile.text
}
```

### 3. PSI может измениться между read actions

```kotlin
// ❌ Плохо
val element = readAction { psiFile.findElementAt(0) }
// ... другая работа ...
readAction {
    element.getText() // element может быть invalid!
}

// ✅ Правильно
val element = readAction { psiFile.findElementAt(0) }
// ... другая работа ...
readAction {
    if (!element.isValid) return@readAction
    element.text
}
```

## References

Для детального изучения PSI:
- [PSI Documentation](https://plugins.jetbrains.com/docs/intellij/psi.html)
- [PSI Cookbook](https://plugins.jetbrains.com/docs/intellij/psi-cookbook.html)
- [Custom Language Support](https://plugins.jetbrains.com/docs/intellij/custom-language-support.html)

**Для LG plugin:** PSI опционален, фокус на VFS и external processes.
