package dev.proofjava.intellij.engine.java.ui

import com.intellij.openapi.project.Project
import dev.proofjava.intellij.core.ui.toolwindow.ProofToolWindowTab
import javax.swing.JComponent

/**
 * Registers [LineTestsToolWindowPanel] as a tab of the "Proof" tool
 * window via `core.ui.toolwindow.ProofToolWindowTab` - replaces the
 * earlier "Proof: Line to Tests" *separate* top-level tool window, which
 * real user feedback found confusing (a live `runIde` session,
 * 2026-09-07: two windows to hunt for instead of one, tabbed one).
 */
class JavaLineTestsToolWindowTab : ProofToolWindowTab {
    override val title: String = "Line → Tests"

    override fun createComponent(project: Project): JComponent = LineTestsToolWindowPanel(project)
}
