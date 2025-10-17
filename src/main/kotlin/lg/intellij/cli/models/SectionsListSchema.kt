package lg.intellij.cli.models

import kotlinx.serialization.*

/**
 * Response schema for 'lg list sections' command
 */
@Serializable
data class SectionsListSchema(
    val sections: List<String>
)

