// To parse the JSON, install kotlin's serialization plugin and do:
//
// val json              = Json { allowStructuredMapKeys = true }
// val tagSetsListSchema = json.parse(TagSetsListSchema.serializer(), jsonString)

package lg.intellij.cli.models

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

/**
 * Response schema for 'lg list tag-sets' command
 */
@Serializable
data class TagSetsListSchema (
    @SerialName("tag-sets")
    val tagSets: List<TagSet>
)

@Serializable
data class TagSet (
    /**
     * Unique identifier of the tag set
     */
    val id: String,

    val tags: List<Tag>,

    /**
     * Human-readable title of the tag set
     */
    val title: String
)

@Serializable
data class Tag (
    /**
     * Optional detailed description of the tag
     */
    val description: String? = null,

    /**
     * Unique identifier of the tag within the tag set
     */
    val id: String,

    /**
     * Human-readable title of the tag
     */
    val title: String
)
