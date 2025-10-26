package lg.intellij.services.ai.base

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lg.intellij.services.ai.AiProvider
import lg.intellij.services.ai.AiProviderException

/**
 * Базовый класс для Extension-based AI провайдеров.
 * 
 * Используется для провайдеров, которые работают через другие плагины IntelliJ Platform
 * (например, JetBrains AI Assistant, GitHub Copilot).
 * 
 * Основные возможности:
 * - Проверка наличия и активности плагина
 * - Автоматическая активация tool window
 * - Получение текущего проекта
 * - Обработка ошибок отсутствия плагина
 */
abstract class BaseExtensionProvider : AiProvider {
    
    private val LOG = logger<BaseExtensionProvider>()
    
    /**
     * ID плагина, с которым интегрируется данный провайдер.
     * Например, "com.intellij.ml.llm" для JetBrains AI.
     */
    protected abstract val pluginId: String
    
    /**
     * ID tool window для автоматической активации.
     * Например, "AIAssistant" для JetBrains AI.
     */
    protected abstract val toolWindowId: String
    
    /**
     * Проверяет наличие и активность плагина.
     */
    override suspend fun isAvailable(): Boolean {
        return try {
            val plugin = PluginManager.getInstance().findEnabledPlugin(PluginId.getId(pluginId))
            plugin != null
        } catch (e: Exception) {
            LOG.debug("Plugin '$pluginId' not found or not enabled", e)
            false
        }
    }
    
    /**
     * Отправляет контент через плагин.
     * 
     * Проверяет доступность плагина, активирует tool window и делегирует sendToExtension.
     */
    override suspend fun send(content: String) {
        if (!isAvailable()) {
            throw AiProviderException(
                "$name is not available. Please install and enable the plugin: $pluginId"
            )
        }
        
        val project = getCurrentProject()
        
        // Активировать tool window
        openToolWindow(project)
        
        // Отправить контент
        sendToExtension(project, content)
    }
    
    /**
     * Получает текущий активный проект.
     */
    protected fun getCurrentProject(): Project {
        val openProjects = ProjectManager.getInstance().openProjects
        return openProjects.first()
    }
    
    /**
     * Открывает tool window провайдера.
     */
    private suspend fun openToolWindow(project: Project) = withContext(Dispatchers.EDT) {
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow(toolWindowId)
        
        if (toolWindow != null) {
            LOG.debug("Opening tool window: $toolWindowId")
            toolWindow.activate(null)
        } else {
            LOG.warn("Tool window not found: $toolWindowId")
        }
    }
    
    /**
     * Отправляет контент в конкретный плагин.
     * 
     * Реализуется наследниками для специфичной логики взаимодействия с плагином.
     * 
     * @param project Текущий проект
     * @param content Контент для отправки
     */
    protected abstract suspend fun sendToExtension(project: Project, content: String)
}

