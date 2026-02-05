package lg.intellij.ui.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import lg.intellij.LgBundle
import lg.intellij.statepce.PCEState
import lg.intellij.statepce.PCEStateCoordinator
import lg.intellij.statepce.PCEStateStore
import lg.intellij.statepce.domains.SelectMode
import lg.intellij.statepce.domains.SelectModePayload
import lg.intellij.statepce.domains.SelectBranch
import lg.intellij.statepce.domains.SelectBranchPayload
import lg.intellij.ui.components.LgLabeledComponent
import lg.intellij.ui.components.LgWrappingPanel
import javax.swing.JComponent

/**
 * Self-contained panel for managing modes (mode-sets).
 *
 * Command-driven architecture:
 * - Subscribes to PCEStateStore for state updates
 * - Dispatches commands via PCEStateCoordinator for all user interactions
 * - Dynamic UI updates when data changes
 * - Target branch selector (shown in review mode)
 */
class LgModeSetsPanel(
    private val coordinator: PCEStateCoordinator,
    private val store: PCEStateStore,
    parentDisposable: Disposable
) : Disposable {

    // Coroutine scope (auto-cancelled on dispose)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // UI references
    private lateinit var wrappingPanel: LgWrappingPanel
    private var targetBranchCombo: ComboBox<String>? = null

    // Track last state for diff-based updates
    private var lastState: PCEState? = null

    // Suppresses dispatch during programmatic UI updates
    private var suppressDispatch = false

    init {
        // Register as child disposable
        Disposer.register(parentDisposable, this)

        // Subscribe to state changes
        val unsubscribe = store.subscribe { state ->
            ApplicationManager.getApplication().invokeLater {
                updateUI(state)
            }
        }

        Disposer.register(this, Disposable {
            unsubscribe()
        })
    }

    /**
     * Creates UI component.
     * Returns JComponent for embedding in layout.
     */
    fun createUI(): JComponent {
        wrappingPanel = LgWrappingPanel(hgap = 16)
        return wrappingPanel
    }

    /**
     * Updates UI from PCEState.
     */
    private fun updateUI(state: PCEState) {
        suppressDispatch = true
        try {
            val prev = lastState

            if (prev == null || prev.configuration.modeSets != state.configuration.modeSets
                || prev.environment.branches != state.environment.branches
                || prev.persistent.modesByContextProvider != state.persistent.modesByContextProvider
                || prev.persistent.template != state.persistent.template) {
                rebuildUI(state)
            }

            if (prev == null || prev.environment.branches != state.environment.branches
                || prev.persistent.targetBranch != state.persistent.targetBranch) {
                updateTargetBranchCombo(state)
            }

            lastState = state
        } finally {
            suppressDispatch = false
        }
    }

    /**
     * Rebuilds UI with current data.
     */
    private fun rebuildUI(state: PCEState) {
        if (!::wrappingPanel.isInitialized) return

        // Clear existing components
        wrappingPanel.removeAll()

        val modeSetsSchema = state.configuration.modeSets
        if (modeSetsSchema.modeSets.isEmpty()) {
            // Empty state
            val emptyLabel = JBLabel(LgBundle.message("control.modes.empty")).apply {
                foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
            }
            wrappingPanel.add(emptyLabel)
        } else {
            val ctx = state.persistent.template
            val provider = state.persistent.providerId
            val currentModes = state.persistent.modesByContextProvider[ctx]?.get(provider) ?: emptyMap()

            // Create labeled ComboBox for each mode-set
            for (modeSet in modeSetsSchema.modeSets) {
                val modeIds = modeSet.modes.map { it.id }.toTypedArray()
                val combo = ComboBox(modeIds).apply {
                    // Custom renderer
                    renderer = SimpleListCellRenderer.create { label, value, _ ->
                        val mode = modeSet.modes.find { it.id == value }
                        label.text = mode?.title ?: value
                        if (mode?.description != null) {
                            label.toolTipText = mode.description
                        }
                    }

                    // Restore saved value or default to first mode
                    val savedMode = currentModes[modeSet.id]
                    val defaultMode = modeSet.modes.firstOrNull()?.id

                    if (savedMode != null && savedMode in modeIds) {
                        selectedItem = savedMode
                    } else if (defaultMode != null) {
                        selectedItem = defaultMode
                    }

                    // Listen to changes
                    addActionListener {
                        if (!suppressDispatch) {
                            val selectedMode = selectedItem as? String
                            if (selectedMode != null) {
                                scope.launch {
                                    coordinator.dispatch(
                                        SelectMode.create(SelectModePayload(modeSet.id, selectedMode))
                                    )
                                }
                            }
                        }
                    }
                }

                // Add labeled component
                val labeledCombo = LgLabeledComponent.create(
                    labelText = modeSet.title + ":",
                    component = combo
                )
                wrappingPanel.add(labeledCombo)
            }

            // Add target branch selector at the end
            addTargetBranchSelector(state)
        }

        // Refresh layout
        wrappingPanel.revalidate()
        wrappingPanel.repaint()
    }

    /**
     * Adds target branch selector at the end of panel.
     */
    private fun addTargetBranchSelector(state: PCEState) {
        if (!hasReviewMode(state)) {
            targetBranchCombo = null
            return
        }

        val combo = ComboBox<String>().apply {
            // At this stage, just a placeholder for layout (waiting for updateTargetBranchCombo call)
            isEnabled = false
            addItem(LgBundle.message("control.target.branch.no.git"))
        }

        targetBranchCombo = combo

        val labeledCombo = LgLabeledComponent.create(
            labelText = LgBundle.message("control.target.branch.label"),
            component = combo
        )
        wrappingPanel.add(labeledCombo)
    }

    /**
     * Updates target branch ComboBox content without full UI rebuild.
     */
    private fun updateTargetBranchCombo(state: PCEState) {
        val combo = targetBranchCombo ?: return

        if (!hasReviewMode(state)) {
            return
        }

        val branches = state.environment.branches
        val savedBranch = state.persistent.targetBranch

        // Remove all listeners
        val listeners = combo.actionListeners
        listeners.forEach { combo.removeActionListener(it) }

        // Remember current selection
        val currentSelection = combo.selectedItem as? String

        // Update items
        combo.removeAllItems()

        if (branches.isEmpty()) {
            combo.isEnabled = false
            combo.addItem(LgBundle.message("control.target.branch.no.git"))
        } else {
            combo.isEnabled = true
            branches.forEach { combo.addItem(it) }

            // Priority: saved from state > current selection > main/master > first
            when {
                // 1. Restore from state (priority)
                !savedBranch.isBlank() && savedBranch in branches -> {
                    combo.selectedItem = savedBranch
                }
                // 2. Keep current selection if still valid
                currentSelection != null && currentSelection in branches -> {
                    combo.selectedItem = currentSelection
                }
                // 3. Fallback: search for main/master or first in list
                else -> {
                    val defaultBranch = findDefaultBranch(branches)
                    if (defaultBranch != null) {
                        combo.selectedItem = defaultBranch
                    } else if (branches.isNotEmpty()) {
                        combo.selectedIndex = 0
                    }
                }
            }
        }

        // Set listener AFTER filling and selecting the right item
        combo.addActionListener {
            if (!suppressDispatch) {
                val selected = combo.selectedItem as? String
                if (selected != null && selected != LgBundle.message("control.target.branch.no.git")) {
                    scope.launch {
                        coordinator.dispatch(
                            SelectBranch.create(SelectBranchPayload(selected))
                        )
                    }
                }
            }
        }
    }
    
    /**
     * Searches for default parent branch (main or master).
     * Returns the first found in priority order.
     */
    private fun findDefaultBranch(branches: List<String>): String? {
        // Default branch name options in priority order
        val defaultBranchNames = listOf("main", "master", "origin/main", "origin/master")

        for (defaultName in defaultBranchNames) {
            val found = branches.find { it == defaultName || it.endsWith("/$defaultName") }
            if (found != null) {
                return found
            }
        }

        return null
    }

    /**
     * Checks for "review" mode in current selections.
     */
    private fun hasReviewMode(state: PCEState): Boolean {
        val ctx = state.persistent.template
        val provider = state.persistent.providerId
        val modes = state.persistent.modesByContextProvider[ctx]?.get(provider) ?: emptyMap()
        return modes.values.any { it == "review" }
    }

    override fun dispose() {
        scope.cancel()
        LOG.debug("LgModeSetsPanel disposed")
    }

    companion object {
        private val LOG = logger<LgModeSetsPanel>()
    }
}