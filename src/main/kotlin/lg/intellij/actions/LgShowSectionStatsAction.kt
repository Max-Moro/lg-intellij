package lg.intellij.actions

import com.intellij.icons.AllIcons
import lg.intellij.LgBundle
import lg.intellij.cli.CliTarget
import lg.intellij.statepce.PCEStateStore

/**
 * Action to show statistics for selected section.
 */
class LgShowSectionStatsAction : LgShowStatsAction(
    LgBundle.message("action.show.stats.text"),
    LgBundle.message("action.show.stats.description"),
    AllIcons.Actions.ListFiles
) {

    override fun determineTarget(store: PCEStateStore): Pair<String?, String?> {
        val selectedSection = store.getBusinessState().persistent.section

        return if (selectedSection.isNotBlank()) {
            CliTarget.build("sec", selectedSection) to selectedSection
        } else {
            null to null
        }
    }

    override fun hasValidTarget(store: PCEStateStore): Boolean {
        return store.getBusinessState().persistent.section.isNotBlank()
    }
}
