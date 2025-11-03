package lg.intellij.services.ai.providers

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lg.intellij.models.AiInteractionMode
import lg.intellij.services.ai.base.BaseExtensionProvider

/**
 * Провайдер для интеграции с JetBrains AI Assistant.
 * 

 * Использует рефлексию для работы с JetBrains AI API, чтобы избежать
 * compile-time зависимости (плагин не является bundled в Community Edition).
 * 
 * Функциональность:
 * - Открывает панель AI Assistant
 * - Создаёт новую чат-сессию (оптимально для LG)
 * - Отправляет сгенерированный контент как сообщение
 * 
 * Priority: 90 (highest among all providers)
 */
class JetBrainsAiProvider : BaseExtensionProvider() {
    
    private val log = logger<JetBrainsAiProvider>()
    
    override val id = "jetbrains.ai"
    override val name = "JetBrains AI Assistant"
    override val priority = 90
    override val pluginId = "com.intellij.ml.llm"
    override val toolWindowId = "AIAssistant"

    override suspend fun sendToExtension(
        project: Project,
        content: String,
        mode: AiInteractionMode
    ) {
        log.info("Sending content to JetBrains AI Assistant in ${mode.name} mode")

        // 1. Получаем или создаём чат-сессию
        val chatSession = getOrCreateChatSession(project)

        // 2. Переключаем фокус на новую сессию (на EDT)
        withContext(Dispatchers.EDT) {
            focusChatSession(project, chatSession)
        }

        // 3. Отправляем сообщение с режимом
        sendMessage(chatSession, content, mode)

        log.info("Successfully sent to JetBrains AI")
    }
    
    /**
     * Переключает фокус на указанную чат-сессию.
     */
    private fun focusChatSession(project: Project, chatSession: Any) {
        val classLoader = this::class.java.classLoader
        
        // Get FocusedChatSessionHost
        val focusedHostClass = Class.forName(
            "com.intellij.ml.llm.core.chat.session.FocusedChatSessionHost",
            true,
            classLoader
        )
        @Suppress("IncorrectServiceRetrieving")
        val focusedHost = project.getService(focusedHostClass)
        
        // Call focusChatSession(chatSession)
        val focusMethod = focusedHostClass.getMethod("focusChatSession", chatSession::class.java.interfaces[0])
        focusMethod.invoke(focusedHost, chatSession)
        
        log.debug("Focused chat session")
    }
    
    /**
     * Получает текущий активный чат или создаёт новый через рефлексию.
     * 
     * @param createNew Если true — всегда создаёт новую сессию (по умолчанию)
     */
    private suspend fun getOrCreateChatSession(project: Project, createNew: Boolean = true): Any {
        val classLoader = this::class.java.classLoader
        
        // Если нужна новая сессия — сразу создаём
        if (createNew) {
            log.debug("Creating new chat session (createNew=true)")
            return createNewChatSession(project, classLoader)
        }
        
        // Load classes via reflection
        val focusedHostClass = Class.forName(
            "com.intellij.ml.llm.core.chat.session.FocusedChatSessionHost",
            true,
            classLoader
        )
        
        // Try to get focused chat
        @Suppress("IncorrectServiceRetrieving")
        val focusedHost = project.getService(focusedHostClass)
        val getFocusedMethod = focusedHostClass.getMethod("getFocusedChatSession")
        val focusedSession = getFocusedMethod.invoke(focusedHost)
        
        if (focusedSession != null) {
            val sessionClass = Class.forName(
                "com.intellij.ml.llm.core.chat.session.ChatSession",
                true,
                classLoader
            )
            val getUidMethod = sessionClass.getMethod("getUid")
            val uid = getUidMethod.invoke(focusedSession)
            log.debug("Using existing chat session: $uid")
            return focusedSession
        }
        
        // Create new chat session
        log.debug("Creating new chat session (no focused session found)")
        return createNewChatSession(project, classLoader)
    }
    
