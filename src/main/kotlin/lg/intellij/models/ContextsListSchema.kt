package lg.intellij.models

import kotlinx.serialization.*

/**
 * Response schema for 'lg list contexts' command
 */
@Serializable
data class ContextsListSchema(
    val contexts: List<String>
)

