# Tree Views в IntelliJ Platform

## Обзор

**Tree Views** используются для отображения иерархических данных:
- Project View (дерево файлов)
- Structure View (структура файла)
- Included Files (в LG plugin)
- Dependencies Tree
- И многое другое

IntelliJ Platform предоставляет расширенные компоненты на базе Swing `JTree`.

## Базовые компоненты

### Tree

```kotlin
import com.intellij.ui.treeStructure.Tree
import javax.swing.tree.*

val root = DefaultMutableTreeNode("Root")
root.add(DefaultMutableTreeNode("Child 1"))
root.add(DefaultMutableTreeNode("Child 2"))

val model = DefaultTreeModel(root)
val tree = Tree(model)
```

**Отличия от JTree:**
- Лучший UI (соответствует IDE стилю)
- Поддержка SpeedSearch (быстрый поиск при typing)
- Лучше performance для больших деревьев

## TreeModel

### DefaultTreeModel (простой)

```kotlin
val root = DefaultMutableTreeNode("Root")
val model = DefaultTreeModel(root)

// Добавить узел
val newNode = DefaultMutableTreeNode("New Item")
root.add(newNode)
model.nodeStructureChanged(root)

// Удалить узел
model.removeNodeFromParent(newNode)

// Обновить узел
model.nodeChanged(node)
```

### Custom TreeModel

```kotlin
import javax.swing.tree.TreeModel
import javax.swing.event.TreeModelListener

class MyTreeModel(private val root: MyNode) : TreeModel {
    
    private val listeners = mutableListOf<TreeModelListener>()
    
    override fun getRoot() = root
    
    override fun getChild(parent: Any, index: Int): Any {
        return (parent as MyNode).children[index]
    }
    
    override fun getChildCount(parent: Any): Int {
        return (parent as MyNode).children.size
    }
    
    override fun isLeaf(node: Any): Boolean {
        return (node as MyNode).children.isEmpty()
    }
    
    override fun getIndexOfChild(parent: Any, child: Any): Int {
        return (parent as MyNode).children.indexOf(child)
    }
    
    override fun valueForPathChanged(path: TreePath, newValue: Any) {
        // Handle edit
    }
    
    override fun addTreeModelListener(l: TreeModelListener) {
        listeners.add(l)
    }
    
    override fun removeTreeModelListener(l: TreeModelListener) {
        listeners.remove(l)
    }
}
```

## TreeCellRenderer

Custom отображение узлов дерева.

### SimpleTreeCellRenderer

```kotlin
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes

val tree = Tree(model)

tree.cellRenderer = object : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(
        tree: JTree,
        value: Any,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ) {
        when (val node = (value as? DefaultMutableTreeNode)?.userObject) {
            is VirtualFile -> {
                // File node
                icon = node.fileType.icon
                append(node.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                
                // Показать размер
                val size = node.length
                append(" (${formatSize(size)})", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
            is String -> {
                // Directory node
                icon = AllIcons.Nodes.Folder
                append(node, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
            }
        }
    }
}
```

### IconDescriptor для dynamic icons

```kotlin
override fun customizeCellRenderer(...) {
    when (val item = userObject) {
        is FileItem -> {
            // Dynamic icon в зависимости от состояния
            icon = if (item.isModified) {
                AllIcons.Actions.Edit
            } else {
                item.file.fileType.icon
            }
            
            append(item.file.name)
        }
    }
}
```

## Tree Selection

### Selection Modes

```kotlin
import javax.swing.tree.TreeSelectionModel

tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
// Или:
// CONTIGUOUS_TREE_SELECTION — непрерывный диапазон
// DISCONTIGUOUS_TREE_SELECTION — множественный выбор
```

### Listening to Selection

```kotlin
tree.addTreeSelectionListener { event ->
    val selectedNode = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
    val userObject = selectedNode?.userObject
    
    when (userObject) {
        is VirtualFile -> onFileSelected(userObject)
        is String -> onDirectorySelected(userObject)
    }
}
```

### Programmatic Selection

