package dev.proofjava.intellij.core.startup

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import dev.proofjava.intellij.core.model.PerTestState
import dev.proofjava.intellij.core.model.coverageStateFrom
import dev.proofjava.intellij.core.state.CoverageStateService
import dev.proofjava.intellij.core.state.PerTestStateService
import dev.proofjava.intellij.core.ui.gutter.applyGutterCoverage
import dev.proofjava.intellij.core.verdict.ParseResult
import dev.proofjava.intellij.core.verdict.parsePerTestSnapshot
import dev.proofjava.intellij.core.verdict.parseVerdict
import java.io.File

/**
 * Restores the last scan's results on project open - port of
 * `extension.ts`'s `restoreLastCoverage`/`restoreLastCoverageFrom`. The
 * CLI's own output is already sitting on disk from the last run
 * (`.proof/verdict-current.json`/`pertest-current.json`) - a window
 * reload/reopen should not force a fresh scan just to see it again. Real
 * user request (2026-09-07), after directly comparing against
 * `proof-vscode`'s own behavior on the same repo side by side.
 *
 * `pertest-current.json` and `verdict-current.json` restore
 * independently (same reasoning as the TS source: one missing/corrupt
 * must not take the other down) - when the per-test snapshot restored
 * successfully, `verdict-current.json`'s own (possibly absent or stale)
 * `perTest` block is not used to overwrite it.
 */
class RestoreLastScanActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val repo = project.basePath ?: return
        val perTestRestored = restorePerTestSnapshot(project, repo)
        restoreVerdictSnapshot(project, repo, perTestRestored)
    }
}

private fun restorePerTestSnapshot(project: Project, repo: String): Boolean {
    val raw = runCatching { File(File(repo, ".proof"), "pertest-current.json").readText() }.getOrNull()
        ?: return false // nothing saved yet - the normal first-run shape, not an error
    val snapshot = parsePerTestSnapshot(raw) ?: return false
    ApplicationManager.getApplication().invokeLater {
        PerTestStateService.getInstance(project).publish(
            PerTestState(perTest = snapshot.perTest, warnings = snapshot.warnings, targets = snapshot.targets, ranAt = snapshot.ranAtMs),
        )
    }
    return true
}

private fun restoreVerdictSnapshot(project: Project, repo: String, perTestRestored: Boolean) {
    val raw = runCatching { File(File(repo, ".proof"), "verdict-current.json").readText() }.getOrNull() ?: return
    val parsed = parseVerdict(raw)
    if (parsed !is ParseResult.Ok) return
    val document = parsed.value

    ApplicationManager.getApplication().invokeLater {
        val coverageState = coverageStateFrom(repo, document)
        CoverageStateService.getInstance(project).publish(coverageState)
        coverageState.fileCoverage?.let { applyGutterCoverage(project, repo, it) }

        // Same precedence as extension.ts's own restoreLastCoverageFrom:
        // verdict-current.json carries no timestamp/explicit-targets of
        // its own (that's exactly why the pertest snapshot exists as a
        // separate file) - only used as a perTest fallback when the real
        // snapshot did not restore.
        if (!perTestRestored) {
            PerTestStateService.getInstance(project).publish(
                PerTestState(perTest = document.perTest, warnings = document.warnings, targets = emptyList(), ranAt = null),
            )
        }
    }
}
