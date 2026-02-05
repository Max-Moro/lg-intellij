package lg.intellij.actions

import com.intellij.icons.AllIcons
import lg.intellij.LgBundle
import lg.intellij.services.generation.GenerationTarget

/**
 * Action to generate context for the selected template.
 */
class LgGenerateContextAction : LgGenerateAction(
    text = LgBundle.message("action.generate.context.text"),
    description = LgBundle.message("action.generate.context.description"),
    icon = AllIcons.Actions.ShowCode,
    targetType = GenerationTarget.CONTEXT,
    targetNameProvider = { store ->
        store.getBusinessState().persistent.template.takeIf { it.isNotBlank() }
    }
)
