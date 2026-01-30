package lg.intellij.services.ai.providers

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lg.intellij.services.ai.ProviderModeInfo
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
 * - Supports CHAT (ask) and ISSUE (agent) types via runs
 *
 * Priority: 70 (lower than JetBrains AI and GitHub Copilot, but higher than CLI)
 */
class JunieProvider : BaseExtensionProvider() {

    private val log = logger<JunieProvider>()

    override val id = "org.jetbrains.junie.ext"
    override val name = "Junie (JetBrains AI Agent)"
    override val priority = 70
    override val pluginId = "org.jetbrains.junie"
    override val toolWindowId = "ElectroJunToolWindow"

    override suspend fun sendToExtension(
        project: Project,
        content: String,
        runs: String
    ) {
        log.info("Sending content to Junie (runs: $runs)")

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

            // Create ExplicitTaskContext based on runs
            val taskContext = createTaskContext(content, classLoader, runs)

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
     * runs is treated as opaque string containing IssueType enum field name.
     * If runs is empty or field not found, defaults to ISSUE.
     *
     * Example runs values:
     * - "CHAT" → IssueType.CHAT
     * - "ISSUE" → IssueType.ISSUE
     * - "SOME_NEW_TYPE" → IssueType.SOME_NEW_TYPE (forward compatible)
     */
    private fun createTaskContext(
        description: String,
        classLoader: ClassLoader,
        runs: String
    ): Any {
        val explicitTaskContextClass = Class.forName(
            "com.intellij.ml.llm.matterhorn.ej.api.ExplicitTaskContext",
            true,
            classLoader
        )

        // Get IssueType enum class
        val issueTypeClass = Class.forName(
            "com.intellij.ml.llm.matterhorn.ej.api.IssueType",
            true,
            classLoader
        )

        // Try to get IssueType field by name from runs (opaque string)
        val issueTypeFieldName = runs.trim().ifBlank { "ISSUE" }
        val issueType = tryGetEnumField(issueTypeClass, issueTypeFieldName)
            ?: tryGetEnumField(issueTypeClass, "ISSUE") // fallback
            ?: throw IllegalStateException("Cannot find IssueType enum values")

        log.debug("Creating TaskContext with IssueType from runs: $issueTypeFieldName")

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

    /**
     * Attempts to get an enum field by name.
     * Returns null if field not found (forward compatibility).
     */
    private fun tryGetEnumField(enumClass: Class<*>, fieldName: String): Any? {
        return try {
            val field = enumClass.getDeclaredField(fieldName)
            field.get(null)
        } catch (e: NoSuchFieldException) {
            log.debug("Enum field $fieldName not found in ${enumClass.simpleName} (may be new API)")
            null
        } catch (e: Exception) {
            log.warn("Failed to get enum field $fieldName: ${e.message}")
            null
        }
    }

    override fun getSupportedModes(): List<ProviderModeInfo> {
        // runs contains IssueType enum field name
        return listOf(
            ProviderModeInfo("ask", "CHAT"),
            ProviderModeInfo("agent", "ISSUE")
        )
    }
}
