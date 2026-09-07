package dev.proofjava.intellij.core.ui.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class CoverageToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = CoverageToolWindowPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        // Ties the panel's listener subscription (CoverageStateService.addListener)
        // to this content's own lifecycle, not just the project's - correct even
        // if a tool window's content is someday recreated within one project session.
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }
}
