package lg.intellij.toolWindow

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lg.intellij.LgBundle
import lg.intellij.ui.toolwindow.LgControlPanel
import lg.intellij.ui.toolwindow.LgIncludedFilesPanel
import java.nio.file.Files
import kotlin.io.path.Path

/**
 * Factory for creating Listing Generator Tool Window.
 * 
 * Creates a Tool Window with two tabs:
 * - Control Panel: main configuration and generation controls
 * - Included Files: tree view of files included in selected section
 * 
 * Tool Window is shown only for projects containing lg-cfg/ directory.
 */
class LgToolWindowFactory : ToolWindowFactory, DumbAware {
    
    private val LOG = thisLogger()
    
    init {
        LOG.info("LgToolWindowFactory initialized")
    }
    
    /**
     * Checks if Tool Window should be available for this project.
     * 
     * Tool Window is shown only if lg-cfg/ directory exists in project root.
     */
    override suspend fun isApplicableAsync(project: Project): Boolean {
        return withContext(Dispatchers.IO) {
            val basePath = project.basePath ?: return@withContext false
            val lgCfgDir = Path(basePath, "lg-cfg")
            Files.exists(lgCfgDir)
        }
    }
    
    /**
     * Creates Tool Window content with two tabs.
     */
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentManager = toolWindow.contentManager
        val contentFactory = ContentFactory.getInstance()
        
        // Tab 1: Control Panel
        val controlPanel = LgControlPanel(project, toolWindow)
        val controlContent = contentFactory.createContent(
            controlPanel,
            LgBundle.message("toolwindow.control.tab"),
            false
        )
        controlContent.isCloseable = false
        contentManager.addContent(controlContent)
        
        // Tab 2: Included Files
        val includedFilesPanel = LgIncludedFilesPanel(project, toolWindow)
        val includedContent = contentFactory.createContent(
            includedFilesPanel,
            LgBundle.message("toolwindow.included.tab"),
            false
        )
        includedContent.isCloseable = false
        contentManager.addContent(includedContent)
        
        LOG.info("Tool Window content created for project: ${project.name}")
    }
    
    /**
     * Initializes Tool Window properties.
     */
    override fun init(toolWindow: ToolWindow) {
        // Set stripe title (short name for tool window button)
        toolWindow.stripeTitle = LgBundle.message("toolwindow.stripe.title")
        
        LOG.debug("Tool Window initialized: ${toolWindow.id}")
    }
}