```kotlin
// Выбрать узел
val path = TreePath(arrayOf(root, child))
tree.selectionPath = path

// Или через node
val node = findNode(value)
if (node != null) {
    val path = TreePath(node.path)
    tree.selectionPath = path
    tree.scrollPathToVisible(path)
}
```

## Tree Actions

### Double-Click

```kotlin
tree.addMouseListener(object : MouseAdapter() {
    override fun mouseClicked(e: MouseEvent) {
        if (e.clickCount == 2) {
            val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
            val file = node?.userObject as? VirtualFile
            
            if (file != null && !file.isDirectory) {
                // Открыть файл
                FileEditorManager.getInstance(project)
                    .openFile(file, true)
            }
        }
    }
})
```

### Context Menu

```kotlin
tree.addMouseListener(object : MouseAdapter() {
    override fun mousePressed(e: MouseEvent) {
        if (e.isPopupTrigger) showPopup(e)
    }
    
    override fun mouseReleased(e: MouseEvent) {
        if (e.isPopupTrigger) showPopup(e)
    }
    
    private fun showPopup(e: MouseEvent) {
        // Выбрать узел под курсором
        val path = tree.getPathForLocation(e.x, e.y)
        tree.selectionPath = path
        
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
        val file = node?.userObject as? VirtualFile
        
        if (file != null) {
            val actionGroup = DefaultActionGroup().apply {
                add(OpenFileAction(file))
                add(CopyPathAction(file))
                addSeparator()
                add(RefreshAction())
            }
            
            val popup = ActionManager.getInstance()
                .createActionPopupMenu("TreeContext", actionGroup)
            
            popup.component.show(e.component, e.x, e.y)
        }
    }
})
```

## Expand/Collapse

### Programmatic Expand

```kotlin
// Развернуть узел
tree.expandPath(TreePath(node.path))

// Развернуть все
fun expandAll() {
    for (i in 0 until tree.rowCount) {
        tree.expandRow(i)
    }
}

// Развернуть до уровня
fun expandToLevel(level: Int) {
    fun expand(node: TreeNode, currentLevel: Int) {
        if (currentLevel >= level) return
        
        val path = TreePath((node as DefaultMutableTreeNode).path)
        tree.expandPath(path)
        
        for (i in 0 until node.childCount) {
            expand(node.getChildAt(i), currentLevel + 1)
        }
    }
    
    expand(model.root as TreeNode, 0)
}

// Свернуть всё
tree.collapseRow(0)
```

### Auto-expand на изменении

```kotlin
model.addTreeModelListener(object : TreeModelListener {
    override fun treeNodesInserted(e: TreeModelEvent) {
        // Развернуть parent добавленных узлов
        val path = e.treePath
        tree.expandPath(path)
    }
    
    override fun treeNodesRemoved(e: TreeModelEvent) { }
    override fun treeNodesChanged(e: TreeModelEvent) { }
    override fun treeStructureChanged(e: TreeModelEvent) { }
})
```

## Speed Search

IntelliJ Platform tree автоматически поддерживает **Speed Search** — поиск при typing.

### Включение

```kotlin
import com.intellij.ui.TreeSpeedSearch

// По умолчанию уже включён для Tree()
// Для кастомизации:
TreeSpeedSearch(tree) { treePath ->
    val node = treePath.lastPathComponent as? DefaultMutableTreeNode
    val obj = node?.userObject
    
    when (obj) {
        is VirtualFile -> obj.name
        is String -> obj
        else -> obj.toString()
    }
}
```

Теперь пользователь может просто начать печатать в дереве для поиска.

## Async Tree (для больших данных)

Для дерева с ленивой загрузкой:

```kotlin
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel

class MyTreeStructure : AbstractTreeStructure() {
    
    private val root = MyRootNode()
    
    override fun getRootElement() = root
    
    override fun getChildElements(element: Any): Array<Any> {
        return when (element) {
            is MyRootNode -> element.loadChildren() // Может быть async!
            is MyNode -> element.children.toTypedArray()
            else -> emptyArray()
        }
    }
    
    override fun getParentElement(element: Any): Any? {
        return (element as? MyNode)?.parent
    }
    
    override fun commit() { }
    
    override fun hasSomethingToCommit() = false
}

// Создание async tree
val structure = MyTreeStructure()
val structureModel = StructureTreeModel(structure, disposable)
val asyncModel = AsyncTreeModel(structureModel, disposable)
val tree = Tree(asyncModel)
```

