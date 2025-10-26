package lg.intellij.services.ai.providers

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    
    private val LOG = logger<JetBrainsAiProvider>()
    
    override val id = "jetbrains.ai"
    override val name = "JetBrains AI Assistant"
    override val priority = 90
    override val pluginId = "com.intellij.ml.llm"
    override val toolWindowId = "AIAssistant"

    override suspend fun sendToExtension(project: Project, content: String) {
        LOG.info("Sending content to JetBrains AI Assistant")

        // 1. Получаем или создаём чат-сессию
        val chatSession = getOrCreateChatSession(project)

        // 2. Переключаем фокус на новую сессию (на EDT)
        withContext(Dispatchers.EDT) {
            focusChatSession(project, chatSession)
        }

        // 3. Отправляем сообщение
        sendMessage(project, chatSession, content)

        LOG.info("Successfully sent to JetBrains AI")
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
        
        LOG.debug("Focused chat session")
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
            LOG.debug("Creating new chat session (createNew=true)")
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
            LOG.debug("Using existing chat session: $uid")
            return focusedSession
        }
        
        // Create new chat session
        LOG.debug("Creating new chat session (no focused session found)")
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

        // SourceAction is enum
        val sourceActionClass = Class.forName(
            "com.intellij.ml.llm.core.chat.session.ChatSessionStorage\$SourceAction",
            true,
            classLoader
        )
        val newChatAction = sourceActionClass.enumConstants.first { it.toString() == "NEW_CHAT" }
        
        // Find constructor with 5 params (not the synthetic one with DefaultConstructorMarker)
        val contextConstructor = contextClass.constructors.first { it.parameterCount == 5 }
        
        // Get PrivacySafeTaskContext.Companion.getEmpty()
        val taskContextClass = Class.forName(
            "com.intellij.ml.llm.grazieAPIAdapters.tasksFacade.PrivacySafeTaskContext",
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
        
        // Call createChatSession (suspend function) - use kotlinx.coroutines
        return withContext(Dispatchers.Default) {
            // createChatSession is suspend function - call via suspendCoroutine
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                try {
                    // Find method with Continuation parameter
                    val createMethod = chatHostClass.declaredMethods.first { 
                        it.name == "createChatSession" && it.parameterCount == 2
                    }
                    createMethod.invoke(chatHost, context, continuation)
                } catch (e: Exception) {
                    continuation.resumeWith(Result.failure(e))
                }
            }
        }
    }
    
    /**
     * Отправляет сообщение в чат через рефлексию.
     */
    private suspend fun sendMessage(project: Project, chatSession: Any, content: String) {
        val classLoader = this::class.java.classLoader
        
        // Create PSString via ConstantsKt.getPrivacyConst()
        val constantsKtClass = Class.forName(
            "com.intellij.ml.llm.privacy.trustedStringBuilders.ConstantsKt",
            true,
            classLoader
        )
        val getPrivacyConstMethod = constantsKtClass.getMethod("getPrivacyConst", String::class.java)
        val psString = getPrivacyConstMethod.invoke(null, content)
        
        // Create FormattedString
        val formattedStringClass = Class.forName(
            "com.intellij.ml.llm.core.models.openai.FormattedString",
            true,
            classLoader
        )
        val psStringClass = psString::class.java
        val formattedConstructor = formattedStringClass.getConstructor(psStringClass)
        val formattedText = formattedConstructor.newInstance(psString)
        
        // Get SimpleChat.INSTANCE (implements ChatKind)
        val simpleChatClass = Class.forName(
            "com.intellij.ml.llm.core.chat.session.SimpleChat",
            true,
            classLoader
        )
        val instanceField = simpleChatClass.getDeclaredField("INSTANCE")
        val chatKind = instanceField.get(null)
        

        // Call send method (suspend) via suspendCancellableCoroutine
        kotlinx.coroutines.suspendCancellableCoroutine<Any> { continuation ->
            try {
                // Get ChatSession interface (methods are there, not in impl)
                val chatSessionInterface = Class.forName(
                    "com.intellij.ml.llm.core.chat.session.ChatSession",
                    true,
                    classLoader
                )
                
                // Find send method with FormattedString parameter (2nd param)
                val sendMethod = chatSessionInterface.declaredMethods.first {
                    it.name == "send" && 
                    it.parameterCount == 4 && 
                    it.parameterTypes[1].name.contains("FormattedString")
                }
                
                sendMethod.invoke(chatSession, project, formattedText, chatKind, continuation)
                
                // Log after invoke
                val getUidMethod = chatSessionInterface.getMethod("getUid")
                val uid = getUidMethod.invoke(chatSession)
                LOG.debug("Message sent to chat session: $uid")
            } catch (e: Exception) {
                continuation.resumeWith(Result.failure(e))
            }
        }
    }
}
