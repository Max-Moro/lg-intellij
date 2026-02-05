package lg.intellij.actions

import com.intellij.icons.AllIcons
import lg.intellij.LgBundle
import lg.intellij.statepce.PCEStateStore

/**
 * Action to show statistics for selected context.
 */
class LgShowContextStatsAction : LgShowStatsAction(
    LgBundle.message("action.show.context.stats.text"),
    LgBundle.message("action.show.context.stats.description"),
    AllIcons.Actions.ListFiles
) {

    override fun determineTarget(store: PCEStateStore): Pair<String?, String?> {
        val selectedTemplate = store.getBusinessState().persistent.template

        return if (selectedTemplate.isNotBlank()) {
            "ctx:$selectedTemplate" to selectedTemplate
        } else {
            null to null
        }
    }

    override fun hasValidTarget(store: PCEStateStore): Boolean {
        return store.getBusinessState().persistent.template.isNotBlank()
    }
}