    /**
     * Создаёт новую чат-сессию через рефлексию.
     */
    private suspend fun createNewChatSession(project: Project, classLoader: ClassLoader): Any {
        val chatHostClass = Class.forName(
            "com.intellij.ml.llm.core.chat.session.ChatSessionHost",
            true,
            classLoader
        )
        
        @Suppress( "IncorrectServiceRetrieving")
        val chatHost = project.getService(chatHostClass)
        
        // Create ChatCreationContext
        val contextClass = Class.forName(
            "com.intellij.ml.llm.core.chat.session.ChatCreationContext",
            true,
            classLoader
        )
        
        // ChatOrigin.AIAssistantTool is a sealed class object
        val aiAssistantToolClass = Class.forName(
            "com.intellij.ml.llm.core.chat.session.ChatOrigin\$AIAssistantTool",
            true,
            classLoader
        )
        val instanceField = aiAssistantToolClass.getDeclaredField("INSTANCE")
        val aiAssistantOrigin = instanceField.get(null)

        aiAssistantToolClass.superclass // ChatOrigin

        // ChatSourceAction is enum
        val sourceActionClass = Class.forName(
            "com.intellij.ml.llm.core.chat.session.ChatSourceAction",
            true,
            classLoader
        )
        val newChatAction = sourceActionClass.enumConstants.first { it.toString() == "NEW_CHAT" }
        
        // Find constructor with 5 params (not the synthetic one with DefaultConstructorMarker)
        val contextConstructor = contextClass.constructors.first { it.parameterCount == 5 }
        
        // Get PrivacySafeTaskContext.Companion.getEmpty()
        val taskContextClass = Class.forName(
            "com.intellij.ml.llm.grazie.adapters.tasksFacade.PrivacySafeTaskContext",
            true,
            classLoader
        )
        val companionField = taskContextClass.getDeclaredField("Companion")
        val companion = companionField.get(null)
        val getEmptyMethod = companion::class.java.getMethod("getEmpty")
        val emptyTaskContext = getEmptyMethod.invoke(companion)
        
        val context = contextConstructor.newInstance(
            aiAssistantOrigin,
            newChatAction,
            null,  // PsiFile (nullable) - отключает автоприкрепление активных файлов
            emptyList<Any>(),  // List<ChatContextItem> - пустой список (без файлов из контекста)
            emptyTaskContext   // PrivacySafeTaskContext (not nullable!)
        )
        
        // Create ChatRetrievalSession
        val chatRetrievalSessionClass = Class.forName(
            "com.intellij.ml.llm.core.chat.context.ChatRetrievalSession",
            true,
            classLoader
        )
        
        // Get ContextStorageImpl
        val contextStorageImplClass = Class.forName(
            "com.intellij.ml.llm.core.chat.session.impl.ContextStorageImpl",
            true,
            classLoader
        )
        val contextStorage = contextStorageImplClass.getDeclaredConstructor().newInstance()
        
        // Create CoroutineScope for retrieval session
        val supervisorScope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + Dispatchers.Default
        )
        
        // Create ChatRetrievalSession(project, coroutineScope, contextStorage, chat = null)
        val retrievalSessionConstructor = chatRetrievalSessionClass.constructors.first { it.parameterCount == 4 }
        val retrievalSession = retrievalSessionConstructor.newInstance(
            project,
            supervisorScope,
            contextStorage,
            null  // chat (nullable)
        )
        
