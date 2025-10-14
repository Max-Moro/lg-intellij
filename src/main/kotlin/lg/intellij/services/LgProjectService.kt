package lg.intellij.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import lg.intellij.LgBundle

@Service(Service.Level.PROJECT)
class LgProjectService(project: Project) {

    init {
        thisLogger().info(LgBundle.message("projectService", project.name))
        // TODO: Remove sample code
    }

    fun getRandomNumber() = (1..100).random()
}
