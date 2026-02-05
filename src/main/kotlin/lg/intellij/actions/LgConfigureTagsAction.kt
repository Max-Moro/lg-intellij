package lg.intellij.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import lg.intellij.statepce.LgCoordinatorService
import lg.intellij.statepce.PCEStateStore
import lg.intellij.statepce.domains.SetTags
import lg.intellij.statepce.domains.SetTagsPayload
import lg.intellij.ui.dialogs.LgTagsDialog

/**
 * Action to open Tags Configuration Dialog.
 *
 * Opens modal dialog with tag-sets, allows user to select tags,
 * dispatches SetTags command to coordinator on OK.
 */
class LgConfigureTagsAction : AnAction() {

    private val log = logger<LgConfigureTagsAction>()
    private val scope = CoroutineScope(Dispatchers.Default)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val store = PCEStateStore.getInstance(project)
        val state = store.getBusinessState()

        // Get current context for context-dependent tags
        val ctx = state.persistent.template

        // Get current tag-sets and selected tags
        val tagSetsData = state.configuration.tagSets
        val currentSelectedTags = store.getCurrentTags(ctx)

        // Open dialog
        val dialog = LgTagsDialog(
            project = project,
            tagSetsData = tagSetsData,
            initialSelectedTags = currentSelectedTags
        )

        if (dialog.showAndGet()) {
            // User clicked OK — dispatch SetTags command
            val newSelectedTags = dialog.getSelectedTags()

            scope.launch {
                val coordinator = LgCoordinatorService.getInstance(project).coordinator
                coordinator.dispatch(
                    SetTags.create(SetTagsPayload(newSelectedTags))
                )
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
