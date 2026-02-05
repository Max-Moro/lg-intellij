package lg.intellij.statepce

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * Project-level service managing PCEStateCoordinator lifecycle.
 *
 * Replaces manual bootstrap: the coordinator is created lazily on first access
 * and disposed automatically when the project closes.
 */
@Service(Service.Level.PROJECT)
class LgCoordinatorService(
    private val project: Project
) : Disposable {

    val coordinator: PCEStateCoordinator by lazy {
        createPCECoordinator(PCEStateStore.getInstance(project), project)
    }

    override fun dispose() {
        coordinator.dispose()
    }

    companion object {
        fun getInstance(project: Project): LgCoordinatorService = project.service()
    }
}
