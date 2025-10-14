# Virtual File System (VFS)

## Обзор

**Virtual File System (VFS)** — компонент IntelliJ Platform для работы с файлами независимо от их фактического расположения.

VFS инкапсулирует всю работу с файлами и предоставляет:
- ✅ Универсальный API (файлы на диске, в архивах, на HTTP серверах)
- ✅ Отслеживание изменений файлов (с old/new версиями)
- ✅ Хранение дополнительных метаданных
- ✅ События об изменениях (event-driven)
- ✅ Кэширование в памяти (snapshot)

## Snapshot-based Architecture

VFS поддерживает **persistent snapshot** части жёсткого диска пользователя в памяти.

### Как работает

```
Disk                VFS Snapshot              IntelliJ Platform
─────               ────────────              ──────────────────
file.txt ─refresh→  file.txt (cached)  ←────  UI / PSI / Code
                         ↓
                    metadata + content
```

**Важные моменты:**
1. Snapshot **application-level** (не project-level)
   - Один файл из JDK для всех проектов → одна копия в snapshot

2. Snapshot обновляется **асинхронно** через refresh operations

3. **Write operations** через VFS → **синхронные** (сразу на диск)

4. Snapshot содержит только **запрошенные** файлы/директории

5. Файл остаётся в snapshot **навсегда** (пока не удалён и не сделан refresh)

### Последствия

```kotlin
// UI показывает данные из snapshot, а не с диска!

// Сценарий:
// 1. File.txt существует на диске
// 2. Внешний процесс удаляет file.txt
// 3. VFS ещё не знает об этом
// 4. UI продолжает показывать file.txt (из snapshot)
// 5. После refresh — файл исчезнет из UI
```

## VirtualFile

[`VirtualFile`](https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/openapi/vfs/VirtualFile.java) — основной класс для работы с файлами.

### Получение VirtualFile

```kotlin
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

// Из file path
val virtualFile = LocalFileSystem.getInstance()
    .findFileByPath("/path/to/file.txt")

// Из java.io.File
val javaFile = File("/path/to/file.txt")
val virtualFile = LocalFileSystem.getInstance()
    .findFileByIoFile(javaFile)

// Из Project
val baseDir = project.baseDir // VirtualFile директории проекта
val projectFile = project.projectFile

// Из Editor
val virtualFile = FileDocumentManager.getInstance()
    .getFile(editor.document)

// Из PsiFile
val virtualFile = psiFile.virtualFile
```

### Основные свойства

```kotlin
val file: VirtualFile = ...

// Базовая информация
val name: String = file.name                    // "file.txt"
val path: String = file.path                    // "/path/to/file.txt"
val extension: String? = file.extension         // "txt"
val nameWithoutExtension = file.nameWithoutExtension

// Проверки
val isDirectory: Boolean = file.isDirectory
val isValid: Boolean = file.isValid             // Файл ещё существует?
val isWritable: Boolean = file.isWritable
val exists: Boolean = file.exists()

// Размер и время
val length: Long = file.length                  // Размер в байтах
val timeStamp: Long = file.timeStamp            // Modification time

// Родитель и путь
val parent: VirtualFile? = file.parent
val canonicalPath: String? = file.canonicalPath
```

### Чтение содержимого

```kotlin
// Как ByteArray
val bytes: ByteArray = file.contentsToByteArray()

// Как String
val text = String(file.contentsToByteArray(), Charsets.UTF_8)

// Через InputStream
file.inputStream.use { stream ->
    // Read from stream
}

// Через BOM-aware InputStream
val bomStream = file.inputStream
val charset = file.charset
```

### Запись содержимого

```kotlin
// ⚠️ Всегда в write action!
import com.intellij.openapi.application.writeAction

writeAction {
    // Из String
    file.setBinaryContent("New content".toByteArray())
    
    // Из ByteArray
    val bytes = loadBytes()
    file.setBinaryContent(bytes)
    
    // Через OutputStream
    file.getOutputStream(requestor = null).use { stream ->
        stream.write(bytes)
    }
}
```

**Requestor:**
- Объект, запросивший изменение (для отслеживания)
- Обычно `null` или `this`

### Навигация по дереву

```kotlin
// Родитель
val parent: VirtualFile? = file.parent

// Дети (для директории)
val children: Array<VirtualFile> = directory.children

// Конкретный дочерний файл
val child: VirtualFile? = directory.findChild("file.txt")

// Рекурсивный поиск
val found: VirtualFile? = directory.findFileByRelativePath("subdir/file.txt")

// Проверка вложенности
val isAncestor = VfsUtil.isAncestor(ancestor, descendant, strict = false)
```

