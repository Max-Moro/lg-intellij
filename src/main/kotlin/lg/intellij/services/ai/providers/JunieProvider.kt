package lg.intellij.services.ai.providers

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lg.intellij.models.AiInteractionMode
import lg.intellij.services.ai.base.BaseExtensionProvider

/**
 * Provider for Junie integration, the AI coding agent by JetBrains.
 *
 * Uses reflection to work with Junie API to avoid
 * compile-time dependency (plugin is optional).
 *
 * Features:
 * - Creates a new task chain
 * - Sends generated content as a task description
 * - Uses IssueType.ISSUE for formal tasks
 *
 * Priority: 70 (lower than JetBrains AI and GitHub Copilot, but higher than CLI)
 */
class JunieProvider : BaseExtensionProvider() {
    
    private val log = logger<JunieProvider>()
    
    override val id = "jetbrains.junie"
    override val name = "Junie (JetBrains AI Agent)"
    override val priority = 70
    override val pluginId = "org.jetbrains.junie"
    override val toolWindowId = "ElectroJunToolWindow"

    override suspend fun sendToExtension(
        project: Project,
        content: String,
        mode: AiInteractionMode
    ) {
        log.info("Sending content to Junie in ${mode.name} mode")

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
            
            // Create ExplicitTaskContext with mode
            val taskContext = createTaskContext(content, classLoader, mode)
            
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
                    
                    log.info("Successfully started Junie task")
                } catch (e: Exception) {
                    continuation.resumeWith(Result.failure(e))
                }
            }
        }
    }
    
    /**
     * Creates TaskChainId via reflection.
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
     * Creates ExplicitTaskContext via reflection.
     *
     * Mode mapping:
     * - ASK → IssueType.CHAT (free-form dialogue)
     * - AGENT → IssueType.ISSUE (formal task with tools)
     */
    private fun createTaskContext(
        description: String,
        classLoader: ClassLoader,
        mode: AiInteractionMode
    ): Any {
        val explicitTaskContextClass = Class.forName(
            "com.intellij.ml.llm.matterhorn.ej.api.ExplicitTaskContext",
            true,
            classLoader
        )
        
        // Get IssueType based on mode
        val issueTypeClass = Class.forName(
            "com.intellij.ml.llm.matterhorn.ej.api.IssueType",
            true,
            classLoader
        )
        val issueTypeFieldName = when (mode) {
            AiInteractionMode.ASK -> "CHAT"
            AiInteractionMode.AGENT -> "ISSUE"
        }
        val issueTypeField = issueTypeClass.getDeclaredField(issueTypeFieldName)
        val issueType = issueTypeField.get(null)
        
        log.debug("Creating TaskContext with IssueType.$issueTypeFieldName")
        
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

