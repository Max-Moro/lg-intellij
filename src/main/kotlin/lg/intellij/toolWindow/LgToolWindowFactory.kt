package lg.intellij.toolWindow

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.util.Disposer
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.content.ContentFactory
import lg.intellij.LgBundle
import lg.intellij.bootstrap.shutdownCoordinator
import lg.intellij.services.state.LgWorkspaceStateService
import lg.intellij.ui.toolwindow.LgControlPanel
import lg.intellij.ui.toolwindow.LgIncludedFilesPanel
import java.beans.PropertyChangeListener

/**
 * Factory for creating Listing Generator Tool Window.
 * 
 * Creates a Tool Window with vertical splitter layout:
 * - Top: Control Panel (main configuration and generation controls)
 * - Bottom: Included Files (tree view of files, collapsible)
 * 
 * Tool Window is always available (even without lg-cfg/).
 * Users can create lg-cfg/ via "Create Starter Config" button in toolbar.
 */
class LgToolWindowFactory : ToolWindowFactory, DumbAware {
    
    private val log = thisLogger()
    
    init {
        log.info("LgToolWindowFactory initialized")
    }
    
    /**
     * Creates Tool Window content with vertical splitter.
     */
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentManager = toolWindow.contentManager
        val contentFactory = ContentFactory.getInstance()
        val workspaceState = project.service<LgWorkspaceStateService>()
        
        // Create panels
        val controlPanel = LgControlPanel(project)
        val includedFilesPanel = LgIncludedFilesPanel(project)
        
        // Restore saved splitter proportion (default 0.7 if not set)
        val savedProportion = workspaceState.state.splitterProportion
        val initialProportion = if (savedProportion > 0f) savedProportion else 0.7f
        
        // Create vertical splitter
        // OnePixelSplitter(vertical = true) creates horizontal divider (splits vertically)
        val splitter = OnePixelSplitter(true, initialProportion).apply {
            firstComponent = controlPanel
            secondComponent = includedFilesPanel
            
            // Allow collapsing the bottom panel
            setResizeEnabled(true)
            isShowDividerControls = true
            
            // Save proportion changes to workspace state
            addPropertyChangeListener(
                OnePixelSplitter.PROP_PROPORTION,
                PropertyChangeListener { evt ->
                    val newProportion = evt.newValue as? Float ?: return@PropertyChangeListener
                    workspaceState.state.splitterProportion = newProportion
                    log.debug("Splitter proportion saved: $newProportion")
                }
            )
        }
        
        log.debug("Splitter initialized with proportion: $initialProportion")
        
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

        // Cleanup coordinator when content is disposed
        Disposer.register(content, com.intellij.openapi.Disposable {
            shutdownCoordinator(project)
        })

        log.info("Tool Window content created for project: ${project.name}")
    }
    
    /**
     * Initializes Tool Window properties.
     */
    override fun init(toolWindow: ToolWindow) {
        // Set stripe title (short name for tool window button)
        toolWindow.stripeTitle = LgBundle.message("toolwindow.stripe.title")
        
        log.debug("Tool Window initialized: ${toolWindow.id}")
    }
}