### Создание файлов/директорий

```kotlin
// В write action!
writeAction {
    // Создать файл
    val newFile = directory.createChildData(
        requestor = null,
        "newfile.txt"
    )
    newFile.setBinaryContent("Content".toByteArray())
    
    // Создать директорию
    val newDir = directory.createChildDirectory(
        requestor = null,
        "subdir"
    )
    
    // Удалить
    file.delete(requestor = null)
    
    // Переименовать
    file.rename(requestor = null, "newname.txt")
    
    // Переместить
    file.move(requestor = null, newParent)
}
```

## LocalFileSystem

Главный VFS для работы с локальными файлами.

### Получение instance

```kotlin
import com.intellij.openapi.vfs.LocalFileSystem

val lfs = LocalFileSystem.getInstance()
```

### Поиск файлов

```kotlin
// Синхронно (если в snapshot)
val file = lfs.findFileByPath("/path/to/file.txt")

// С автоматическим refresh (может быть медленно)
val file = lfs.refreshAndFindFileByPath("/path/to/file.txt")

// Из java.io.File
val javaFile = File("/path/to/file.txt")
val virtualFile = lfs.findFileByIoFile(javaFile)

// Refresh + find
val virtualFile = lfs.refreshAndFindFileByIoFile(javaFile)
```

**Важно:**
- `findFile*` — быстро, но может вернуть `null` если файл не в snapshot
- `refreshAndFind*` — может быть медленно (I/O operation)

## Refresh Operations

Refresh синхронизирует VFS snapshot с реальным диском.

### Refresh файла/директории

```kotlin
import com.intellij.openapi.vfs.VirtualFile

// Асинхронный refresh (рекомендуется)
file.refresh(
    async = true,         // Асинхронно
    recursive = true      // Рекурсивно для поддиректорий
)

// С callback
file.refresh(async = true, recursive = true) {
    // Вызовется после refresh
    println("Refresh completed")
}
```

### Refresh через RefreshQueue

```kotlin
import com.intellij.openapi.vfs.newvfs.RefreshQueue

val files = listOf(file1, file2, file3)

RefreshQueue.getInstance().refresh(
    async = true,
    recursive = true,
    postAction = {
        // После refresh
        println("All files refreshed")
    },
    files = files
)
```

### Синхронный refresh (избегайте!)

```kotlin
// ⚠️ Может вызвать deadlock!
file.refresh(async = false, recursive = true)
```

**Почему избегать:**
- Блокирует текущий поток
- Может вызвать deadlock если держите read lock
- Ухудшает UX

**Когда использовать:**
- Только в тестах
- Когда необходимо гарантировать актуальность перед следующей операцией

## VFS Events

VFS генерирует события при изменении файлов.

### BulkFileListener

**Рекомендуемый** способ подписки на VFS события:

```kotlin
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.*

class MyFileListener : BulkFileListener {
    
    override fun before(events: List<VFileEvent>) {
        // ДО изменений (можно получить old content)
        for (event in events) {
            when (event) {
                is VFileDeleteEvent -> {
                    val file = event.file
                    // Файл ещё в snapshot, можно прочитать
                    val oldContent = file.contentsToByteArray()
                }
            }
        }
    }
    
    override fun after(events: List<VFileEvent>) {
        // ПОСЛЕ изменений
        for (event in events) {
            when (event) {
                is VFileCreateEvent -> {
                    val file = event.file
                    println("Created: ${file?.path}")
                }
                is VFileDeleteEvent -> {
                    println("Deleted: ${event.path}")
                }
                is VFileContentChangeEvent -> {
                    val file = event.file
                    println("Changed: ${file.path}")
                }
                is VFileMoveEvent -> {
                    println("Moved: ${event.file.path}")
                }
                is VFilePropertyChangeEvent -> {
                    if (event.propertyName == VirtualFile.PROP_NAME) {
                        println("Renamed: ${event.oldValue} → ${event.newValue}")
                    }
                }
            }
        }
    }
}
```

### Регистрация Listener

**В plugin.xml:**

```xml
<applicationListeners>
    <listener 
        topic="com.intellij.openapi.vfs.newvfs.BulkFileListener"
        class="com.example.MyFileListener"/>
</applicationListeners>
```

**Programmatically:**

