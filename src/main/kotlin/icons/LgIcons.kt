package icons

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * Icon registry for Listing Generator plugin.
 * 
 * All icons are loaded lazily via IconLoader.
 */
object LgIcons {
    /**
     * Tool Window icon (13x13)
     */
    @JvmField
    val ToolWindow: Icon = IconLoader.getIcon("/icons/toolWindow.svg", LgIcons::class.java)
    
    // Action icons will be added in future phases when needed
    // For now using platform AllIcons as placeholders
}
