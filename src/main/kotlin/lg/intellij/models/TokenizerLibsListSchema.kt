package lg.intellij.models

import kotlinx.serialization.*

/**
 * Response schema for 'lg list tokenizer-libs' command
 */
@Serializable
data class TokenizerLibsListSchema(
    @SerialName("tokenizer_libs")
    val tokenizerLibs: List<String>
)

