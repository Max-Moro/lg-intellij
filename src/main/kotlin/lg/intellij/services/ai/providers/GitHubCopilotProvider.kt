package lg.intellij.services.ai.providers

import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import lg.intellij.models.AiInteractionMode
import lg.intellij.services.ai.base.BaseExtensionProvider
import kotlin.jvm.functions.Function1

/**
 * Provider for GitHub Copilot integration.
 *
 * Uses reflection to work with GitHub Copilot API to avoid
 * compile-time dependency (plugin is optional).
 *
 * Features:
 * - Opens a new chat session
 * - Sends generated content as a request
 * - Uses "Ask" mode (question-answer)
 *
 * Priority: 80 (high, but lower than JetBrains AI)
 */
class GitHubCopilotProvider : BaseExtensionProvider() {
    
    private val log = logger<GitHubCopilotProvider>()
    
    override val id = "github.copilot"
    override val name = "GitHub Copilot"
    override val priority = 80
    override val pluginId = "com.github.copilot"
    override val toolWindowId = "GitHub Copilot Chat"

    override suspend fun sendToExtension(
        project: Project,
        content: String,
        mode: AiInteractionMode
    ) {
        log.info("Sending content to GitHub Copilot in ${mode.name} mode")

        val classLoader = this::class.java.classLoader

        // Get CopilotChatService
        val chatServiceClass = Class.forName(
            "com.github.copilot.api.CopilotChatService",
            true,
            classLoader
        )
        
        @Suppress("IncorrectServiceRetrieving")
        val chatService = project.getService(chatServiceClass)
        
        // Create DataContext (empty, can be extended if needed)
        val dataContext = SimpleDataContext.builder().build()
        
        // Create QueryOptionBuilder lambda with mode
        val builderLambda = createQueryOptionBuilderLambda(content, classLoader, mode)
        
        // Call query method
        val queryMethod = chatServiceClass.getMethod(
            "query",
            Class.forName("com.intellij.openapi.actionSystem.DataContext", true, classLoader),
            Class.forName("kotlin.jvm.functions.Function1", true, classLoader)
        )
        
        queryMethod.invoke(chatService, dataContext, builderLambda)
        
        log.info("Successfully sent to GitHub Copilot")
    }
    
    /**
     * Creates a lambda for QueryOptionBuilder with settings.
     */
    private fun createQueryOptionBuilderLambda(
        content: String,
        classLoader: ClassLoader,
        mode: AiInteractionMode
    ): Any {
        val builderClass = Class.forName(
            "com.github.copilot.api.QueryOptionBuilder",
            true,
            classLoader
        )
        
        return object : Function1<Any, Unit> {
            override fun invoke(p1: Any) {
                configureBuilder(p1, content, builderClass, mode)
                return
            }
        }
    }
    
    /**
     * Configures QueryOptionBuilder through reflection.
     *
     * Mode mapping:
     * - ASK → withAskMode()
     * - AGENT → withAgentMode()
     */
    private fun configureBuilder(
        builder: Any,
        content: String,
        builderClass: Class<*>,
        mode: AiInteractionMode
    ) {
        // withInput(content)
        val withInputMethod = builderClass.getMethod("withInput", String::class.java)
        withInputMethod.invoke(builder, content)

        // withNewSession() - create a new session for each request
        val withNewSessionMethod = builderClass.getMethod("withNewSession")
        withNewSessionMethod.invoke(builder)

        // hideWelcomeMessage() - hide welcome message
        val hideWelcomeMethod = builderClass.getMethod("hideWelcomeMessage")
        hideWelcomeMethod.invoke(builder)
        
        // withAgentMode() or withAskMode()
        val (modeMethod, modeName) = when (mode) {
            AiInteractionMode.ASK -> builderClass.getMethod("withAskMode") to "askMode"
            AiInteractionMode.AGENT -> builderClass.getMethod("withAgentMode") to "agentMode"
        }
        modeMethod.invoke(builder)
        
        log.debug("Configured QueryOptionBuilder: newSession, hideWelcome, $modeName")
    }
}