```kotlin
@Service(Service.Level.PROJECT)
class MyService(private val project: Project) : Disposable {
    
    init {
        project.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    handleFileChanges(events)
                }
            }
        )
    }
    
    override fun dispose() {
        // Auto-unsubscribe
    }
}
```

### Фильтрация событий по проекту

VFS listeners **application-level** → получают события для **всех** проектов!

Фильтруйте:

```kotlin
import com.intellij.openapi.roots.ProjectFileIndex

override fun after(events: List<VFileEvent>) {
    val projectFileIndex = ProjectFileIndex.getInstance(project)
    
    for (event in events) {
        val file = event.file ?: continue
        
        // Фильтр: только файлы проекта
        if (!projectFileIndex.isInContent(file)) {
            continue
        }
        
        // Обработать
        handleFileChange(file)
    }
}
```

Фильтры:
- `isInContent(file)` — файл в content roots проекта
- `isInSource(file)` — файл в source roots
- `isInSourceContent(file)` — файл в source roots (не test/resource)
- `isInTestSourceContent(file)` — файл в test roots

## VirtualFileManager

Главный менеджер для всех VFS.

```kotlin
import com.intellij.openapi.vfs.VirtualFileManager

val vfm = VirtualFileManager.getInstance()

// Найти по URL
val file = vfm.findFileByUrl("file:///path/to/file.txt")

// Refresh всего
vfm.refreshWithoutFileWatcher(async = true)

// Синхронизировать с диском
vfm.syncRefresh()
```

## Working with Directories

### Поиск lg-cfg директории

```kotlin
fun findLgConfigDir(project: Project): VirtualFile? {
    val baseDir = project.baseDir ?: return null
    return baseDir.findChild("lg-cfg")
}
```

### Сканирование файлов

```kotlin
fun collectYamlFiles(directory: VirtualFile): List<VirtualFile> {
    val result = mutableListOf<VirtualFile>()
    
    VfsUtil.processFilesRecursively(directory) { file ->
        if (file.extension == "yaml" || file.extension == "yml") {
            result.add(file)
        }
        true // Continue processing
    }
    
    return result
}
```

### Создание структуры директорий

```kotlin
writeAction {
    val baseDir = project.baseDir ?: return@writeAction
    
    // Создать lg-cfg/
    val lgCfgDir = baseDir.createChildDirectory(null, "lg-cfg")
    
    // Создать sections.yaml
    val sectionsFile = lgCfgDir.createChildData(null, "sections.yaml")
    sectionsFile.setBinaryContent(defaultSectionsYaml.toByteArray())
    
    // Создать поддиректории
    val templatesDir = lgCfgDir.createChildDirectory(null, "templates")
}
```

## VFS и java.io.File

### Конверсия

```kotlin
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

// VirtualFile → java.io.File
val file: VirtualFile = ...
val ioFile: File = File(file.path)

// java.io.File → VirtualFile
val ioFile = File("/path/to/file")
val virtualFile = LocalFileSystem.getInstance()
    .findFileByIoFile(ioFile)
```

**Важно:** VirtualFile может указывать на файл в JAR, HTTP, в памяти — `File(file.path)` не всегда корректен!

### NioPath support

```kotlin
import java.nio.file.Path

// VirtualFile → Path
val path: Path = file.toNioPath()

// Path → VirtualFile
val nioPath = Path.of("/path/to/file")
val virtualFile = LocalFileSystem.getInstance()
    .findFileByNioFile(nioPath)
```

## Document и VirtualFile

Связь между VirtualFile и Document (editor).

### VirtualFile → Document

```kotlin
import com.intellij.openapi.fileEditor.FileDocumentManager

val file: VirtualFile = ...

// Получить Document (или null если не loaded)
val document = FileDocumentManager.getInstance()
    .getDocument(file)

// Получить Document (загрузить если нужно)
val document = FileDocumentManager.getInstance()
    .getCachedDocument(file) ?: FileDocumentManager.getInstance()
        .getDocument(file)
```

### Document → VirtualFile

```kotlin
val document: Document = editor.document

val file = FileDocumentManager.getInstance()
    .getFile(document)
```

### Сохранение Document

```kotlin
// Сохранить все документы
FileDocumentManager.getInstance().saveAllDocuments()

// Сохранить конкретный
FileDocumentManager.getInstance().saveDocument(document)
```

## File Types

### Получение FileType

