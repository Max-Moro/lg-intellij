package lg.intellij.services.ai.providers

import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import lg.intellij.services.ai.base.BaseExtensionProvider
import kotlin.jvm.functions.Function1

/**
 * Провайдер для интеграции с GitHub Copilot.
 * 
 * Использует рефлексию для работы с GitHub Copilot API, чтобы избежать
 * compile-time зависимости (плагин опциональный).
 * 
 * Функциональность:
 * - Открывает новую чат-сессию
 * - Отправляет сгенерированный контент как запрос
 * - Использует режим "Ask" (вопрос-ответ)
 *
 * Priority: 80 (высокий, но ниже чем JetBrains AI)
 */
class GitHubCopilotProvider : BaseExtensionProvider() {
    
    private val LOG = logger<GitHubCopilotProvider>()
    
    override val id = "github.copilot"
    override val name = "GitHub Copilot"
    override val priority = 80
    override val pluginId = "com.github.copilot"

    /**
     * Получает текущий активный проект.
     */
    private fun getCurrentProject(): Project {
        val openProjects = ProjectManager.getInstance().openProjects
        return openProjects.first()
    }

    override suspend fun sendToExtension(content: String) {
        LOG.info("Sending content to GitHub Copilot")

        val classLoader = this::class.java.classLoader

        // Программно специально открывать "GitHub Copilot Chat" toolWindow не требуется
        // — это сделает сам CopilotChatService.

        // Get CopilotChatService
        val chatServiceClass = Class.forName(
            "com.github.copilot.api.CopilotChatService",
            true,
            classLoader
        )
        
        @Suppress("IncorrectServiceRetrieving")
        val chatService = getCurrentProject().getService(chatServiceClass)
        
        // Create DataContext (empty, можно расширить при необходимости)
        val dataContext = SimpleDataContext.builder().build()
        
        // Create QueryOptionBuilder lambda (default: Agent mode)
        val builderLambda = createQueryOptionBuilderLambda(content, classLoader, useAgentMode = true)
        
        // Call query method
        val queryMethod = chatServiceClass.getMethod(
            "query",
            Class.forName("com.intellij.openapi.actionSystem.DataContext", true, classLoader),
            Class.forName("kotlin.jvm.functions.Function1", true, classLoader)
        )
        
        queryMethod.invoke(chatService, dataContext, builderLambda)
        
        LOG.info("Successfully sent to GitHub Copilot")
    }
    
    /**
     * Создаёт лямбду для QueryOptionBuilder с настройками.
     */
    private fun createQueryOptionBuilderLambda(
        content: String,
        classLoader: ClassLoader,
        useAgentMode: Boolean
    ): Any {
        val builderClass = Class.forName(
            "com.github.copilot.api.QueryOptionBuilder",
            true,
            classLoader
        )
        
        return object : Function1<Any, Unit> {
            override fun invoke(p1: Any) {
                configureBuilder(p1, content, builderClass, useAgentMode)
                return
            }
        }
    }
    
    /**
     * Настраивает QueryOptionBuilder через рефлексию.
     */
    private fun configureBuilder(
        builder: Any,
        content: String,
        builderClass: Class<*>,
        useAgentMode: Boolean
    ) {
        // withInput(content)
        val withInputMethod = builderClass.getMethod("withInput", String::class.java)
        withInputMethod.invoke(builder, content)
        
        // withNewSession() - создаём новую сессию для каждого запроса
        val withNewSessionMethod = builderClass.getMethod("withNewSession")
        withNewSessionMethod.invoke(builder)
        
        // hideWelcomeMessage() - скрываем приветственное сообщение
        val hideWelcomeMethod = builderClass.getMethod("hideWelcomeMessage")
        hideWelcomeMethod.invoke(builder)
        
        // withAgentMode() or withAskMode()
        val modeMethod = if (useAgentMode) {
            builderClass.getMethod("withAgentMode")
        } else {
            builderClass.getMethod("withAskMode")
        }
        modeMethod.invoke(builder)
        
        val mode = if (useAgentMode) "agentMode" else "askMode"
        LOG.debug("Configured QueryOptionBuilder: newSession, hideWelcome, $mode")
    }
}

