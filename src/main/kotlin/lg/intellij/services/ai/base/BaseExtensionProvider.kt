package lg.intellij.services.ai.base

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
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
 * - Автоматическая активация плагина при необходимости
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
     * Проверяет доступность плагина и делегирует sendToExtension.
     */
    override suspend fun send(content: String) {
        if (!isAvailable()) {
            throw AiProviderException(
                "$name is not available. Please install and enable the plugin: $pluginId"
            )
        }
        
        sendToExtension(content)
    }
    
    /**
     * Отправляет контент в конкретный плагин.
     * 
     * Реализуется наследниками для специфичной логики взаимодействия с плагином.
     * 
     * @param content Контент для отправки
     */
    protected abstract suspend fun sendToExtension(content: String)
}

