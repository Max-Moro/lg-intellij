package lg.intellij.ui.components

import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import com.intellij.ui.components.JBLabel

/**
 * Utility for creating non-breakable label+component pairs.
 * 
 * Label and component stay together when wrapping (useful in flow/wrap layouts).
 */
object LgLabeledComponent {
    
    /**
     * Creates a panel with label on the left and component on the right.
     * 
     * @param labelText Label text
     * @param component Component to label
     * @param gap Horizontal gap between label and component (default 2px)
     * @return Panel containing label and component
     */
    fun create(
        labelText: String,
        component: JComponent,
        gap: Int = 2
    ): JPanel {
        return JPanel(BorderLayout(gap, 0)).apply {
            isOpaque = false
            add(JBLabel(labelText), BorderLayout.WEST)
            add(component, BorderLayout.CENTER)
        }
    }
}