```kotlin
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager

val file: VirtualFile = ...

// FileType файла
val fileType: FileType = file.fileType

// По extension
val yamlType = FileTypeManager.getInstance()
    .getFileTypeByExtension("yaml")

// По filename
val fileType = FileTypeManager.getInstance()
    .getFileTypeByFileName("sections.yaml")
```

### Стандартные FileTypes

```kotlin
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.fileTypes.PlainTextFileType

FileTypes.PLAIN_TEXT
PlainTextFileType.INSTANCE

// Или через FileTypeManager
val jsonType = FileTypeManager.getInstance()
    .getStdFileType("JSON")
```

## VFS Utils

### VfsUtil

```kotlin
import com.intellij.openapi.vfs.VfsUtil

// Рекурсивная обработка файлов
VfsUtil.processFilesRecursively(directory) { file ->
    println(file.path)
    true // continue (false = stop)
}

// Копирование
VfsUtil.copy(requestor = null, source, target)

// Создание родительских директорий
val file = VfsUtil.createDirectoryIfMissing("/path/to/nested/dir")

// Относительный путь
val relativePath = VfsUtil.getRelativePath(file, baseDirectory)

// Путь для отображения
val presentablePath = VfsUtil.getPathForLibraryJar(file)
```

### VfsUtilCore

```kotlin
import com.intellij.openapi.vfs.VfsUtilCore

// virtualFile → java.io.File
val ioFile = VfsUtilCore.virtualToIoFile(virtualFile)

// Посетить дерево (visitor pattern)
VfsUtilCore.visitChildrenRecursively(directory, object : VirtualFileVisitor<Unit>() {
    override fun visitFile(file: VirtualFile): Boolean {
        if (file.extension == "yaml") {
            processYaml(file)
        }
        return true
    }
})
```

## File Watchers

IntelliJ Platform запускает **native file watcher** процесс:
- Windows: ReadDirectoryChangesW
- macOS: FSEvents
- Linux: inotify

File watcher сообщает о изменениях → VFS делает targeted refresh только изменённых файлов.

**Без file watcher:** refresh сканирует все директории (медленно).

### Просмотр watched roots

Internal Mode → Tools → Internal Actions → VFS → Show Watched VFS Roots

## Read/Write Operations

### Правила threading

```kotlin
// Чтение — любой поток (в read action если BGT)
readAction {
    val content = file.contentsToByteArray()
}

// Запись — только EDT (в write action)
ApplicationManager.getApplication().invokeLater {
    writeAction {
        file.setBinaryContent(newContent)
    }
}
```

### Модификация из Action

```kotlin
class ModifyFileAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        
        // actionPerformed выполняется на EDT
        writeAction {
            file.setBinaryContent("Modified content".toByteArray())
        }
    }
    
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
```

### Модификация из background task

```kotlin
object : Task.Backgroundable(project, "Modifying...", true) {
    
    override fun run(indicator: ProgressIndicator) {
        // Background thread
        val newContent = generateContent()
        
        // Switch to EDT для записи
        ApplicationManager.getApplication().invokeLater {
            writeAction {
                file.setBinaryContent(newContent)
            }
        }
    }
}.queue()
```

## Ignored Files

VFS **не учитывает** ignored files из:
- Settings → Editor → File Types → Ignored Files and Folders
- Project Structure → Modules → Sources → Excluded

Если код обращается к ignored file → VFS вернёт его!

**Фильтрация в коде:**

```kotlin
import com.intellij.openapi.fileTypes.FileTypeManager

val fileTypeManager = FileTypeManager.getInstance()

if (!fileTypeManager.isFileIgnored(file)) {
    // Файл не ignored
    processFile(file)
}
```

## Temporary File System

Для временных файлов в памяти:

```kotlin
import com.intellij.openapi.vfs.ex.temp.TempFileSystem

val tempFs = TempFileSystem.getInstance()

// Создать временный файл
val tempFile = runWriteAction {
    tempFs.createRoot().createChildData(null, "temp.txt")
}

tempFile.setBinaryContent("Temporary content".toByteArray())
```

## JarFileSystem

Для файлов внутри JAR:

```kotlin
import com.intellij.openapi.vfs.JarFileSystem

val jarFs = JarFileSystem.getInstance()

// Открыть JAR
val jarRoot = jarFs.findFileByPath("/path/to/lib.jar!/")

// Файл внутри JAR
val classFile = jarRoot?.findFileByRelativePath("com/example/MyClass.class")
```

## URL Scheme

VFS использует URL для файлов:

