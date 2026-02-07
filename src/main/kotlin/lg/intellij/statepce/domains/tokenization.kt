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

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import lg.intellij.cli.CliClient
import lg.intellij.stateengine.AsyncOperation
import lg.intellij.stateengine.BaseCommand
import lg.intellij.stateengine.RuleConfig
import lg.intellij.stateengine.command
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

val SelectLib = command("tokenization/SELECT_LIB").payload<String>()
val SetEncoder = command("tokenization/SET_ENCODER").payload<String>()
val SetCtxLimit = command("tokenization/SET_CTX_LIMIT").payload<Int>()
val LibsLoaded = command("tokenization/LIBS_LOADED").payload<List<String>>()
val EncodersLoaded = command("tokenization/ENCODERS_LOADED").payload<List<String>>()

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
        condition = { _: PCEState, _: List<String> -> true },
        apply = { state: PCEState, libs: List<String> ->
            val currentLib = state.persistent.tokenizerLib

            val isValid = currentLib.isNotEmpty() && currentLib in libs
            val newLib = if (isValid) currentLib else (libs.firstOrNull() ?: DEFAULT_TOKENIZER_LIB)

            if (!isValid || state.configuration.encoders.isEmpty()) {
                lgResult(
                    config = { c -> c.copy(tokenizerLibs = libs) },
                    persistent = { s -> s.copy(tokenizerLib = newLib) },
                    asyncOps = listOf(object : AsyncOperation {
                        override suspend fun execute(): BaseCommand {
                            val cliClient = project.service<CliClient>()
                            val encoders = cliClient.listEncoders(newLib)
                            return EncodersLoaded.create(encoders)
                        }
                    })
                )
            } else {
                lgResult(
                    config = { c -> c.copy(tokenizerLibs = libs) }
                )
            }
        }
    ))

    // When tokenizer lib changes, reload encoders list
    rule.invoke(SelectLib, RuleConfig(
        condition = { state: PCEState, lib: String ->
            lib != state.persistent.tokenizerLib
        },
        apply = { _: PCEState, lib: String ->
            lgResult(
                persistent = { s -> s.copy(tokenizerLib = lib) },
                asyncOps = listOf(object : AsyncOperation {
                    override suspend fun execute(): BaseCommand {
                        val cliClient = project.service<CliClient>()
                        val encoders = cliClient.listEncoders(lib)
                        return EncodersLoaded.create(encoders)
                    }
                })
            )
        }
    ))

    // When encoders are loaded, store them and validate current selection
    rule.invoke(EncodersLoaded, RuleConfig(
        condition = { _: PCEState, _: List<String> -> true },
        apply = { state: PCEState, encoders: List<String> ->
            val currentEncoder = state.persistent.encoder
            val currentLib = state.persistent.tokenizerLib

            val isValid = currentEncoder.isNotEmpty() && currentEncoder in encoders

            if (isValid) {
                lgResult(
                    config = { c -> c.copy(encoders = encoders) }
                )
            } else {
                val newEncoder = selectBestEncoder(currentLib, encoders)
                lgResult(
                    config = { c -> c.copy(encoders = encoders) },
                    followUp = if (newEncoder.isNotBlank()) {
                        listOf(SetEncoder.create(newEncoder))
                    } else {
                        null
                    }
                )
            }
        }
    ))

    // When encoder is set, update persistent state
    rule.invoke(SetEncoder, RuleConfig(
        condition = { state: PCEState, encoder: String ->
            encoder != state.persistent.encoder
        },
        apply = { _: PCEState, encoder: String ->
            lgResult(persistent = { s -> s.copy(encoder = encoder) })
        }
    ))

    // When context limit is set, validate and update persistent state
    rule.invoke(SetCtxLimit, RuleConfig(
        condition = { _: PCEState, _: Int -> true },
        apply = { _: PCEState, limit: Int ->
            val limit = limit.coerceIn(1000, 2_000_000)
            lgResult(persistent = { s -> s.copy(ctxLimit = limit) })
        }
    ))
}
