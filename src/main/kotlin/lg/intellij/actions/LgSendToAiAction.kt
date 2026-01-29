package lg.intellij.actions

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import lg.intellij.LgBundle
import lg.intellij.services.ai.AiIntegrationService
import lg.intellij.services.ai.AiProviderException
import lg.intellij.services.catalog.LgCatalogService
import lg.intellij.services.generation.GenerationTarget
import lg.intellij.services.generation.LgGenerationService
import lg.intellij.services.state.LgPanelStateService

/**
 * Action to send generated context to AI provider.
 *
 * Only works with contexts (templates). Section listings cannot be sent to AI.
 *
 * Requirements:
 * - Context must be selected
 * - Provider must be selected
 * - Integration mode must be configured (except clipboard)
 */
class LgSendToAiAction : AnAction(
    LgBundle.message("action.send.ai.text"),
    LgBundle.message("action.send.ai.description"),
    AllIcons.Actions.Execute
) {

    private val log = logger<LgSendToAiAction>()
    private val scope = CoroutineScope(Dispatchers.Default)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val panelState = project.service<LgPanelStateService>()
        val catalogService = project.service<LgCatalogService>()
        val aiService = AiIntegrationService.getInstance()
        val generationService = project.service<LgGenerationService>()

        // Get provider from panel state
        val providerId = runBlocking {
            aiService.resolveProvider(panelState.state.providerId)
        }

        // Validate: must have context selected
        val selectedTemplate = panelState.state.selectedTemplate
        if (selectedTemplate.isNullOrBlank()) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("LG Notifications")
                .createNotification(
                    "No Context Selected",
                    "Select a context first. Section listings cannot be sent to AI.",
                    NotificationType.WARNING
                )
                .notify(project)
            return
        }

        val providerName = aiService.getProviderName(providerId)

        // Get runs from integration mode-set
        val modeSets = catalogService.modeSets.value
        val runs = panelState.getIntegrationModeRuns(selectedTemplate, providerId, modeSets)

        // Validate: must have integration mode configured (except clipboard)
        if (runs == null && providerId != "clipboard") {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("LG Important")
                .createNotification(
                    "No Integration Mode",
                    "No integration mode configured for this context and provider.\n" +
                    "Run 'Update AI Modes Template' to generate ai-interaction.sec.yaml.",
                    NotificationType.ERROR
                )
                .notify(project)
            return
        }

        // Generation and sending with progress indicator
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            LgBundle.message("action.send.ai.progress", selectedTemplate),
            true
        ) {
            private var generatedContent: String? = null

            override fun run(indicator: ProgressIndicator) {
                // Generate context
                indicator.text = LgBundle.message("action.send.ai.progress.text", selectedTemplate)
                generatedContent = runBlocking {
                    generationService.generate(GenerationTarget.CONTEXT, selectedTemplate)
                }

                // If generation failed, don't continue
                if (generatedContent == null) {
                    return
                }

                // Send to provider
                indicator.text = "Sending to $providerName..."

                scope.launch {
                    try {
                        aiService.sendTo(providerId, generatedContent!!, runs ?: "")
                    } catch (ex: AiProviderException) {
                        // Error notification with clipboard fallback
                        val notification = NotificationGroupManager.getInstance()
                            .getNotificationGroup("LG Important")
                            .createNotification(
                                "Send to AI Failed",
                                ex.message ?: "",
                                NotificationType.ERROR
                            )

                        notification.addAction(
                            NotificationAction.createSimple("Copy to Clipboard") {
                                scope.launch {
                                    try {
                                        aiService.sendTo("clipboard", generatedContent!!, "")
                                        notification.expire()
                                    } catch (fallbackEx: Exception) {
                                        log.error("Clipboard fallback failed", fallbackEx)
                                    }
                                }
                            }
                        )

                        notification.addAction(
                            NotificationAction.createSimple("Open settings") {
                                com.intellij.ide.actions.ShowSettingsUtilImpl.showSettingsDialog(
                                    project,
                                    "maxmoro.lg.settings",
                                    null
                                )
                                notification.expire()
                            }
                        )

                        notification.notify(project)
                    }
                }
            }
        })
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
