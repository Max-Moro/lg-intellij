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
import lg.intellij.services.generation.GenerationTarget
import lg.intellij.services.generation.LgGenerationService
import lg.intellij.services.state.LgPanelStateService
import lg.intellij.services.state.LgSettingsService

/**
 * Action to send generated content to AI provider.
 * 
 * Determines target (context or listing) based on Control Panel state:
 * - If template selected → generate context
 * - Otherwise → generate listing for selected section
 * 
 * Sends to provider configured in Settings.
 * On error: offers fallback to clipboard.
 */
class LgSendToAiAction : AnAction(
    LgBundle.message("action.send.ai.text"),
    LgBundle.message("action.send.ai.description"),
    AllIcons.Actions.Execute
) {
    
    private val LOG = logger<LgSendToAiAction>()
    private val scope = CoroutineScope(Dispatchers.Default)
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        val panelState = project.service<LgPanelStateService>()
        val settings = LgSettingsService.getInstance()
        val aiService = AiIntegrationService.getInstance()
        val generationService = project.service<LgGenerationService>()
        
        // Определить target
        val selectedTemplate = panelState.state.selectedTemplate
        val target = if (!selectedTemplate.isNullOrBlank()) {
            GenerationTarget.CONTEXT
        } else {
            GenerationTarget.SECTION
        }
        
        val targetName = when (target) {
            GenerationTarget.CONTEXT -> selectedTemplate ?: ""
            GenerationTarget.SECTION -> panelState.state.selectedSection ?: "all"
        }
        
        // Resolve provider ID (auto-detect if empty)
        val providerId = runBlocking {
            aiService.resolveProvider(settings.state.aiProvider)
        }
        val providerName = aiService.getProviderName(providerId)
        
        // Генерация и отправка с progress indicator
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            LgBundle.message("action.send.ai.progress", targetName),
            true
        ) {
            private var generatedContent: String? = null
            
            override fun run(indicator: ProgressIndicator) {
                // Генерация (blocking call для suspend функции)
                indicator.text = LgBundle.message("action.send.ai.progress.text", targetName)
                generatedContent = runBlocking {
                    generationService.generate(target, targetName)
                }
                
                // Если генерация провалилась, не продолжаем
                if (generatedContent == null) {
                    return
                }

                // Отправка
                indicator.text = "Sending to $providerName..."

                scope.launch {
                    try {
                        aiService.sendTo(providerId, generatedContent!!)
                    } catch (ex: AiProviderException) {
                        // Error notification с fallback на clipboard
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
                                        aiService.sendTo("clipboard", generatedContent!!)
                                        notification.expire()
                                    } catch (fallbackEx: Exception) {
                                        LOG.error("Clipboard fallback failed", fallbackEx)
                                    }
                                }
                            }
                        )

                        notification.addAction(
                            NotificationAction.createSimple("Open Settings") {
                                com.intellij.ide.actions.ShowSettingsUtilImpl.showSettingsDialog(
                                    project,
                                    "lg.intellij.settings",
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