## Included Files Tree (LG Plugin)

### File Tree Structure

```kotlin
data class FileNode(
    val name: String,
    val file: VirtualFile?,
    val children: MutableList<FileNode> = mutableListOf()
)

class IncludedFilesTree(
    private val project: Project,
    private val disposable: Disposable
) {
    
    private val rootNode = DefaultMutableTreeNode("Root")
    private val model = DefaultTreeModel(rootNode)
    
    val tree = Tree(model).apply {
        isRootVisible = false
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        
        cellRenderer = FileTreeCellRenderer()
        
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    openSelectedFile()
                }
            }
        })
    }
    
    fun setPaths(paths: List<String>) {
        ApplicationManager.getApplication().invokeLater {
            updateTree(paths)
        }
    }
    
    private fun updateTree(paths: List<String>) {
        rootNode.removeAllChildren()
        
        // Build tree structure
        val fileTree = buildFileTree(paths)
        addNodesToRoot(fileTree)
        
        model.nodeStructureChanged(rootNode)
        
        // Expand first level
        tree.expandRow(0)
    }
    
    private fun buildFileTree(paths: List<String>): FileNode {
        val root = FileNode("Root", null)
        
        for (path in paths) {
            insertPath(root, path.split('/'), path)
        }
        
        return root
    }
    
    private fun insertPath(
        parent: FileNode,
        parts: List<String>,
        fullPath: String
    ) {
        if (parts.isEmpty()) return
        
        val name = parts.first()
        val remaining = parts.drop(1)
        
        var child = parent.children.find { it.name == name }
        
        if (child == null) {
            val isFile = remaining.isEmpty()
            val file = if (isFile) {
                findVirtualFile(fullPath)
            } else {
                null
            }
            
            child = FileNode(name, file)
            parent.children.add(child)
        }
        
        if (remaining.isNotEmpty()) {
            insertPath(child, remaining, fullPath)
        }
    }
    
    private fun addNodesToRoot(fileNode: FileNode) {
        for (child in fileNode.children.sortedBy { it.name }) {
            val node = if (child.file != null) {
                DefaultMutableTreeNode(child.file)
            } else {
                DefaultMutableTreeNode(child.name)
            }
            
            rootNode.add(node)
            
            if (child.children.isNotEmpty()) {
                addChildNodes(node, child)
            }
        }
    }
    
    private fun addChildNodes(parent: DefaultMutableTreeNode, fileNode: FileNode) {
        for (child in fileNode.children.sortedBy { it.name }) {
            val node = if (child.file != null) {
                DefaultMutableTreeNode(child.file)
            } else {
                DefaultMutableTreeNode(child.name)
            }
            
            parent.add(node)
            
            if (child.children.isNotEmpty()) {
                addChildNodes(node, child)
            }
        }
    }
    
    private fun findVirtualFile(path: String): VirtualFile? {
        val fullPath = "${project.basePath}/$path"
        return LocalFileSystem.getInstance().findFileByPath(fullPath)
    }
    
    private fun openSelectedFile() {
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
        val file = node?.userObject as? VirtualFile
        
        if (file != null && !file.isDirectory) {
            FileEditorManager.getInstance(project).openFile(file, true)
        }
    }
}

class FileTreeCellRenderer : ColoredTreeCellRenderer() {
    
    override fun customizeCellRenderer(
        tree: JTree,
        value: Any,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ) {
        val node = value as? DefaultMutableTreeNode
        
        when (val obj = node?.userObject) {
            is VirtualFile -> {
                icon = obj.fileType.icon
                append(obj.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }
            is String -> {
                icon = if (expanded) {
                    AllIcons.Nodes.FolderOpened
                } else {
                    AllIcons.Nodes.Folder
                }
                append(obj, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
            }
        }
    }
}
```

## Toggle View Mode (Flat vs Tree)

