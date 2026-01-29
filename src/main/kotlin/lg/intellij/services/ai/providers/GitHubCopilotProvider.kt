package lg.intellij.services.ai.providers

import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lg.intellij.services.ai.ProviderModeInfo
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
 * - Supports ask/agent/plan modes via runs string
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
        runs: String
    ) {
        log.info("Sending content to GitHub Copilot (runs: $runs)")

        withContext(Dispatchers.Default) {
            // Get CopilotChatService
            val chatServiceClass = Class.forName(
                "com.github.copilot.api.CopilotChatService",
                true,
                this@GitHubCopilotProvider::class.java.classLoader
            )

            @Suppress("IncorrectServiceRetrieving")
            val chatService = project.getService(chatServiceClass)

            // Create DataContext (empty, can be extended if needed)
            val dataContext = SimpleDataContext.builder().build()

            // Create QueryOptionBuilder lambda with runs
            val builderLambda = createQueryOptionBuilderLambda(content, runs)

            // Call query method
            val queryMethod = chatServiceClass.getMethod(
                "query",
                com.intellij.openapi.actionSystem.DataContext::class.java,
                Function1::class.java
            )

            queryMethod.invoke(chatService, dataContext, builderLambda)
        }

        log.info("Successfully sent to GitHub Copilot")
    }

    /**
     * Creates a lambda for QueryOptionBuilder with settings.
     */
    private fun createQueryOptionBuilderLambda(
        content: String,
        runs: String
    ): Function1<Any, Unit> {
        val builderClass = Class.forName(
            "com.github.copilot.api.QueryOptionBuilder",
            true,
            this::class.java.classLoader
        )

        return object : Function1<Any, Unit> {
            override fun invoke(p1: Any) {
                configureBuilder(p1, content, builderClass, runs)
            }
        }
    }

    /**
     * Configures QueryOptionBuilder through reflection.
     *
     * runs is treated as opaque string containing method names to invoke.
     * Each word in runs is attempted as a method call on the builder.
     * Unknown methods are logged and skipped (forward compatibility).
     *
     * Example runs values:
     * - "withAskMode" → calls builder.withAskMode()
     * - "withAgentMode" → calls builder.withAgentMode()
     * - "withPlanMode" → calls builder.withPlanMode()
     * - "withAskMode withSomeNewFeature" → calls both methods
     *
     * If runs is empty or all methods fail, defaults to withAgentMode().
     */
    private fun configureBuilder(
        builder: Any,
        content: String,
        builderClass: Class<*>,
        runs: String
    ) {
        // withInput(content) - always required
        val withInputMethod = builderClass.getMethod("withInput", String::class.java)
        withInputMethod.invoke(builder, content)

        // withNewSession() - create a new session for each request
        tryInvokeMethod(builder, builderClass, "withNewSession")

        // hideWelcomeMessage() - hide welcome message
        tryInvokeMethod(builder, builderClass, "hideWelcomeMessage")

        // Parse runs as space-separated method names (opaque string)
        val methodNames = runs.trim().split(Regex("\\s+")).filter { it.isNotBlank() }

        if (methodNames.isEmpty()) {
            // Default behavior when runs is empty
            tryInvokeMethod(builder, builderClass, "withAgentMode")
            log.debug("Configured QueryOptionBuilder: newSession, hideWelcome, withAgentMode (default)")
            return
        }

        // Try to invoke each method from runs
        var anySucceeded = false
        for (methodName in methodNames) {
            if (tryInvokeMethod(builder, builderClass, methodName)) {
                anySucceeded = true
            }
        }

        // If all methods failed, fallback to default
        if (!anySucceeded) {
            log.warn("No methods from runs succeeded, falling back to withAgentMode")
            tryInvokeMethod(builder, builderClass, "withAgentMode")
        }

        log.debug("Configured QueryOptionBuilder with runs: $runs")
    }

    /**
     * Attempts to invoke a no-arg method on the builder.
     * Returns true if successful, false if method not found.
     */
    private fun tryInvokeMethod(builder: Any, builderClass: Class<*>, methodName: String): Boolean {
        return try {
            val method = builderClass.getMethod(methodName)
            method.invoke(builder)
            true
        } catch (e: NoSuchMethodException) {
            log.debug("Method $methodName not found on ${builderClass.simpleName} (may be new API)")
            false
        } catch (e: Exception) {
            log.warn("Failed to invoke $methodName: ${e.message}")
            false
        }
    }

    override fun getSupportedModes(): List<ProviderModeInfo> {
        // runs contains actual method names to invoke on QueryOptionBuilder
        return listOf(
            ProviderModeInfo("ask", "withAskMode"),
            ProviderModeInfo("agent", "withAgentMode"),
            ProviderModeInfo("plan", "withPlanMode")
        )
    }
}
