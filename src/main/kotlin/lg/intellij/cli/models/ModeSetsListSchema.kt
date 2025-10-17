// To parse the JSON, install kotlin's serialization plugin and do:
//
// val json               = Json { allowStructuredMapKeys = true }
// val modeSetsListSchema = json.parse(ModeSetsListSchema.serializer(), jsonString)

package lg.intellij.cli.models

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

/**
 * Response schema for 'lg list mode-sets' command
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
     * Additional options specific to this mode
     */
    val options: JsonObject? = null,

    /**
     * Array of tags activated by this mode
     */
    val tags: List<String>? = null,

    /**
     * Human-readable title of the mode
     */
    val title: String
)
