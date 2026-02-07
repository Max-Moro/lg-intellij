package lg.intellij.ui.components

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionField
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionInfo
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionInfoRenderer
import com.intellij.openapi.externalSystem.service.ui.completion.collector.TextCompletionCollector
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import lg.intellij.statepce.PCEStateStore

/**
 * Text field with auto-completion for encoder selection.
 *
 * Features:
 * - Shows suggestions from PCEStateStore configuration
 * - Supports custom encoder values (user can type anything)
 * - Auto-reloads suggestions when tokenizer library changes
 *
 * Usage:
 * ```kotlin
 * val encoderField = LgEncoderCompletionField(project, parentDisposable)
 * encoderField.text = "cl100k_base"
 * encoderField.setLibrary("tiktoken")
 * ```
 */
class LgEncoderCompletionField(
    private val myProject: Project,
    parentDisposable: Disposable
) : TextCompletionField<TextCompletionInfo>(myProject) {

    // Current tokenizer library
    private var currentLibrary: String = "tiktoken"
    
    // Modification tracker to invalidate completion when library changes
    private var libraryChangeCounter = 0L
    private val libraryModificationTracker = ModificationTracker { libraryChangeCounter }
    
    override val completionCollector: TextCompletionCollector<TextCompletionInfo> =
        TextCompletionCollector.async(libraryModificationTracker, parentDisposable) {
            loadEncoderSuggestions()
        }
    
    init {
        renderer = TextCompletionInfoRenderer()
        completionType = CompletionType.REPLACE_TEXT
    }
    
    /**
     * Sets the tokenizer library and reloads encoder suggestions.
     */
    fun setLibrary(library: String) {
        if (currentLibrary != library) {
            currentLibrary = library
            libraryChangeCounter++
            
            // Trigger completion popup update
            updatePopup(UpdatePopupType.SHOW_IF_HAS_VARIANCES)
        }
    }
    
    /**
     * Loads encoder suggestions from state.
     */
    private fun loadEncoderSuggestions(): List<TextCompletionInfo> {
        return try {
            val state = PCEStateStore.getInstance(myProject).getBusinessState()
            val encoders = state.configuration.encoders

            LOG.debug("Loaded ${encoders.size} encoder suggestions for library '$currentLibrary'")

            encoders.map { TextCompletionInfo(it) }
        } catch (e: Exception) {
            LOG.warn("Failed to load encoder suggestions", e)
            emptyList()
        }
    }
    
    companion object {
        private val LOG = logger<LgEncoderCompletionField>()
    }
}

