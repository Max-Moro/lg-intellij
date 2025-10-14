package lg.intellij.ui.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import javax.swing.JLabel
import javax.swing.SwingConstants

/**
 * Included Files panel for Listing Generator Tool Window.
 * 
 * Displays the list of files included in the selected section
 * after applying filters. Supports tree and flat view modes.
 * 
 * Phase 3: Placeholder implementation with "Coming Soon" label.
 * Full tree view implementation will be added in Phase 11.
 */
class LgIncludedFilesPanel(
    private val project: Project,
    private val toolWindow: ToolWindow
) : SimpleToolWindowPanel(
    true,   // vertical = true (toolbar at top)
    true    // borderless = true
) {
    
    init {
        setContent(createPlaceholderContent())
    }
    
    private fun createPlaceholderContent(): JLabel {
        return JLabel(
            "Included Files — Coming Soon",
            SwingConstants.CENTER
        ).apply {
            // Simple placeholder for Phase 3
            // Full tree view with toggle modes will be added in Phase 11
        }
    }
}
