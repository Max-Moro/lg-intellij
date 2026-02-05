package lg.intellij.actions

import com.intellij.icons.AllIcons
import lg.intellij.LgBundle
import lg.intellij.services.generation.GenerationTarget

/**
 * Action to generate listing for the selected section.
 */
class LgGenerateListingAction : LgGenerateAction(
    text = LgBundle.message("action.generate.listing.text"),
    description = LgBundle.message("action.generate.listing.description"),
    icon = AllIcons.Actions.ShowCode,
    targetType = GenerationTarget.SECTION,
    targetNameProvider = { store ->
        store.getBusinessState().persistent.section.takeIf { it.isNotBlank() }
    }
)