```
file:///path/to/file.txt              # Локальный файл
jar:///path/to/lib.jar!/File.class    # Файл в JAR
http://example.com/data.json          # HTTP ресурс
temp:///temp-file.txt                 # Временный файл
```

### Работа с URLs

```kotlin
val url = file.url // "file:///path/to/file.txt"

// Найти по URL
val file = VirtualFileManager.getInstance()
    .findFileByUrl(url)
```

## Project FileIndex

Для определения роли файла в проекте.

```kotlin
import com.intellij.openapi.roots.ProjectFileIndex

val fileIndex = ProjectFileIndex.getInstance(project)

// В content roots?
fileIndex.isInContent(file)

// В source roots?
fileIndex.isInSource(file)
fileIndex.isInSourceContent(file) // Не test

// В test roots?
fileIndex.isInTestSourceContent(file)

// В library?
fileIndex.isInLibrary(file)

// Получить Module
val module = fileIndex.getModuleForFile(file)

// Получить content root
val contentRoot = fileIndex.getContentRootForFile(file)
```

## PathUtil

Утилиты для работы с путями:

```kotlin
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil

// Относительный путь
val relative = FileUtil.getRelativePath(
    baseDir.path,
    file.path,
    '/' // separator
)

// Получить директорию из пути
val dir = PathUtil.getParentPath("/path/to/file.txt") // "/path/to"

// Получить filename
val name = PathUtil.getFileName("/path/to/file.txt") // "file.txt"

// Join paths (POSIX)
val fullPath = FileUtil.join(baseDir.path, "subdir", "file.txt")

// Canonical path
val canonical = FileUtil.toCanonicalPath("/path/to/../file.txt")
```

## Listening for specific files

Подписка на изменения **конкретных** файлов:

```kotlin
@Service(Service.Level.PROJECT)
class SectionsYamlWatcher(private val project: Project) : Disposable {
    
    init {
        project.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    for (event in events) {
                        val file = event.file ?: continue
                        
                        // Интересуют только lg-cfg/sections.yaml
                        if (file.name == "sections.yaml" && 
                            file.parent?.name == "lg-cfg"
                        ) {
                            when (event) {
                                is VFileContentChangeEvent -> reloadSections()
                                is VFileDeleteEvent -> sectionsDeleted()
                                is VFileCreateEvent -> sectionsCreated()
                            }
                        }
                    }
                }
            }
        )
    }
    
    override fun dispose() { }
}
```

## Performance Tips

### 1. Кэшируйте VirtualFile references

```kotlin
@Service(Service.Level.PROJECT)
class LgConfigService(private val project: Project) {
    
    private var cachedConfigDir: VirtualFile? = null
    
    fun getConfigDir(): VirtualFile? {
        // Проверить validity
        if (cachedConfigDir?.isValid == true) {
            return cachedConfigDir
        }
        
        // Refresh cache
        cachedConfigDir = project.baseDir?.findChild("lg-cfg")
        return cachedConfigDir
    }
}
```

### 2. Используйте VirtualFile вместо paths

```kotlin
// ❌ Плохо — каждый раз поиск
fun processFile(path: String) {
    val file = LocalFileSystem.getInstance()
        .findFileByPath(path) // Lookup в snapshot
    // ...
}

// ✅ Хорошо — передавать VirtualFile
fun processFile(file: VirtualFile) {
    // Уже есть
}
```

### 3. Батчинг write operations

```kotlin
// ❌ Плохо — много мелких write actions
for (file in files) {
    writeAction {
        file.delete(null)
    }
}

// ✅ Хорошо — одна write action
writeAction {
    for (file in files) {
        file.delete(null)
    }
}
```

### 4. Асинхронный refresh

```kotlin
// ❌ Блокирует поток
file.refresh(async = false, recursive = true)

// ✅ Не блокирует
file.refresh(async = true, recursive = true)
```

## AsyncFileListener (non-blocking)

Для обработки VFS events **без блокировки** write action:

```kotlin
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

class MyAsyncFileListener : AsyncFileListener {
    
    override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
        // Подготовка (может быть на BGT)
        val relevantEvents = events.filter { isRelevant(it) }
        
        if (relevantEvents.isEmpty()) {
            return null
        }
        
        return object : AsyncFileListener.ChangeApplier {
            override fun afterVfsChange() {
                // После применения изменений (на EDT в write action)
                handleEvents(relevantEvents)
            }
        }
    }
}
```

