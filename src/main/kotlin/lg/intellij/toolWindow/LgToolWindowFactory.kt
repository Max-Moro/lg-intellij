package lg.intellij.toolWindow

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.util.Disposer
import com.intellij.ui.OnePixelSplitter
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
 * Creates a Tool Window with vertical splitter layout:
 * - Top: Control Panel (main configuration and generation controls)
 * - Bottom: Included Files (tree view of files, collapsible)
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
     * Creates Tool Window content with vertical splitter.
     */
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentManager = toolWindow.contentManager
        val contentFactory = ContentFactory.getInstance()
        
        // Create panels
        val controlPanel = LgControlPanel(project)
        val includedFilesPanel = LgIncludedFilesPanel(project, toolWindow)
        
        // Create vertical splitter
        // OnePixelSplitter(vertical = true) creates horizontal divider (splits vertically)
        val splitter = OnePixelSplitter(true, 0.7f).apply {
            firstComponent = controlPanel
            secondComponent = includedFilesPanel
            
            // Allow collapsing the bottom panel
            setResizeEnabled(true)
            setShowDividerControls(true)
            
            // TODO Phase 6: Load proportion from LgWorkspaceStateService
            // TODO Phase 6: Save proportion changes to state
        }
        
        // Create single content with splitter
        val content = contentFactory.createContent(
            splitter,
            null, // No tab name needed
            false
        )
        content.isCloseable = false
        
        // Register control panel as disposable with content as parent
        Disposer.register(content, controlPanel)
        
        contentManager.addContent(content)
        
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
