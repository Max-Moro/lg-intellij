/**
 * Tokenization Domain - tokenizer settings.
 *
 * Commands:
 * - tokenization/SELECT_LIB - select tokenizer library
 * - tokenization/SET_ENCODER - set encoder within selected library
 * - tokenization/SET_CTX_LIMIT - set context token limit
 * - tokenization/LIBS_LOADED - store loaded libraries, validate selection
 * - tokenization/ENCODERS_LOADED - store loaded encoders, validate selection
 *
 * Manages tokenizer library, encoder, and context limit settings.
 * Handles preferred encoder selection per library.
 */
package lg.intellij.statepce.domains

import com.intellij.openapi.project.Project
import lg.intellij.services.catalog.TokenizerCatalogService
import lg.intellij.stateengine.AsyncOperation
import lg.intellij.stateengine.BaseCommand
import lg.intellij.stateengine.RuleConfig
import lg.intellij.stateengine.command
import lg.intellij.statepce.EncoderEntry
import lg.intellij.statepce.PCEState
import lg.intellij.statepce.lgResult
import lg.intellij.statepce.rule

// ============================================
// Tokenization Defaults
// ============================================

/** Default tokenizer library */
const val DEFAULT_TOKENIZER_LIB = "tiktoken"

/** Default encoder (for tiktoken) */
const val DEFAULT_ENCODER = "o200k_base"

/** Default context limit in tokens */
const val DEFAULT_CTX_LIMIT = 128_000

// ============================================
// Preferred Encoders
// ============================================

/**
 * Preferred encoder for each tokenizer library.
 * Modern encoders optimized for code and multilingual content.
 *
 * - tiktoken: o200k_base — OpenAI tokenizer for GPT-4o, o1, o3, GPT-5
 * - tokenizers: mistralai/Mistral-7B-v0.1 — Tekken-based, 30% more efficient for code
 * - sentencepiece: google/mt5-base — excellent multilingual support
 */
private val PREFERRED_ENCODER = mapOf(
    "tiktoken" to "o200k_base",
    "tokenizers" to "mistralai/Mistral-7B-v0.1",
    "sentencepiece" to "google/mt5-base"
)

/**
 * Get preferred encoder for library, fallback to first available.
 */
private fun selectBestEncoder(lib: String, availableEncoders: List<String>): String {
    val preferred = PREFERRED_ENCODER[lib]
    if (preferred != null && preferred in availableEncoders) {
        return preferred
    }
    return availableEncoders.firstOrNull() ?: ""
}

// ============================================
// Commands
// ============================================

data class SelectLibPayload(val lib: String)
val SelectLib = command("tokenization/SELECT_LIB").payload<SelectLibPayload>()

data class SetEncoderPayload(val encoder: String)
val SetEncoder = command("tokenization/SET_ENCODER").payload<SetEncoderPayload>()

data class SetCtxLimitPayload(val limit: Int)
val SetCtxLimit = command("tokenization/SET_CTX_LIMIT").payload<SetCtxLimitPayload>()

data class LibsLoadedPayload(val libs: List<String>)
val LibsLoaded = command("tokenization/LIBS_LOADED").payload<LibsLoadedPayload>()

data class EncodersLoadedPayload(val encoders: List<EncoderEntry>)
val EncodersLoaded = command("tokenization/ENCODERS_LOADED").payload<EncodersLoadedPayload>()

// ============================================
// Rule Registration
// ============================================

/**
 * Register tokenization domain rules.
 *
 * @param project Project for service access in async operations
 */
fun registerTokenizationRules(project: Project) {

    // When tokenizer libs are loaded, store them and validate selection
    rule.invoke(LibsLoaded, RuleConfig(
        condition = { _: PCEState, _: LibsLoadedPayload -> true },
        apply = { state: PCEState, payload: LibsLoadedPayload ->
            val libs = payload.libs
            val currentLib = state.persistent.tokenizerLib

            val isValid = currentLib.isNotEmpty() && currentLib in libs
            val newLib = if (isValid) currentLib else (libs.firstOrNull() ?: DEFAULT_TOKENIZER_LIB)

            if (!isValid || state.configuration.encoders.isEmpty()) {
                lgResult(
                    configMutations = mapOf("tokenizerLibs" to libs),
                    mutations = mapOf("tokenizerLib" to newLib),
                    asyncOps = listOf(object : AsyncOperation {
                        override suspend fun execute(): BaseCommand {
                            val tokenizerService = TokenizerCatalogService.getInstance()
                            val encoderNames = tokenizerService.getEncoders(newLib, project)
                            val encoders = encoderNames.map { EncoderEntry(name = it) }
                            return EncodersLoaded.create(EncodersLoadedPayload(encoders))
                        }
                    })
                )
            } else {
                lgResult(
                    configMutations = mapOf("tokenizerLibs" to libs)
                )
            }
        }
    ))

    // When tokenizer lib changes, reload encoders list
    rule.invoke(SelectLib, RuleConfig(
        condition = { state: PCEState, payload: SelectLibPayload ->
            payload.lib != state.persistent.tokenizerLib
        },
        apply = { _: PCEState, payload: SelectLibPayload ->
            val lib = payload.lib
            lgResult(
                mutations = mapOf("tokenizerLib" to lib),
                asyncOps = listOf(object : AsyncOperation {
                    override suspend fun execute(): BaseCommand {
                        val tokenizerService = TokenizerCatalogService.getInstance()
                        val encoderNames = tokenizerService.getEncoders(lib, project)
                        val encoders = encoderNames.map { EncoderEntry(name = it) }
                        return EncodersLoaded.create(EncodersLoadedPayload(encoders))
                    }
                })
            )
        }
    ))

    // When encoders are loaded, store them and validate current selection
    rule.invoke(EncodersLoaded, RuleConfig(
        condition = { _: PCEState, _: EncodersLoadedPayload -> true },
        apply = { state: PCEState, payload: EncodersLoadedPayload ->
            val encoders = payload.encoders
            val currentEncoder = state.persistent.encoder
            val currentLib = state.persistent.tokenizerLib

            val encoderNames = encoders.map { it.name }
            val isValid = currentEncoder.isNotEmpty() && currentEncoder in encoderNames

            if (isValid) {
                lgResult(
                    configMutations = mapOf("encoders" to encoders)
                )
            } else {
                val newEncoder = selectBestEncoder(currentLib, encoderNames)
                lgResult(
                    configMutations = mapOf("encoders" to encoders),
                    followUp = if (newEncoder.isNotBlank()) {
                        listOf(SetEncoder.create(SetEncoderPayload(newEncoder)))
                    } else {
                        null
                    }
                )
            }
        }
    ))

    // When encoder is set, update persistent state
    rule.invoke(SetEncoder, RuleConfig(
        condition = { state: PCEState, payload: SetEncoderPayload ->
            payload.encoder != state.persistent.encoder
        },
        apply = { _: PCEState, payload: SetEncoderPayload ->
            lgResult(
                mutations = mapOf("encoder" to payload.encoder)
            )
        }
    ))

    // When context limit is set, validate and update persistent state
    rule.invoke(SetCtxLimit, RuleConfig(
        condition = { _: PCEState, _: SetCtxLimitPayload -> true },
        apply = { _: PCEState, payload: SetCtxLimitPayload ->
            val limit = payload.limit.coerceIn(1000, 2_000_000)
            lgResult(
                mutations = mapOf("ctxLimit" to limit)
            )
        }
    ))
}
