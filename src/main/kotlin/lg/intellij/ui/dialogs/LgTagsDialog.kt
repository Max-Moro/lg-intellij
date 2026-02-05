package lg.intellij.ui.dialogs

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import lg.intellij.LgBundle
import lg.intellij.models.TagSet
import lg.intellij.models.TagSetsListSchema
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants

/**
 * Modal dialog for configuring active tags.
 *
 * Features:
 * - Displays all available tag-sets in collapsible groups
 * - Checkbox for each tag with description tooltip
 * - Returns selected tags on OK (grouped by tag-set)
 */
class LgTagsDialog(
    project: Project?,
    private val tagSetsData: TagSetsListSchema?,
    initialSelectedTags: Map<String, Set<String>>
) : DialogWrapper(project) {
    
    // Mutable state for tracking selected tags: tagSetId -> Set<tagId>
    private val selectedTags = initialSelectedTags.mapValues { it.value.toMutableSet() }.toMutableMap()
    
    // Store panel reference for apply()
    private lateinit var contentPanel: DialogPanel
    
    init {
        title = LgBundle.message("dialog.tags.title")
        isResizable = true
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        contentPanel = panel {
            if (tagSetsData == null || tagSetsData.tagSets.isEmpty()) {
                // Empty state
                row {
                    label(LgBundle.message("dialog.tags.empty"))
                        .applyToComponent {
                            foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
                        }
                }
            } else {
                // Render tag-sets
                for (tagSet in tagSetsData.tagSets) {
                    // Skip global tag-set (managed by modes)
                    if (tagSet.id == "global") continue
                    
                    createTagSetGroup(tagSet)
                }
            }
        }
        
        return JBScrollPane(contentPanel).apply {
            preferredSize = JBUI.size(500, 600)
            border = null
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        }
    }
    
    /**
     * Creates a collapsible group for a tag-set.
     */
    private fun Panel.createTagSetGroup(tagSet: TagSet) {
        // Check if this tag-set has any selected tags
        val hasSelectedTags = selectedTags[tagSet.id]?.isNotEmpty() == true
        
        val group = collapsibleGroup(tagSet.title, indent = false) {
            if (tagSet.tags.isEmpty()) {
                row {
                    label(LgBundle.message("dialog.tags.set.empty"))
                        .applyToComponent {
                            foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
                        }
                }
            } else {
                for (tag in tagSet.tags) {
                    row {
                        checkBox(tag.title)
                            .bindSelected(
                                getter = { 
                                    selectedTags[tagSet.id]?.contains(tag.id) ?: false
                                },
                                setter = { checked ->
                                    val tagSetTags = selectedTags.getOrPut(tagSet.id) { mutableSetOf() }
                                    if (checked) {
                                        tagSetTags.add(tag.id)
                                    } else {
                                        tagSetTags.remove(tag.id)
                                    }
                                }
                            )
                            .apply {
                                // Add description as comment if available
                                if (!tag.description.isNullOrBlank()) {
                                    comment(tag.description)
                                }
                            }
                    }
                }
            }
        }
        
        // Expand group if it has selected tags
        if (hasSelectedTags) {
            group.expanded = true
        }
    }
    
    /**
     * Returns the map of selected tag IDs grouped by tag-set.
     * 
     * Call after showAndGet() returns true.
     */
    fun getSelectedTags(): Map<String, Set<String>> {
        return selectedTags.mapValues { it.value.toSet() }
    }
    
    override fun doOKAction() {
        contentPanel.apply()
        super.doOKAction()
    }
    
    override fun createActions() = arrayOf(
        okAction,
        cancelAction
    )
}

