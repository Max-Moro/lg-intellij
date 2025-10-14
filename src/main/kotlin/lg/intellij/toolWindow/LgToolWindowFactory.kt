package lg.intellij.toolWindow

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import lg.intellij.LgBundle
import lg.intellij.services.LgProjectService
import javax.swing.JButton


class LgToolWindowFactory : ToolWindowFactory, DumbAware {

    init {
        thisLogger().info("LgToolWindowFactory initialized")
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val lgToolWindow = LgToolWindow(toolWindow)
        val content = ContentFactory.getInstance().createContent(lgToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class LgToolWindow(toolWindow: ToolWindow) {

        private val service = toolWindow.project.service<LgProjectService>()

        fun getContent() = JBPanel<JBPanel<*>>().apply {
            val label = JBLabel(LgBundle.message("randomLabel", "?"))

            add(label)
            add(JButton(LgBundle.message("shuffle")).apply {
                addActionListener {
                    label.text = LgBundle.message("randomLabel", service.getRandomNumber())
                }
            })
        }
    }
}
