package lg.intellij.services.ai.providers

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lg.intellij.services.ai.base.BaseExtensionProvider

/**
 * Провайдер для интеграции с Junie, the AI coding agent by JetBrains.
 * 
 * Использует рефлексию для работы с Junie API, чтобы избежать
 * compile-time зависимости (плагин опциональный).
 * 
 * Функциональность:
 * - Создаёт новую task chain
 * - Отправляет сгенерированный контент как task description
 * - Использует IssueType.ISSUE для формальных задач
 * 
 * Priority: 70 (ниже чем JetBrains AI и GitHub Copilot, но выше CLI)
 */
class JunieProvider : BaseExtensionProvider() {
    
    private val LOG = logger<JunieProvider>()
    
    override val id = "jetbrains.junie"
    override val name = "Junie (JetBrains AI Agent)"
    override val priority = 70
    override val pluginId = "org.jetbrains.junie"
    override val toolWindowId = "ElectroJunToolWindow"

    override suspend fun sendToExtension(project: Project, content: String) {
        LOG.info("Sending content to Junie")

        withContext(Dispatchers.Default) {
            // Get TaskService interface via implementation class (avoids content module restriction)
            val implClass = Class.forName("com.intellij.ml.llm.matterhorn.ej.ui.tasks.TaskServiceImpl")
            val taskServiceInterface = implClass.interfaces.first { it.simpleName == "TaskService" }
            val classLoader = taskServiceInterface.classLoader
            
            val companionField = taskServiceInterface.getDeclaredField("Companion")
            val companion = companionField.get(null)
            val getInstanceMethod = companion::class.java.getMethod("getInstance", Project::class.java)
            
            val taskService = getInstanceMethod.invoke(companion, project)
            
            // Create TaskChainId
            val taskChainId = createTaskChainId(classLoader)
            
            // Create ExplicitTaskContext (with configurable IssueType)
            val taskContext = createTaskContext(content, classLoader, useIssueMode  = true)
            
            // Call start method (suspend function)
            kotlinx.coroutines.suspendCancellableCoroutine<Any> { continuation ->
                try {
                    // Find start method with Continuation parameter
                    val startMethod = taskServiceInterface.declaredMethods.first {
                        it.name == "start" && it.parameterCount == 4
                    }
                    
                    // Call: start(chainId, context, previousTasksInfo = null, continuation)
                    startMethod.invoke(
                        taskService,
                        taskChainId,
                        taskContext,
                        null, // previousTasksInfo
                        continuation
                    )
                    
                    LOG.info("Successfully started Junie task")
                } catch (e: Exception) {
                    continuation.resumeWith(Result.failure(e))
                }
            }
        }
    }
    
    /**
     * Создаёт TaskChainId через рефлексию.
     */
    private fun createTaskChainId(classLoader: ClassLoader): Any {
        val taskChainIdClass = Class.forName(
            "com.intellij.ml.llm.matterhorn.junie.core.shared.tasks.TaskChainId",
            true,
            classLoader
        )
        
        // Get Companion.new()
        val companionField = taskChainIdClass.getDeclaredField("Companion")
        val companion = companionField.get(null)
        val newMethod = companion::class.java.getMethod("new")
        
        return newMethod.invoke(companion)
    }
    
    /**
     * Создаёт ExplicitTaskContext через рефлексию.
     * 
     * @param useIssueMode If true → IssueType.ISSUE (formal task), if false → IssueType.CHAT (free dialog)
     */
    private fun createTaskContext(
        description: String,
        classLoader: ClassLoader,
        useIssueMode: Boolean
    ): Any {
        val explicitTaskContextClass = Class.forName(
            "com.intellij.ml.llm.matterhorn.ej.api.ExplicitTaskContext",
            true,
            classLoader
        )
        
        // Get IssueType (ISSUE or CHAT)
        val issueTypeClass = Class.forName(
            "com.intellij.ml.llm.matterhorn.ej.api.IssueType",
            true,
            classLoader
        )
        val issueTypeFieldName = if (useIssueMode) "ISSUE" else "CHAT"
        val issueTypeField = issueTypeClass.getDeclaredField(issueTypeFieldName)
        val issueType = issueTypeField.get(null)
        
        LOG.debug("Creating TaskContext with IssueType.$issueTypeFieldName")
        
        // Create ExplicitTaskContext(type = IssueType, description = content, explicitlySelectedContextFiles = emptyList())
        val constructor = explicitTaskContextClass.getConstructor(
            issueTypeClass,
            String::class.java,
            List::class.java
        )
        
        return constructor.newInstance(
            issueType,
            description,
            emptyList<Any>() // explicitlySelectedContextFiles
        )
    }
}

