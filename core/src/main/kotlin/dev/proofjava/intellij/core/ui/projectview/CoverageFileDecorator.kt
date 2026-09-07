package dev.proofjava.intellij.core.ui.projectview

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import dev.proofjava.intellij.core.model.BadgeMetric
import dev.proofjava.intellij.core.model.forBadgeMetric
import dev.proofjava.intellij.core.model.rollupFolder
import dev.proofjava.intellij.core.state.CoverageStateService
import java.io.File

/**
 * IntelliJ port of `proof-vscode/src/ui/explorerBadges.ts`, via the
 * `com.intellij.projectViewNodeDecorator` extension point (the direct
 * analogue of VS Code's `FileDecorationProvider`). A file's badge is the
 * CLI's own already-computed percentage; a folder's is the one arithmetic
 * this plugin does itself ([rollupFolder]).
 *
 * Simpler than the TS source in one specific way, not an oversight: VS
 * Code's badge string is capped at two code points (a real VS Code
 * constraint - a longer string silently drops the whole decoration), which
 * is why the TS source special-cases 100% as `✓`. `PresentationData.locationString`
 * has no such limit, so the real percentage is always shown directly.
 *
 * Deferred from this port: the "excluded" and "stale" badge states (a file
 * matching `coverageExclusions`, or edited since the last scan) - real,
 * disclosed gaps, not oversights; a file simply shows no badge in either
 * case for now rather than a wrong one.
 */
class CoverageFileDecorator : ProjectViewNodeDecorator {
    override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
        val project = node.project ?: return
        val service = CoverageStateService.getInstance(project)
        if (!service.gutterVisible) return
        val state = service.state ?: return
        val fileCoverage = state.fileCoverage ?: return
        val virtualFile = node.virtualFile ?: return

        val fileEntry = fileCoverage.files.find { File(state.projectRoot, it.path).path == virtualFile.path }
        if (fileEntry != null) {
            val percent = fileEntry.metrics.forBadgeMetric(BadgeMetric.SONAR_COMPATIBLE).percent ?: return
            data.locationString = formatPercent(percent)
            return
        }

        if (virtualFile.isDirectory) {
            val prefix = virtualFile.path + "/"
            val childFiles = fileCoverage.files.filter { File(state.projectRoot, it.path).path.startsWith(prefix) }
            if (childFiles.isEmpty()) return
            val rollup = rollupFolder(childFiles, BadgeMetric.SONAR_COMPATIBLE)
            val percent = rollup.percent ?: return
            data.locationString = formatPercent(percent)
        }
    }
}

private fun formatPercent(percent: Double): String {
    val rounded = Math.round(percent)
    return "$rounded%"
}
