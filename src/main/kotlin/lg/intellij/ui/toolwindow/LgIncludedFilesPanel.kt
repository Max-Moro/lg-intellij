package lg.intellij.ui.toolwindow

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import lg.intellij.LgBundle
import lg.intellij.services.state.LgWorkspaceStateService
import lg.intellij.ui.renderers.FileTreeCellRenderer
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.SwingConstants
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

/**
 * Included Files panel for Listing Generator Tool Window.
 *
 * Displays the list of files included in the selected section after applying filters.
 * Supports two view modes:
 * - Tree: hierarchical folder structure
 * - Flat: all files in flat list with full paths
 */
class LgIncludedFilesPanel(
    private val project: Project
) : SimpleToolWindowPanel(
    true,   // vertical = true (toolbar at top)
    true    // borderless = true
), UiDataProvider {
    
    private val workspaceState = project.service<LgWorkspaceStateService>()
    
    // Tree components
    private val rootNode = DefaultMutableTreeNode("Root")
    private val treeModel = DefaultTreeModel(rootNode)
    private val tree = Tree(treeModel)
    
    // Current file paths
    private var currentPaths: List<String> = emptyList()
    
    // Empty state label
    private val emptyLabel = JBLabel(
        "<html><center>${LgBundle.message("included.empty.text")}</center></html>",
        SwingConstants.CENTER
    ).apply {
        foreground = com.intellij.util.ui.UIUtil.getInactiveTextColor()
    }
    
    init {
        setupTree()
        showEmptyState()
    }
    
    private fun setupTree() {
        tree.isRootVisible = false
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        tree.cellRenderer = FileTreeCellRenderer()
        
        // Double-click to open file + context menu
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    openSelectedFile()
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                if (e.isPopupTrigger) showContextMenu(e)
            }
        })
        
        // Speed Search (type to filter)
        TreeUIHelper.getInstance().installTreeSpeedSearch(tree, { treePath ->
            val node = treePath.lastPathComponent as? DefaultMutableTreeNode
            when (val obj = node?.userObject) {
                is FlatFileItem -> obj.path
                is VirtualFile -> obj.name
                is String -> obj
                else -> obj?.toString() ?: ""
            }
        }, true)
    }
    
    /**
     * Sets the list of file paths to display.
     * Called from LgShowIncludedFilesAction after loading data.
     */
    fun setPaths(paths: List<String>) {
        currentPaths = paths
        
        ApplicationManager.getApplication().invokeLater {
            if (paths.isEmpty()) {
                showEmptyState()
            } else {
                rebuildTree()
                showTree()
            }
        }
    }
    
    /**
     * Rebuilds tree based on current view mode.
     * Called when view mode is toggled or paths are updated.
     */
    fun rebuildTree() {
        ApplicationManager.getApplication().invokeLater {
            when (workspaceState.state.includedFilesViewMode) {
                LgWorkspaceStateService.ViewMode.FLAT -> buildFlatTree()
                LgWorkspaceStateService.ViewMode.TREE -> buildHierarchicalTree()
            }
        }
    }
    
    private fun buildFlatTree() {
        rootNode.removeAllChildren()
        
        for (path in currentPaths.sorted()) {
            val file = findVirtualFile(path)
            // В flat режиме сохраняем путь как userObject для отображения
            val node = DefaultMutableTreeNode(FlatFileItem(file, path))
            rootNode.add(node)
        }
        
        treeModel.nodeStructureChanged(rootNode)
        LOG.debug("Built flat tree with ${currentPaths.size} files")
    }
    
    private fun buildHierarchicalTree() {
        rootNode.removeAllChildren()
        
        // Build file hierarchy
        val fileTree = buildFileHierarchy(currentPaths)
        
        // Add nodes to tree
        addNodesToTree(rootNode, fileTree)
        
        treeModel.nodeStructureChanged(rootNode)
        
        // Expand first level
        if (rootNode.childCount > 0) {
            tree.expandRow(0)
        }
        
        LOG.debug("Built hierarchical tree with ${currentPaths.size} files")
    }
    
    private fun buildFileHierarchy(paths: List<String>): FileNode {
        val root = FileNode("Root", null)
        
        for (path in paths) {
            val parts = path.split('/').filter { it.isNotBlank() }
            insertPath(root, parts, path)
        }
        
        return root
    }
    
    private fun insertPath(parent: FileNode, parts: List<String>, fullPath: String) {
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
    
    private fun addNodesToTree(parent: DefaultMutableTreeNode, fileNode: FileNode) {
        // Sort: directories first, then files
        val sortedChildren = fileNode.children.sortedWith(
            compareBy<FileNode> { it.file != null }.thenBy { it.name }
        )
        
        for (child in sortedChildren) {
            val node = if (child.file != null) {
                DefaultMutableTreeNode(child.file)
            } else {
                DefaultMutableTreeNode(child.name)
            }
            
            parent.add(node)
            
            if (child.children.isNotEmpty()) {
                addNodesToTree(node, child)
            }
        }
    }
    
    private fun findVirtualFile(path: String): VirtualFile? {
        val basePath = project.basePath ?: return null
        val fullPath = "$basePath/$path"
        return LocalFileSystem.getInstance().findFileByPath(fullPath)
    }
    
    private fun openSelectedFile() {
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
        
        val file = when (val obj = node.userObject) {
            is VirtualFile -> obj
            is FlatFileItem -> obj.file
            else -> null
        }
        
        if (file != null && !file.isDirectory) {
            FileEditorManager.getInstance(project).openFile(file, true)
        }
    }
    
    private fun showContextMenu(e: MouseEvent) {
        // Select node under cursor
        val path = tree.getPathForLocation(e.x, e.y) ?: return
        tree.selectionPath = path
        
        // Use standard platform context menu for files
        val actionGroup = ActionManager.getInstance()
            .getAction("ProjectViewPopupMenu") as? ActionGroup
            ?: return
        
        val popupMenu = ActionManager.getInstance()
            .createActionPopupMenu(ActionPlaces.PROJECT_VIEW_POPUP, actionGroup)
        
        popupMenu.setTargetComponent(tree)
        popupMenu.component.show(e.component, e.x, e.y)
    }
    
    override fun uiDataSnapshot(sink: DataSink) {
        // Provide selected file data for context menu actions
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
        val file = when (val obj = node?.userObject) {
            is VirtualFile -> obj
            is FlatFileItem -> obj.file
            is String -> {
                // Directory node - need to resolve VirtualFile
                findVirtualFileForDirectory(node)
            }
            else -> null
        }
        
        sink[CommonDataKeys.PROJECT] = project
        if (file != null) {
            sink[CommonDataKeys.VIRTUAL_FILE] = file
            sink[CommonDataKeys.VIRTUAL_FILE_ARRAY] = arrayOf(file)
        }
    }
    
    private fun findVirtualFileForDirectory(dirNode: DefaultMutableTreeNode): VirtualFile? {
        // Build full path from tree hierarchy
        val pathSegments = mutableListOf<String>()
        var currentNode: Any? = dirNode
        
        while (currentNode is DefaultMutableTreeNode) {
            if (currentNode == rootNode) break
            
            val name = currentNode.userObject as? String
            if (name != null) {
                pathSegments.add(0, name)
            }
            
            currentNode = currentNode.parent
        }
        
        if (pathSegments.isEmpty()) return null
        
        // Resolve path
        val basePath = project.basePath ?: return null
        val fullPath = "$basePath/${pathSegments.joinToString("/")}"
        val resolved = LocalFileSystem.getInstance().findFileByPath(fullPath)

        return resolved
    }
    
    private fun showEmptyState() {
        setContent(emptyLabel)
    }
    
    private fun showTree() {
        setContent(JBScrollPane(tree))
    }
    
    /**
     * Internal data structure for building hierarchical tree.
     */
    private data class FileNode(
        val name: String,
        val file: VirtualFile?,
        val children: MutableList<FileNode> = mutableListOf()
    )
    
    /**
     * Wrapper for flat mode items to preserve full path.
     */
    data class FlatFileItem(
        val file: VirtualFile?,
        val path: String
    )
    
    companion object {
        private val LOG = logger<LgIncludedFilesPanel>()
    }
}
