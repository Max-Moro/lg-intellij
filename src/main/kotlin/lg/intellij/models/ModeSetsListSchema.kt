// To parse the JSON, install kotlin's serialization plugin and do:
//
// val json               = Json { allowStructuredMapKeys = true }
// val modeSetsListSchema = json.parse(ModeSetsListSchema.serializer(), jsonString)

package lg.intellij.models

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

/**
 * Response schema for 'list mode-sets' command
 */
@Serializable
data class ModeSetsListSchema (
    @SerialName("mode-sets")
    val modeSets: List<ModeSet>
)

@Serializable
data class ModeSet (
    /**
     * Unique identifier of the mode set
     */
    val id: String,

    /**
     * True if this mode-set is an integration type (has runs)
     */
    val integration: Boolean? = null,

    val modes: List<Mode>,

    /**
     * Human-readable title of the mode set
     */
    val title: String
)

@Serializable
data class Mode (
    /**
     * Optional detailed description of the mode
     */
    val description: String? = null,

    /**
     * Unique identifier of the mode within the mode set
     */
    val id: String,

    /**
     * Provider-specific run commands mapping provider_id to command string
     */
    val runs: Map<String, String>? = null,

    /**
     * Array of tags activated by this mode
     */
    val tags: List<String>? = null,

    /**
     * Human-readable title of the mode
     */
    val title: String
)
