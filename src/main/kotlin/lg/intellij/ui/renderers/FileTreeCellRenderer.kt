package lg.intellij.ui.renderers

import com.intellij.icons.AllIcons
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import lg.intellij.ui.toolwindow.LgIncludedFilesPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

/**
 * Custom tree cell renderer for Included Files tree.
 *
 * Renders:
 * - Files: file type icon + name (tree mode) or full path (flat mode)
 * - Directories: folder icon (open/closed) + name (bold)
 */
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
        val node = value as? DefaultMutableTreeNode ?: return
        
        when (val userObject = node.userObject) {
            is LgIncludedFilesPanel.FlatFileItem -> {
                // Flat mode: show full path
                icon = userObject.file?.fileType?.icon ?: AllIcons.FileTypes.Any_type
                append(userObject.path, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }
            
            is VirtualFile -> {
                // Tree mode: show only name
                icon = userObject.fileType.icon
                append(userObject.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }
            
            is String -> {
                // Directory node (represented by String name)
                icon = AllIcons.Nodes.Folder
                append(userObject, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }
            
            else -> {
                // Fallback for unknown node types
                append(userObject?.toString() ?: "", SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }
        }
    }
}

