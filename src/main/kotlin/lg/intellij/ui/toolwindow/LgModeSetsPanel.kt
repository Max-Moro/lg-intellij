package lg.intellij.ui.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import lg.intellij.LgBundle
import lg.intellij.models.ModeSet
import lg.intellij.services.catalog.LgCatalogService
import lg.intellij.services.state.LgPanelStateService
import lg.intellij.ui.components.LgLabeledComponent
import lg.intellij.ui.components.LgWrappingPanel
import javax.swing.JComponent

/**
 * Самостоятельный panel для управления режимами (mode-sets).
 *
 * Полностью инкапсулирует:
 * - Загрузку mode-sets из LgCatalogService
 * - Управление состоянием через LgPanelStateService
 * - Динамическое обновление UI при изменении данных
 * - Target branch selector (показывается при review режиме)
 */
class LgModeSetsPanel(
    project: Project,
    parentDisposable: Disposable
) : Disposable {

    private val catalogService = project.service<LgCatalogService>()
    private val stateService = project.service<LgPanelStateService>()

    // Coroutine scope (auto-cancelled on dispose)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Track current mode-sets data
    private var currentModeSets: List<ModeSet> = emptyList()
    
    // Track current branches data
    private var currentBranches: List<String> = emptyList()

    // UI references
    private lateinit var wrappingPanel: LgWrappingPanel
    private var targetBranchCombo: ComboBox<String>? = null

    init {
        // Register as child disposable
        Disposer.register(parentDisposable, this)

        // Subscribe to catalog updates
        scope.launch {
            catalogService.modeSets.collectLatest { modeSets ->
                currentModeSets = modeSets?.modeSets ?: emptyList()
                LOG.debug("Mode-sets updated: ${currentModeSets.size} sets")

                withContext(Dispatchers.EDT) {
                    rebuildUI()
                }
            }
        }
        
        // Subscribe to branches updates
        scope.launch {
            catalogService.branches.collectLatest { branches ->
                currentBranches = branches
                LOG.debug("Branches updated: ${branches.size} branches")

                withContext(Dispatchers.EDT) {
                    updateTargetBranchCombo()
                }
            }
        }
    }

    /**
     * Создаёт UI компонент.
     * Возвращает JComponent для встраивания в layout.
     */
    fun createUI(): JComponent {
        wrappingPanel = LgWrappingPanel(hgap = 16)
        rebuildUI()
        return wrappingPanel
    }

    /**
     * Перестраивает UI с текущими данными.
     */
    private fun rebuildUI() {
        if (!::wrappingPanel.isInitialized) return

        // Clear existing components
        wrappingPanel.removeAll()

        if (currentModeSets.isEmpty()) {
            // Empty state
            val emptyLabel = JBLabel(LgBundle.message("control.modes.empty")).apply {
                foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
            }
            wrappingPanel.add(emptyLabel)
        } else {
            // Create labeled ComboBox for each mode-set
            for (modeSet in currentModeSets) {
                val combo = ComboBox(modeSet.modes.map { it.id }.toTypedArray()).apply {
                    // Custom renderer
                    renderer = SimpleListCellRenderer.create { label, value, _ ->
                        val mode = modeSet.modes.find { it.id == value }
                        label.text = mode?.title ?: value
                        if (mode?.description != null) {
                            label.toolTipText = mode.description
                        }
                    }

                    // Restore saved value or default to first mode
                    val savedMode = stateService.state.modes[modeSet.id]
                    val defaultMode = modeSet.modes.firstOrNull()?.id

                    if (savedMode != null && savedMode in modeSet.modes.map { it.id }) {
                        selectedItem = savedMode
                    } else if (defaultMode != null) {
                        selectedItem = defaultMode
                        stateService.state.modes[modeSet.id] = defaultMode
                    }

                    // Listen to changes
                    addActionListener {
                        val selectedMode = selectedItem as? String
                        if (selectedMode != null) {
                            stateService.state.modes[modeSet.id] = selectedMode
                            LOG.debug("Mode changed: ${modeSet.id} -> $selectedMode")

                            // Update target branch visibility
                            updateTargetBranchVisibility()
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
            addTargetBranchSelector()
        }

        // Refresh layout
        wrappingPanel.revalidate()
        wrappingPanel.repaint()
    }

    /**
     * Добавляет target branch selector в конец panel.
     */
    private fun addTargetBranchSelector() {
        if (!hasReviewMode()) {
            targetBranchCombo = null
            return
        }

        val combo = ComboBox<String>().apply {
            // На данном этапе просто плейсхолдер для верстки (ждем вызова updateTargetBranchCombo)
            isEnabled = false
            addItem(LgBundle.message("control.target.branch.no.git"))
        }
        
        targetBranchCombo = combo

        val labeledCombo = LgLabeledComponent.create(
            labelText = LgBundle.message("control.target.branch.label"),
            component = combo
        )
        wrappingPanel.add(labeledCombo)
        
        // Если ветки еще не загружены, триггерим их загрузку
        if (currentBranches.isEmpty()) {
            scope.launch {
                catalogService.loadBranchesOnly()
            }
        } else {
            updateTargetBranchCombo()
        }
    }
    
    /**
     * Обновляет содержимое target branch ComboBox без полного rebuild UI.
     */
    private fun updateTargetBranchCombo() {
        val combo = targetBranchCombo ?: return
        
        if (!hasReviewMode()) {
            return
        }
        
        // Удаляем все listeners
        val listeners = combo.actionListeners
        listeners.forEach { combo.removeActionListener(it) }

        // Remember current selection
        val currentSelection = combo.selectedItem as? String

        // Update items
        combo.removeAllItems()

        if (currentBranches.isEmpty()) {
            combo.isEnabled = false
            combo.addItem(LgBundle.message("control.target.branch.no.git"))
        } else {
            combo.isEnabled = true
            currentBranches.forEach { combo.addItem(it) }

            // Приоритет: сохраненная из state > текущая выбранная > main/master > первая
            val savedBranch = stateService.state.targetBranch

            when {
                // 1. Восстановить из state (приоритет)
                !savedBranch.isNullOrBlank() && savedBranch in currentBranches -> {
                    combo.selectedItem = savedBranch
                }
                // 2. Сохранить текущий выбор если он еще актуален
                currentSelection != null && currentSelection in currentBranches -> {
                    combo.selectedItem = currentSelection
                }
                // 3. Fallback: искать main/master или первая в списке
                else -> {
                    val defaultBranch = findDefaultBranch(currentBranches)
                    if (defaultBranch != null) {
                        combo.selectedItem = defaultBranch
                        stateService.state.targetBranch = defaultBranch
                    } else if (currentBranches.isNotEmpty()) {
                        combo.selectedIndex = 0
                        stateService.state.targetBranch = currentBranches[0]
                    }
                }
            }
        }

        // Установить listener ПОСЛЕ того как заполнили и выбрали нужный item
        combo.addActionListener {
            val selected = combo.selectedItem as? String
            if (selected != null && selected != LgBundle.message("control.target.branch.no.git")) {
                stateService.state.targetBranch = selected
            }
        }
    }
    
    /**
     * Ищет дефолтную родительскую ветку (main или master).
     * Возвращает первую найденную в порядке приоритета.
     */
    private fun findDefaultBranch(branches: List<String>): String? {
        // Варианты дефолтных веток в порядке приоритета
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
     * Обновляет видимость target branch selector на основе текущих режимов.
     */
    private fun updateTargetBranchVisibility() {
        // Full rebuild to add/remove target branch selector
        rebuildUI()
    }

    /**
     * Проверяет наличие режима "review" в текущих выборах.
     */
    private fun hasReviewMode(): Boolean {
        return stateService.state.modes.values.any { it == "review" }
    }

    override fun dispose() {
        scope.cancel()
        LOG.debug("LgModeSetsPanel disposed")
    }

    companion object {
        private val LOG = logger<LgModeSetsPanel>()
    }
}