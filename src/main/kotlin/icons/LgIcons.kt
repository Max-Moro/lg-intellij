package icons

import com.intellij.openapi.util.IconLoader

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
    val ToolWindow = IconLoader.getIcon("/icons/toolWindow.svg", LgIcons::class.java)
}
