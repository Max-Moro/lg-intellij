package lg.intellij.ui.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.SwingConstants

/**
 * Control Panel for Listing Generator Tool Window.
 * 
 * This is the main panel where users configure sections, contexts,
 * tokenization settings, modes, tags, and trigger generation actions.
 * 
 * Phase 3: Placeholder implementation with "Coming Soon" label.
 * Full UI will be implemented in Phase 4.
 */
class LgControlPanel(
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
            "Control Panel — Coming Soon",
            SwingConstants.CENTER
        ).apply {
            // Simple placeholder for Phase 3
            // Full UI with Kotlin UI DSL will be added in Phase 4
        }
    }
}
