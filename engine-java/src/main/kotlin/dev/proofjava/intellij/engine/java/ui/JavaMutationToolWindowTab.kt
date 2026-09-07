package dev.proofjava.intellij.engine.java.ui

import com.intellij.openapi.project.Project
import dev.proofjava.intellij.core.ui.toolwindow.ProofToolWindowTab
import javax.swing.JComponent

/** Registers [MutationToolWindowPanel] as a tab of the "Proof" tool window via `core.ui.toolwindow.ProofToolWindowTab` - same pattern [JavaLineTestsToolWindowTab] already established (M6). */
class JavaMutationToolWindowTab : ProofToolWindowTab {
    override val title: String = "Mutation"

    override fun createComponent(project: Project): JComponent = MutationToolWindowPanel(project)
}
