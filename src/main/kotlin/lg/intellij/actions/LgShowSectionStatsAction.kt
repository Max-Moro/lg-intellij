package lg.intellij.actions

import com.intellij.icons.AllIcons
import lg.intellij.LgBundle
import lg.intellij.services.state.LgPanelStateService

/**
 * Action to show statistics for selected section.
 */
class LgShowSectionStatsAction : LgShowStatsAction(
    LgBundle.message("action.show.stats.text"),
    LgBundle.message("action.show.stats.description"),
    AllIcons.Actions.ListFiles
) {
    
    override fun determineTarget(panelState: LgPanelStateService): Pair<String?, String?> {
        val selectedSection = panelState.state.selectedSection
        
        return if (!selectedSection.isNullOrBlank()) {
            "sec:$selectedSection" to selectedSection
        } else {
            null to null
        }
    }
    
    override fun hasValidTarget(panelState: LgPanelStateService): Boolean {
        return !panelState.state.selectedSection.isNullOrBlank()
    }
}