```kotlin
@Service(Service.Level.PROJECT)
@State(
    name = "IncludedFilesState",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)]
)
class IncludedFilesState : SimplePersistentStateComponent<State>(State()) {
    
    class State : BaseState() {
        var viewMode by enum(ViewMode.TREE)
    }
    
    enum class ViewMode { FLAT, TREE }
}

class IncludedFilesPanel(private val project: Project) : JPanel() {
    
    private val state = project.service<IncludedFilesState>()
    private val treeModel = DefaultTreeModel(DefaultMutableTreeNode("Root"))
    private val tree = Tree(treeModel)
    
    private var currentPaths: List<String> = emptyList()
    
    init {
        layout = BorderLayout()
        add(JBScrollPane(tree), BorderLayout.CENTER)
        
        // Показать текущий режим
        updateView()
    }
    
    fun toggleViewMode() {
        state.state.viewMode = when (state.state.viewMode) {
            IncludedFilesState.ViewMode.FLAT -> IncludedFilesState.ViewMode.TREE
            IncludedFilesState.ViewMode.TREE -> IncludedFilesState.ViewMode.FLAT
        }
        
        updateView()
    }
    
    fun setPaths(paths: List<String>) {
        currentPaths = paths
        updateView()
    }
    
    private fun updateView() {
        ApplicationManager.getApplication().invokeLater {
            when (state.state.viewMode) {
                IncludedFilesState.ViewMode.FLAT -> buildFlatTree()
                IncludedFilesState.ViewMode.TREE -> buildHierarchicalTree()
            }
        }
    }
    
    private fun buildFlatTree() {
        val root = DefaultMutableTreeNode("Root")
        
        for (path in currentPaths.sorted()) {
            val file = findFile(path)
            root.add(DefaultMutableTreeNode(file ?: path))
        }
        
        treeModel.setRoot(root)
        tree.isRootVisible = false
    }
    
    private fun buildHierarchicalTree() {
        val root = DefaultMutableTreeNode("Root")
        val fileTree = buildFileHierarchy(currentPaths)
        
        addNodesToTree(root, fileTree)
        
        treeModel.setRoot(root)
        tree.isRootVisible = false
        
        // Expand first level
        tree.expandRow(0)
    }
}

// Toggle Action
class ToggleViewModeAction : AnAction("Toggle View Mode", null, AllIcons.Actions.ListFiles) {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val panel = project.service<LgToolWindowService>()
            .getIncludedFilesPanel()
        
        panel?.toggleViewMode()
    }
    
    override fun getActionUpdateThread() = ActionUpdateThread.EDT
}
```

## Drag and Drop

```kotlin
import com.intellij.ide.dnd.*
import java.awt.datatransfer.DataFlavor

// Enable drag
tree.dragEnabled = true

// Install drop handler
DnDSupport.createBuilder(tree)
    .setTargetChecker { event ->
        // Проверить можно ли drop
        event.attachedObject is VirtualFile
    }
    .setDropHandler { event ->
        val file = event.attachedObject as? VirtualFile ?: return@setDropHandler
        handleFileDrop(file)
    }
    .install()
```

## Toolbar для Tree

```kotlin
class TreePanel : SimpleToolWindowPanel(true, true) {
    
    private val tree = createTree()
    
    init {
        setContent(JBScrollPane(tree))
        toolbar = createToolbar()
    }
    
    private fun createToolbar(): JComponent {
        val group = DefaultActionGroup().apply {
            add(RefreshAction())
            add(ToggleViewModeAction())
            addSeparator()
            add(ExpandAllAction())
            add(CollapseAllAction())
        }
        
        val toolbar = ActionManager.getInstance()
            .createActionToolbar("TreeToolbar", group, true)
        
        toolbar.targetComponent = this
        return toolbar.component
    }
}

class ExpandAllAction : AnAction("Expand All", null, AllIcons.Actions.Expandall) {
    override fun actionPerformed(e: AnActionEvent) {
        expandAll(tree)
    }
    
    override fun getActionUpdateThread() = ActionUpdateThread.EDT
}

class CollapseAllAction : AnAction("Collapse All", null, AllIcons.Actions.Collapseall) {
    override fun actionPerformed(e: AnActionEvent) {
        collapseAll(tree)
    }
    
    override fun getActionUpdateThread() = ActionUpdateThread.EDT
}
```