        // Call createChatSession (suspend function) - use kotlinx.coroutines
        return withContext(Dispatchers.Default) {
            // createChatSession is suspend function - call via suspendCoroutine
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                try {
                    // Find method with 3 params: (ChatCreationContext, InteractiveRetrievalSession, Continuation)
                    val createMethod = chatHostClass.declaredMethods.first { 
                        it.name == "createChatSession" && it.parameterCount == 3
                    }
                    createMethod.invoke(chatHost, context, retrievalSession, continuation)
                } catch (e: Exception) {
                    continuation.resumeWith(Result.failure(e))
                }
            }
        }
    }
    
    /**
     * Отправляет сообщение в чат через рефлексию.
     * 
     * Соответствие режимов:
     * - ASK → Chat
     * - AGENT → Quick Edit
     */
    private suspend fun sendMessage(
        chatSession: Any,
        content: String,
        mode: AiInteractionMode
    ) {
        val classLoader = this::class.java.classLoader
        
        val chatSessionInterface = Class.forName(
            "com.intellij.ml.llm.core.chat.session.ChatSession",
            true,
            classLoader
        )
        
        if (mode == AiInteractionMode.AGENT) {
            // Set CODE_GENERATION mode (Quick Edit) via getChatMode().setChatMode()
            try {
                // Get CurrentChatSessionMode via getChatMode() from ChatSessionVm
                val chatSessionVmClass = Class.forName(
                    "com.intellij.ml.llm.core.chat.session.ChatSessionVm",
                    true,
                    classLoader
                )
                
                val getChatModeMethod = chatSessionVmClass.declaredMethods.first {
                    it.name == "getChatMode" && it.parameterCount == 0
                }
                
                val currentChatMode = getChatModeMethod.invoke(chatSession)
                
                // Get NewChatMode.CodeGeneration.INSTANCE
                val newChatModeClass = Class.forName(
                    "com.intellij.ml.llm.core.chat.ui.chat.input.chatModeSelector.NewChatMode",
                    true,
                    classLoader
                )
                val codeGenerationClass = newChatModeClass.declaredClasses.first {
                    it.simpleName == "CodeGeneration"
                }
                val instanceField = codeGenerationClass.getField("INSTANCE")
                val codeGenerationMode = instanceField.get(null)
                
                // Call setChatMode (suspend function) with proper coroutine context
                // This ensures the chat session is in CODE_GENERATION mode before sending
                withContext(Dispatchers.Default) {
                    kotlinx.coroutines.suspendCancellableCoroutine<Any> { continuation ->
                        try {
                            val setChatModeMethod = currentChatMode::class.java.declaredMethods.first {
                                it.name == "setChatMode" && it.parameterCount == 2
                            }
                            
                            // Call suspend function via reflection
                            val result = setChatModeMethod.invoke(currentChatMode, codeGenerationMode, continuation)
                            
                            // Check if function completed immediately (not suspended)
                            // If it returns COROUTINE_SUSPENDED, continuation will be called automatically
                            if (result !== kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED) {
                                continuation.resumeWith(Result.success(result))
                            }
                        } catch (e: Exception) {
                            log.error("Failed to set Quick Edit mode", e)
                            continuation.resumeWith(Result.failure(e))
                        }
                    }
                }
                
                log.debug("Switched to Quick Edit mode")
                
            } catch (e: Exception) {
                log.error("Failed to configure Quick Edit mode", e)
                // Continue with default mode
            }
        }
        
        // Create PSString via ConstantsKt.getPrivacyConst()
        val constantsKtClass = Class.forName(
            "com.intellij.ml.llm.privacy.trustedStringBuilders.ConstantsKt",
            true,
            classLoader
        )
        val getPrivacyConstMethod = constantsKtClass.getMethod("getPrivacyConst", String::class.java)
        val psString = getPrivacyConstMethod.invoke(null, content)
        
        // Use sendRequest(PSString) which automatically determines ChatKind based on session mode
        val sendRequestMethod = chatSessionInterface.declaredMethods.first {
            it.name == "sendRequest" && 
            it.parameterCount == 1 &&
            it.parameterTypes[0].name.contains("PSString")
        }
        
        sendRequestMethod.invoke(chatSession, psString)
        
        // Log
        val getUidMethod = chatSessionInterface.getMethod("getUid")
        val uid = getUidMethod.invoke(chatSession)
        log.info("Message sent to chat session: $uid in ${mode.name} mode")
    }
}
