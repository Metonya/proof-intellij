package dev.proofjava.intellij.core.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import dev.proofjava.intellij.core.state.CoverageStateService
import dev.proofjava.intellij.core.ui.gutter.applyGutterCoverage
import dev.proofjava.intellij.core.ui.gutter.clearGutterCoverage

/**
 * Port of `proof-vscode/src/ui/commands.ts`'s `toggleCoverage`. A plain
 * function, not just an `AnAction` body, so the status bar widget's click
 * handler can call the exact same logic without synthesizing a fake
 * `AnActionEvent` (mirrors the TS source's own status-bar item having its
 * `command` set to `proof.toggleCoverage` - same command, two entry
 * points).
 */
fun toggleCoverage(project: Project) {
    val service = CoverageStateService.getInstance(project)
    val state = service.state
    if (state?.fileCoverage == null) {
        Messages.showInfoMessage(project, "No coverage data yet - run Quick Scan first.", "Proof")
        return
    }
    val nextVisible = !service.gutterVisible
    service.setGutterVisible(nextVisible)
    if (nextVisible) {
        applyGutterCoverage(project, state.projectRoot, state.fileCoverage)
    } else {
        clearGutterCoverage(project)
    }
}

class ToggleCoverageAction : AnAction("Proof: Toggle Coverage View") {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        toggleCoverage(e.project ?: return)
    }
}