## Empty State

Для пустого дерева:

```kotlin
import com.intellij.ui.components.JBLabel
import java.awt.BorderLayout

fun updateEmptyState(paths: List<String>) {
    if (paths.isEmpty()) {
        // Show empty state
        removeAll()
        
        val emptyLabel = JBLabel(
            "No files to display",
            AllIcons.General.Information,
            SwingConstants.CENTER
        ).apply {
            foreground = UIUtil.getInactiveTextColor()
        }
        
        add(emptyLabel, BorderLayout.CENTER)
    } else {
        // Show tree
        removeAll()
        add(JBScrollPane(tree), BorderLayout.CENTER)
    }
    
    revalidate()
    repaint()
}
```

## Loading State

```kotlin
class TreePanel : JPanel(BorderLayout()), Disposable {
    
    private val tree = createTree()
    private val loadingLabel = JLabel(
        "Loading...",
        AllIcons.Process.Step_1,
        SwingConstants.CENTER
    )
    
    init {
        showLoading()
        loadDataAsync()
    }
    
    private fun showLoading() {
        removeAll()
        add(loadingLabel, BorderLayout.CENTER)
        revalidate()
        repaint()
    }
    
    private fun showTree() {
        removeAll()
        add(JBScrollPane(tree), BorderLayout.CENTER)
        revalidate()
        repaint()
    }
    
    private fun loadDataAsync() {
        scope.launch {
            val data = loadData()
            
            withContext(Dispatchers.EDT) {
                updateTree(data)
                showTree()
            }
        }
    }
    
    override fun dispose() { }
}
```

## Tree с Checkboxes

```kotlin
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckedTreeNode

val root = CheckedTreeNode("Root")

val node1 = CheckedTreeNode("Item 1").apply { isChecked = true }
val node2 = CheckedTreeNode("Item 2").apply { isChecked = false }

root.add(node1)
root.add(node2)

val tree = CheckboxTree(
    object : CheckboxTree.CheckboxTreeCellRenderer() {
        override fun customizeRenderer(
            tree: JTree,
            value: Any,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ) {
            val node = value as? CheckedTreeNode
            textRenderer.append(node?.userObject?.toString() ?: "")
        }
    },
    root
)

// Получить checked items
fun getCheckedItems(): List<Any> {
    val result = mutableListOf<Any>()
    
    fun collectChecked(node: TreeNode) {
        if (node is CheckedTreeNode && node.isChecked) {
            result.add(node.userObject)
        }
        
        for (i in 0 until node.childCount) {
            collectChecked(node.getChildAt(i))
        }
    }
    
    collectChecked(root)
    return result
}
```

## Tooltip для Tree

```kotlin
tree.toolTipText = "" // Enable tooltips

tree.cellRenderer = object : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(...) {
        when (val obj = userObject) {
            is VirtualFile -> {
                append(obj.name)
                
                // Tooltip с полным путём
                toolTipText = obj.path
            }
        }
    }
}
```

## Best Practices

### 1. Root visible или нет

```kotlin
// ✅ Для логичного root
tree.isRootVisible = true
// Root: "Project Files"
//   ├─ src/
//   └─ tests/

// ✅ Для списка items
tree.isRootVisible = false
// (скрыть Root)
// ├─ src/
// └─ tests/
```

### 2. Используйте ColoredTreeCellRenderer

```kotlin
// ✅ Богатый renderer
class MyRenderer : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(...) {
        append(text, attributes)
        icon = ...
    }
}

// ❌ DefaultTreeCellRenderer (ограниченный)
```

### 3. Dispose tree resources

```kotlin
class TreePanel(disposable: Disposable) : JPanel() {
    
    private val tree = Tree(model)
    
    init {
        Disposer.register(disposable, Disposable {
            // Cleanup
            tree.removeAll()
        })
    }
}
```

### 4. Invoke Later для updates

```kotlin
fun updateTree(data: List<Item>) {
    ApplicationManager.getApplication().invokeLater {
        // Update tree model
        rebuildTree(data)
        model.nodeStructureChanged(root)
    }
}
```
