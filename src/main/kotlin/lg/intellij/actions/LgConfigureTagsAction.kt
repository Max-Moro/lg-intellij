package lg.intellij.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import lg.intellij.services.catalog.LgCatalogService
import lg.intellij.services.state.LgPanelStateService
import lg.intellij.ui.dialogs.LgTagsDialog

/**
 * Action to open Tags Configuration Dialog.
 *
 * Opens modal dialog with tag-sets, allows user to select tags,
 * saves selection to LgPanelStateService on OK.
 */
class LgConfigureTagsAction : AnAction() {
    
    private val log = logger<LgConfigureTagsAction>()
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        val catalogService = project.service<LgCatalogService>()
        val panelState = project.service<LgPanelStateService>()
        
        // Get current tag-sets and selected tags
        val tagSetsData = catalogService.tagSets.value
        val currentSelectedTags = panelState.state.tags.mapValues { it.value.toSet() }
        
        // Open dialog
        val dialog = LgTagsDialog(
            project = project,
            tagSetsData = tagSetsData,
            initialSelectedTags = currentSelectedTags
        )
        
        if (dialog.showAndGet()) {
            // User clicked OK — save selected tags
            val newSelectedTags = dialog.getSelectedTags()
            
            panelState.state.tags.clear()
            newSelectedTags.forEach { (tagSetId, tagIds) ->
                panelState.state.tags[tagSetId] = tagIds.toMutableSet()
            }
            
            val totalTags = newSelectedTags.values.sumOf { it.size }
            log.info("Tags updated: $totalTags tags selected across ${newSelectedTags.size} tag-sets")
        }
    }
    
    override fun update(e: AnActionEvent) {
        // Available only if project is open
        e.presentation.isEnabled = e.project != null
    }
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}

