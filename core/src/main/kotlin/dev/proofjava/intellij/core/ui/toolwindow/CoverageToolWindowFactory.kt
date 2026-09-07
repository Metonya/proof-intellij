package dev.proofjava.intellij.core.ui.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/** Title-bar buttons for the tool window's own most common actions - added here, once, since it needs the real registered [com.intellij.openapi.actionSystem.AnAction] instances by id (`plugin.xml`'s `<actions>` block), not a re-declaration. Real user feedback (a live `runIde` session, 2026-09-07): reaching every action through the Tools menu was tiring - these mirror what a VS Code view-title-bar icon row already gives that extension's users. */
private val TITLE_ACTION_IDS = listOf(
    "dev.proofjava.intellij.RunTests",
    "dev.proofjava.intellij.QuickScan",
    "dev.proofjava.intellij.DeepScan",
    "dev.proofjava.intellij.ToggleCoverage",
)

class CoverageToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = CoverageToolWindowPanel(project)
        val coverageContent = ContentFactory.getInstance().createContent(panel, "Coverage", false)
        // Ties the panel's listener subscription (CoverageStateService.addListener)
        // to this content's own lifecycle, not just the project's - correct even
        // if a tool window's content is someday recreated within one project session.
        coverageContent.setDisposer(panel)
        toolWindow.contentManager.addContent(coverageContent)

        for (tab in ProofToolWindowTab.EP_NAME.extensionList) {
            val component = tab.createComponent(project)
            val content = ContentFactory.getInstance().createContent(component, tab.title, false)
            (component as? Disposable)?.let { content.setDisposer(it) }
            toolWindow.contentManager.addContent(content)
        }

        val actionManager = ActionManager.getInstance()
        toolWindow.setTitleActions(TITLE_ACTION_IDS.mapNotNull { actionManager.getAction(it) })
    }
}
