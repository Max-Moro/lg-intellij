package lg.intellij.models

import kotlinx.serialization.*

/**
 * Response schema for 'list sections' command.
 *
 * Each section carries its own mode-sets and tag-sets,
 * allowing per-section adaptive settings.
 */
@Serializable
data class SectionsListSchema (
    val sections: List<SectionInfo>
)

/**
 * Section info with embedded mode-sets and tag-sets.
 *
 * Reuses ModeSet/TagSet from ModeSetsListSchema and TagSetsListSchema
 * (same package, same serialization structure).
 */
@Serializable
data class SectionInfo (
    /**
     * Mode sets after extends resolution (may be empty)
     */
    @SerialName("mode-sets")
    val modeSets: List<ModeSet>,

    /**
     * Section canonical name
     */
    val name: String,

    /**
     * Tag sets after extends resolution (may be empty)
     */
    @SerialName("tag-sets")
    val tagSets: List<TagSet>
)
