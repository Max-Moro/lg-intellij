package lg.intellij.ui.dialogs

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import lg.intellij.LgBundle
import java.awt.datatransfer.StringSelection
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JTextArea

/**
 * Temporary dialog for previewing generated content.
 * 
 * Phase 7: Simple modal dialog with JTextArea.
 * Phase 8: TODO - Replace with LightVirtualFile display in editor.
 * 
 * Features:
 * - Read-only text area with monospace font
 * - Scrollable
 * - Copy to Clipboard action
 * - Resizable
 */
class OutputPreviewDialog(
    private val project: Project?,
    private val content: String,
    title: String
) : DialogWrapper(project) {
    
    init {
        this.title = title
        isResizable = true
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        val textArea = JTextArea(content).apply {
            isEditable = false
            lineWrap = false
            font = java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12)
            
            // Auto-size based on content (with reasonable limits)
            val lines = content.lines()
            val maxLineLength = lines.maxOfOrNull { it.length } ?: 80
            val lineCount = minOf(lines.size, 40) // Max 40 lines visible
            
            rows = lineCount
            columns = minOf(maxLineLength, 120) // Max 120 chars width
        }
        
        return JBScrollPane(textArea).apply {
            preferredSize = java.awt.Dimension(800, 600)
        }
    }
    
    override fun createActions(): Array<Action> {
        return arrayOf(
            copyToClipboardAction,
            okAction
        )
    }
    
    private val copyToClipboardAction = object : DialogWrapperAction(
        LgBundle.message("dialog.output.action.copy")
    ) {
        override fun doAction(e: java.awt.event.ActionEvent) {
            CopyPasteManager.getInstance().setContents(StringSelection(content))
            
            // Optional: Show brief notification
            // (keeping it simple for Phase 7)
        }
    }
}

