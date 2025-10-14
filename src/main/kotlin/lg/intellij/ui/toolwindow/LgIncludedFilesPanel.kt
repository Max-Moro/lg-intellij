package lg.intellij.ui.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import lg.intellij.LgBundle
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.SwingConstants

/**
 * Included Files panel for Listing Generator Tool Window.
 * 
 * Displays the list of files included in the selected section
 * after applying filters. Supports tree and flat view modes.
 * 
 * Phase 3: Placeholder implementation with header and "Coming Soon" label.
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
    
    private fun createPlaceholderContent(): JBPanel<*> {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
            
            // Header with section name
            val header = JBLabel(LgBundle.message("toolwindow.included.tab")).apply {
                font = font.deriveFont(Font.BOLD)
                border = BorderFactory.createEmptyBorder(0, 0, 4, 0)
            }
            
            // Placeholder content
            val content = JBLabel(
                "Coming Soon",
                SwingConstants.CENTER
            ).apply {
                // Simple placeholder for Phase 3
                // Full tree view with toggle modes will be added in Phase 11
            }
            
            add(header, BorderLayout.NORTH)
            add(content, BorderLayout.CENTER)
        }
    }
}
