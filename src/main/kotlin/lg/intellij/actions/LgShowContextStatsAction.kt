package lg.intellij.actions

import com.intellij.icons.AllIcons
import lg.intellij.LgBundle
import lg.intellij.services.state.LgPanelStateService

/**
 * Action to show statistics for selected context.
 */
class LgShowContextStatsAction : LgShowStatsAction(
    LgBundle.message("action.show.context.stats.text"),
    LgBundle.message("action.show.context.stats.description"),
    AllIcons.Actions.ListFiles
) {
    
    override fun determineTarget(panelState: LgPanelStateService): Pair<String?, String?> {
        val selectedTemplate = panelState.state.selectedTemplate
        
        return if (!selectedTemplate.isNullOrBlank()) {
            "ctx:$selectedTemplate" to selectedTemplate
        } else {
            null to null
        }
    }
    
    override fun hasValidTarget(panelState: LgPanelStateService): Boolean {
        return !panelState.state.selectedTemplate.isNullOrBlank()
    }
}

