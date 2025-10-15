package lg.intellij.ui.components

import javax.swing.JPanel
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager

/**
 * Panel with automatic horizontal wrapping of components.
 * 
 * Why this component was created:
 * - Standard `FlowLayout` doesn't correctly recalculate height during dynamic resizing,
 *   causing components to disappear or get clipped at the bottom (1-2px border loss)
 * - `GridBagLayout` (used by Kotlin UI DSL) doesn't support automatic wrapping
 * - IntelliJ Platform's `HorizontalLayout` and `VerticalLayout` don't support wrapping
 * - We need adaptive layout similar to CSS flexbox with `flex-wrap: wrap` for Control Panel sections
 * 
 * Behavior:
 * - Components are laid out horizontally with specified gaps
 * - When a component doesn't fit in current row width, it wraps to next row
 * - All rows are left-aligned
 * - Each row height adjusts to tallest component in that row
 * - Panel height automatically adjusts based on number of wrapped rows
 * 
 * Usage:
 * ```kotlin
 * val panel = LgWrappingPanel(hgap = 4, vgap = 4).apply {
 *     add(comboBox)
 *     add(button1)
 *     add(button2)
 * }
 * ```
 * 
 * @param hgap Horizontal gap between components (default 4px)
 * @param vgap Vertical gap between rows (default 4px)
 */
class LgWrappingPanel(
    hgap: Int = 4,
    vgap: Int = 4
) : JPanel(WrapLayout(hgap, vgap)) {
    
    init {
        isOpaque = false
    }
    
    /**
     * Custom LayoutManager that implements proper wrapping behavior.
     * 
     * Unlike `FlowLayout`, this layout:
     * - Always uses exact component preferred sizes (no clipping)
     * - Correctly calculates wrapped layout height in `preferredLayoutSize`
     * - Immediately wraps components when they don't fit (no intermediate "disappearing" state)
     */
    private class WrapLayout(
        private val hgap: Int,
        private val vgap: Int
    ) : LayoutManager {
        
        override fun addLayoutComponent(name: String?, comp: Component?) {}
        override fun removeLayoutComponent(comp: Component?) {}
        
        override fun preferredLayoutSize(parent: Container): Dimension {
            return layoutSize(parent, preferred = true)
        }
        
        override fun minimumLayoutSize(parent: Container): Dimension {
            return layoutSize(parent, preferred = false)
        }
        
        /**
         * Positions all visible components with wrapping.
         * 
         * Algorithm:
         * 1. Start at top-left (considering insets)
         * 2. For each component:
         *    - If it doesn't fit in current row → wrap to next row
         *    - Position component at current (x, y)
         *    - Advance x by component width + hgap
         *    - Track max height in current row
         * 3. After row wraps: reset x, advance y by row height + vgap
         */
        override fun layoutContainer(parent: Container) {
            val insets = parent.insets
            val maxWidth = parent.width - insets.left - insets.right
            
            var x = insets.left
            var y = insets.top
            var rowHeight = 0
            
            for (comp in parent.components) {
                if (!comp.isVisible) continue
                
                val d = comp.preferredSize
                
                // Wrap to next row if component doesn't fit
                // (but not if it's first component in row)
                if (x != insets.left && x + d.width > maxWidth + insets.left) {
                    x = insets.left
                    y += rowHeight + vgap
                    rowHeight = 0
                }
                
                // Position component
                comp.setBounds(x, y, d.width, d.height)
                
                // Advance position
                x += d.width + hgap
                rowHeight = maxOf(rowHeight, d.height)
            }
        }
        
        /**
         * Calculates panel size needed to fit all components with wrapping.
         * 
         * This method simulates layout algorithm to determine how many rows
         * will be needed and what the total height will be.
         */
        private fun layoutSize(parent: Container, preferred: Boolean): Dimension {
            val insets = parent.insets
            val maxWidth = parent.width - insets.left - insets.right
            
            var x = 0
            var y = insets.top
            var rowHeight = 0
            var maxRowWidth = 0
            
            for (comp in parent.components) {
                if (!comp.isVisible) continue
                
                val d = if (preferred) comp.preferredSize else comp.minimumSize
                
                // Simulate wrapping
                if (x != 0 && x + d.width > maxWidth) {
                    maxRowWidth = maxOf(maxRowWidth, x - hgap)
                    x = 0
                    y += rowHeight + vgap
                    rowHeight = 0
                }
                
                x += d.width + hgap
                rowHeight = maxOf(rowHeight, d.height)
            }
            
            // Finalize dimensions
            maxRowWidth = maxOf(maxRowWidth, x - hgap)
            y += rowHeight
            
            return Dimension(
                insets.left + maxRowWidth + insets.right,
                y + insets.bottom
            )
        }
    }
}

