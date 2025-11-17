package lg.intellij.services.ai.base

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lg.intellij.models.AiInteractionMode
import lg.intellij.services.ai.AiProvider
import lg.intellij.services.ai.AiProviderException
import lg.intellij.services.state.LgPanelStateService

/**
 * Base class for Extension-based AI providers.
 *
 * Used for providers that work through other IntelliJ Platform plugins
 * (e.g., JetBrains AI Assistant, GitHub Copilot).
 *
 * Key features:
 * - Plugin presence and enablement checks
 * - Automatic tool window activation
 * - Current project retrieval
 * - Plugin absence error handling
 */
abstract class BaseExtensionProvider : AiProvider {
    
    private val log = logger<BaseExtensionProvider>()
    
    /**
     * ID of the plugin with which this provider integrates.
     * For example, "com.intellij.ml.llm" for JetBrains AI.
     */
    protected abstract val pluginId: String

    /**
     * ID of the tool window for automatic activation.
     * For example, "AIAssistant" for JetBrains AI.
     */
    protected abstract val toolWindowId: String

    /**
     * Checks plugin presence and enablement.
     */
    override suspend fun isAvailable(): Boolean {
        return try {
            val plugin = PluginManager.getInstance().findEnabledPlugin(PluginId.getId(pluginId))
            plugin != null
        } catch (e: Exception) {
            log.debug("Plugin '$pluginId' not found or not enabled", e)
            false
        }
    }
    
    /**
     * Sends content through the plugin.
     *
     * Checks plugin availability, activates tool window, determines interaction mode
     * and delegates to sendToExtension.
     */
    override suspend fun send(content: String) {
        if (!isAvailable()) {
            throw AiProviderException(
                "$name is not available. Please install and enable the plugin: $pluginId"
            )
        }

        val project = getCurrentProject()

        // Activate tool window
        openToolWindow(project)

        // Get AI interaction mode from panel state
        val panelState = project.service<LgPanelStateService>()
        val mode = panelState.getAiInteractionMode()

        // Send content with mode
        sendToExtension(project, content, mode)
    }
    
    /**
     * Gets the current active project.
     */
    protected fun getCurrentProject(): Project {
        val openProjects = ProjectManager.getInstance().openProjects
        return openProjects.first()
    }

    /**
     * Opens the provider's tool window.
     */
    private suspend fun openToolWindow(project: Project) = withContext(Dispatchers.EDT) {
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow(toolWindowId)

        if (toolWindow != null) {
            log.debug("Opening tool window: $toolWindowId")
            toolWindow.activate(null)
        } else {
            log.warn("Tool window not found: $toolWindowId")
        }
    }

    /**
     * Sends content to a specific plugin.
     *
     * Implemented by subclasses for plugin-specific interaction logic.
     *
     * @param project Current project
     * @param content Content to send
     * @param mode AI interaction mode (Ask or Agent)
     */
    protected abstract suspend fun sendToExtension(
        project: Project,
        content: String,
        mode: AiInteractionMode
    )
}

