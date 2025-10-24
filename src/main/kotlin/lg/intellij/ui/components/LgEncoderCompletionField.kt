package lg.intellij.ui.components

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionField
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionInfo
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionInfoRenderer
import com.intellij.openapi.externalSystem.service.ui.completion.collector.TextCompletionCollector
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lg.intellij.services.catalog.TokenizerCatalogService

/**
 * Text field with auto-completion for encoder selection.
 * 
 * Features:
 * - Shows suggestions from TokenizerCatalogService
 * - Supports custom encoder values (user can type anything)
 * - Indicates cached encoders with badge icon
 * - Auto-reloads suggestions when tokenizer library changes
 * - Async completion collection
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
    
    private val tokenizerService = TokenizerCatalogService.getInstance()
    
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
     * Loads encoder suggestions from TokenizerCatalogService.
     */
    private suspend fun loadEncoderSuggestions(): List<TextCompletionInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val encoders = tokenizerService.getEncoders(currentLibrary, myProject)
                
                LOG.debug("Loaded ${encoders.size} encoders for library '$currentLibrary'")
                
                // Convert encoder names to TextCompletionInfo
                encoders.map { TextCompletionInfo(it) }
                
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LOG.warn("Failed to load encoders for library '$currentLibrary'", e)
                emptyList()
            }
        }
    }
    
    companion object {
        private val LOG = logger<LgEncoderCompletionField>()
    }
}

