package dev.proofjava.intellij.core.model

import dev.proofjava.intellij.core.verdict.ChangedFile
import dev.proofjava.intellij.core.verdict.ChangedFileClassification
import dev.proofjava.intellij.core.verdict.Metric
import dev.proofjava.intellij.core.verdict.MetricSet
import dev.proofjava.intellij.core.verdict.NewCodeCoverage
import dev.proofjava.intellij.core.verdict.Reason

/**
 * Port of `proof-vscode/src/ui/treeViews/coverageView.ts`'s node-building
 * logic - deliberately kept pure (no Swing/IntelliJ import) so it is
 * directly unit-testable, matching this project's own discipline of
 * separating "what the tree contains" from "how it's painted"
 * (`core.ui.toolwindow.CoverageToolWindowPanel` does the Swing wiring).
 *
 * Deferred from this port: `diffModeDetail()` (a one-line "which diff mode
 * is active" hint on the "no changes" status) - it reads a settings value
 * that doesn't exist yet in this milestone (no Configurable/settings
 * service built), so it's simply omitted rather than guessed at.
 */

enum class CoverageSectionId { OVERALL, NEW_CODE, UNCOVERED, WARNINGS }

sealed interface CoverageNode {
    data class Empty(val message: String) : CoverageNode
    data class Section(val id: CoverageSectionId) : CoverageNode
    data class MetricNode(val name: String, val metric: Metric) : CoverageNode
    data class NewCodeStatus(val status: String, val detail: String? = null) : CoverageNode
    data class ChangedFileNode(val file: ChangedFile) : CoverageNode
    data class RangeNode(val file: ChangedFile, val range: Pair<Int, Int>) : CoverageNode
    data class WarningNode(val reason: Reason) : CoverageNode
}

fun coverageRootChildren(state: CoverageState?): List<CoverageNode> {
    if (state == null) {
        return listOf(CoverageNode.Empty("Run Quick Scan first."))
    }
    val sections = mutableListOf(
        CoverageNode.Section(CoverageSectionId.OVERALL),
        CoverageNode.Section(CoverageSectionId.NEW_CODE),
        CoverageNode.Section(CoverageSectionId.UNCOVERED),
    )
    if (state.warnings.isNotEmpty()) {
        sections += CoverageNode.Section(CoverageSectionId.WARNINGS)
    }
    return sections
}

fun coverageSectionChildren(id: CoverageSectionId, state: CoverageState): List<CoverageNode> = when (id) {
    CoverageSectionId.OVERALL -> metricNodes(state.overall)
    CoverageSectionId.NEW_CODE -> newCodeChildren(state.newCode, state.changedFiles, state.warnings)
    CoverageSectionId.WARNINGS -> state.warnings.map { CoverageNode.WarningNode(it) }
    CoverageSectionId.UNCOVERED -> {
        val uncoveredFiles = state.changedFiles.filter {
            it.classification == ChangedFileClassification.MAPPED && (it.uncoveredNewRanges?.size ?: 0) > 0
        }
        if (uncoveredFiles.isEmpty()) listOf(CoverageNode.Empty("No uncovered new lines.")) else uncoveredFiles.map { CoverageNode.ChangedFileNode(it) }
    }
}

fun coverageChangedFileChildren(file: ChangedFile): List<CoverageNode> =
    (file.uncoveredNewRanges ?: emptyList()).map { CoverageNode.RangeNode(file, it) }

private fun newCodeChildren(newCode: NewCodeCoverage, changedFiles: List<ChangedFile>, warnings: List<Reason>): List<CoverageNode> {
    if (newCode is NewCodeCoverage.Status) {
        return listOf(CoverageNode.NewCodeStatus(newCode.status))
    }
    val metricSet = (newCode as NewCodeCoverage.Metrics).metricSet
    if (changedFiles.isEmpty()) {
        return listOf(CoverageNode.NewCodeStatus("no-changes"))
    }
    val stale = warnings.find { it.code == "CHANGED_LINES_ABSENT_FROM_REPORT" }
    if (stale != null) {
        return listOf(CoverageNode.NewCodeStatus("stale-report", stale.message)) + metricNodes(metricSet)
    }
    return metricNodes(metricSet)
}

private fun metricNodes(set: MetricSet): List<CoverageNode> = listOf(
    CoverageNode.MetricNode("jacoco-line", set.jacocoLine),
    CoverageNode.MetricNode("strict-line", set.strictLine),
    CoverageNode.MetricNode("sonar-compatible", set.sonarCompatible),
)

/** Same wording as `coverageView.ts`'s `percentText`. */
fun percentText(metric: Metric): String = if (metric.percent == null) "n/a" else "${metric.percent}% (${metric.numerator}/${metric.denominator})"

/** Same wording as `coverageView.ts`'s `newCodeStatusText`. */
fun newCodeStatusText(status: String): String = when (status) {
    "unavailable_no_vcs" -> "new code cannot be computed in no-vcs mode"
    "unavailable_incomplete" -> "an error occurred during the diff, new code could not be computed"
    "no-changes" -> "no files changed in this diff"
    "stale-report" -> "changed lines are absent from the report - it may be older than this diff"
    else -> status
}

/** Same wording as `coverageView.ts`'s `metricTooltip` - based on `MetricsEngine.java`/D-04, not guessed. */
fun metricTooltip(name: String): String = when (name) {
    "jacoco-line" -> "A line counts as covered if any instruction on it ran - the most generous number, identical to JaCoCo's own raw line coverage."
    "strict-line" -> "A line only counts as covered if EVERY instruction on it ran - the strictest number, usually the lowest."
    "sonar-compatible" -> "Adds branch coverage on top of JaCoCo line coverage - matches the percentage SonarQube shows within ±0.1, so it usually comes out lower than jacoco-line."
    else -> ""
}
