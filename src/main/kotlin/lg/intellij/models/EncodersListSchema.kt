package lg.intellij.models

import kotlinx.serialization.*

/**
 * Response schema for 'lg list encoders --lib <lib>' command
 *
 * CLI returns simple array of strings:
 * {"lib": "tiktoken", "encoders": ["gpt2", "cl100k_base", ...]}
 */
@Serializable
data class EncodersListSchema(
    val lib: String,
    val encoders: List<String>
)