Регистрация:
```xml
<applicationListeners>
    <listener 
        topic="com.intellij.openapi.vfs.AsyncFileListener"
        class="com.example.MyAsyncFileListener"/>
</applicationListeners>
```

## Validity Checks

Всегда проверяйте validity:

```kotlin
fun processFile(file: VirtualFile) {
    if (!file.isValid) {
        // Файл был удалён
        return
    }
    
    // Безопасно использовать
    val content = file.contentsToByteArray()
}
```

**Когда файл становится invalid:**
- Удалён с диска (и был refresh)
- Переименован
- Перемещён
- Проект закрыт

## Common Patterns

### Watching lg-cfg/ directory

```kotlin
@Service(Service.Level.PROJECT)
class LgConfigWatcher(
    private val project: Project,
    private val scope: CoroutineScope
) : Disposable {
    
    init {
        project.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    val configDir = getConfigDir() ?: return
                    
                    val hasConfigChanges = events.any { event ->
                        val file = event.file ?: return@any false
                        VfsUtil.isAncestor(configDir, file, false)
                    }
                    
                    if (hasConfigChanges) {
                        scope.launch {
                            reloadConfiguration()
                        }
                    }
                }
            }
        )
    }
    
    private fun getConfigDir(): VirtualFile? {
        return project.baseDir?.findChild("lg-cfg")
    }
    
    override fun dispose() { }
}
```

### Reading file asynchronously

```kotlin
suspend fun readFileAsync(file: VirtualFile): String {
    return withContext(Dispatchers.IO) {
        readAction {
            String(file.contentsToByteArray(), Charsets.UTF_8)
        }
    }
}
```

### Writing file asynchronously

```kotlin
suspend fun writeFileAsync(file: VirtualFile, content: String) {
    withContext(Dispatchers.EDT) {
        writeAction {
            file.setBinaryContent(content.toByteArray())
        }
    }
}
```

### Создание файла в проекте

```kotlin
suspend fun createLgConfigFile(project: Project) {
    withContext(Dispatchers.EDT) {
        writeAction {
            val baseDir = project.baseDir 
                ?: throw IllegalStateException("No base directory")
            
            val lgCfgDir = baseDir.findChild("lg-cfg") 
                ?: baseDir.createChildDirectory(null, "lg-cfg")
            
            val sectionsFile = lgCfgDir.findChild("sections.yaml")
                ?: lgCfgDir.createChildData(null, "sections.yaml")
            
            sectionsFile.setBinaryContent(
                defaultSectionsContent().toByteArray()
            )
        }
    }
}
```

## Testing VFS

### Test с temporary files

```kotlin
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class VfsTest : BasePlatformTestCase() {
    
    fun testFileCreation() {
        // Создать временный файл
        val file = myFixture.createFile(
            "test.yaml",
            "key: value"
        )
        
        assertNotNull(file.virtualFile)
        assertEquals("test.yaml", file.virtualFile.name)
        
        // Прочитать содержимое
        val content = String(file.virtualFile.contentsToByteArray())
        assertEquals("key: value", content)
    }
    
    fun testFileModification() {
        val file = myFixture.createFile("test.txt", "old")
        
        writeAction {
            file.virtualFile.setBinaryContent("new".toByteArray())
        }
        
        val content = String(file.virtualFile.contentsToByteArray())
        assertEquals("new", content)
    }
}
```

## Common Pitfalls

### 1. Забыли проверить validity

```kotlin
// ❌ Может упасть
fun processFile(file: VirtualFile) {
    val content = file.contentsToByteArray() // NPE если invalid!
}

// ✅ Правильно
fun processFile(file: VirtualFile) {
    if (!file.isValid) return
    val content = file.contentsToByteArray()
}
```

### 2. Запись без write action

```kotlin
// ❌ Упадёт с assertion
file.setBinaryContent(bytes)

// ✅ Правильно
writeAction {
    file.setBinaryContent(bytes)
}
```

### 3. Синхронный refresh с read lock

```kotlin
// ❌ DEADLOCK!
readAction {
    file.refresh(async = false, recursive = true) // Держим read lock!
}

// ✅ Правильно
file.refresh(async = true, recursive = true) // Не блокирует
```

### 4. Использование File вместо VirtualFile

```kotlin
// ❌ Обходит VFS
val ioFile = File(file.path)
ioFile.writeText("new content") // VFS не знает об изменении!

// ✅ Правильно
writeAction {
    file.setBinaryContent("new content".toByteArray()) // VFS знает
}
```
