package dev.proofjava.intellij.core.model

import dev.proofjava.intellij.core.verdict.ChangedFile
import dev.proofjava.intellij.core.verdict.ChangedFileClassification
import dev.proofjava.intellij.core.verdict.Metric
import dev.proofjava.intellij.core.verdict.MetricSet
import dev.proofjava.intellij.core.verdict.NewCodeCoverage
import dev.proofjava.intellij.core.verdict.Reason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * No direct proof-vscode test exists for `coverageView.ts`'s node-building
 * logic (tree data providers there are only integration/manually
 * verified) - separating this into a pure `core.model` function (unlike
 * the TS source, which has it fused with `vscode.TreeDataProvider`) makes
 * it directly testable, so it is tested here for real.
 */
class CoverageTreeTest {

    private fun metric(percent: Double? = 50.0) = Metric("num", 1, "den", 2, percent)
    private fun metricSet() = MetricSet(MetricSet.JACOCO_LINE, metric(), metric(), metric())

    private fun state(
        changedFiles: List<ChangedFile> = emptyList(),
        warnings: List<Reason> = emptyList(),
        newCode: NewCodeCoverage = NewCodeCoverage.Status("unavailable_no_vcs"),
    ) = CoverageState(
        projectRoot = "/repo",
        fileCoverage = null,
        overall = metricSet(),
        newCode = newCode,
        changedFiles = changedFiles,
        findings = emptyList(),
        warnings = warnings,
        modules = emptyList(),
    )

    @Test
    fun `no state means a single empty node`() {
        val children = coverageRootChildren(null)
        assertEquals(1, children.size)
        assertTrue(children[0] is CoverageNode.Empty)
    }

    @Test
    fun `three sections without warnings, four with`() {
        assertEquals(3, coverageRootChildren(state()).size)
        val withWarnings = coverageRootChildren(state(warnings = listOf(Reason("CODE", "msg"))))
        assertEquals(4, withWarnings.size)
        assertTrue(withWarnings.last().let { it is CoverageNode.Section && it.id == CoverageSectionId.WARNINGS })
    }

    @Test
    fun `overall section yields the three metric modes in order`() {
        val children = coverageSectionChildren(CoverageSectionId.OVERALL, state())
        assertEquals(listOf("jacoco-line", "strict-line", "sonar-compatible"), children.map { (it as CoverageNode.MetricNode).name })
    }

    @Test
    fun `new code as a status object yields a single status node`() {
        val children = coverageSectionChildren(CoverageSectionId.NEW_CODE, state(newCode = NewCodeCoverage.Status("unavailable_no_vcs")))
        assertEquals(listOf(CoverageNode.NewCodeStatus("unavailable_no_vcs")), children)
    }

    @Test
    fun `new code as a real metric set with no changed files means no-changes`() {
        val children = coverageSectionChildren(CoverageSectionId.NEW_CODE, state(newCode = NewCodeCoverage.Metrics(metricSet()), changedFiles = emptyList()))
        assertEquals(listOf(CoverageNode.NewCodeStatus("no-changes")), children)
    }

    @Test
    fun `new code with changed files and no staleness warning yields the three metrics`() {
        val file = ChangedFile(path = "A.java", classification = ChangedFileClassification.MAPPED)
        val children = coverageSectionChildren(CoverageSectionId.NEW_CODE, state(newCode = NewCodeCoverage.Metrics(metricSet()), changedFiles = listOf(file)))
        assertEquals(3, children.size)
        assertTrue(children.all { it is CoverageNode.MetricNode })
    }

    @Test
    fun `a staleness warning prefixes the metrics with a stale-report status`() {
        val file = ChangedFile(path = "A.java", classification = ChangedFileClassification.MAPPED)
        val warning = Reason(code = "CHANGED_LINES_ABSENT_FROM_REPORT", message = "3 lines missing")
        val children = coverageSectionChildren(CoverageSectionId.NEW_CODE, state(newCode = NewCodeCoverage.Metrics(metricSet()), changedFiles = listOf(file), warnings = listOf(warning)))
        assertEquals(4, children.size)
        assertEquals(CoverageNode.NewCodeStatus("stale-report", "3 lines missing"), children[0])
    }

    @Test
    fun `uncovered section only lists mapped files with at least one uncovered range`() {
        val mappedWithRanges = ChangedFile(path = "A.java", classification = ChangedFileClassification.MAPPED, uncoveredNewRanges = listOf(1 to 2))
        val mappedWithoutRanges = ChangedFile(path = "B.java", classification = ChangedFileClassification.MAPPED, uncoveredNewRanges = emptyList())
        val excluded = ChangedFile(path = "C.java", classification = ChangedFileClassification.EXCLUDED)
        val children = coverageSectionChildren(CoverageSectionId.UNCOVERED, state(changedFiles = listOf(mappedWithRanges, mappedWithoutRanges, excluded)))
        assertEquals(1, children.size)
        assertEquals("A.java", (children[0] as CoverageNode.ChangedFileNode).file.path)
    }

    @Test
    fun `uncovered section is a single empty node when nothing qualifies`() {
        val children = coverageSectionChildren(CoverageSectionId.UNCOVERED, state())
        assertEquals(1, children.size)
        assertTrue(children[0] is CoverageNode.Empty)
    }

    @Test
    fun `warnings section maps one warning node per reason, in order`() {
        val warnings = listOf(Reason("A", "a"), Reason("B", "b"))
        val children = coverageSectionChildren(CoverageSectionId.WARNINGS, state(warnings = warnings))
        assertEquals(warnings.map { CoverageNode.WarningNode(it) }, children)
    }

    @Test
    fun `changed file children are one range node per uncovered range, preserving order`() {
        val file = ChangedFile(path = "A.java", classification = ChangedFileClassification.MAPPED, uncoveredNewRanges = listOf(42 to 44, 51 to 51))
        val children = coverageChangedFileChildren(file)
        assertEquals(listOf(CoverageNode.RangeNode(file, 42 to 44), CoverageNode.RangeNode(file, 51 to 51)), children)
    }

    @Test
    fun `percentText shows n-a for a null percent, never a coerced number`() {
        assertEquals("n/a", percentText(metric(percent = null)))
        assertEquals("50.0% (1/2)", percentText(metric(percent = 50.0)))
    }
}
