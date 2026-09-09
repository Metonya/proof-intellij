package dev.proofjava.intellij.core.model

import dev.proofjava.intellij.core.verdict.FileCoverageEntry
import dev.proofjava.intellij.core.verdict.Metric
import dev.proofjava.intellij.core.verdict.MetricSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Kotlin port of `proof-vscode/src/test/unit/model/metrics.test.ts`. */
class FolderRollupTest {

    private fun fileWith(numerator: Long, denominator: Long): FileCoverageEntry {
        val percent = if (denominator == 0L) null else Math.round((numerator.toDouble() / denominator) * 1000) / 10.0
        val metric = Metric("a", numerator, "b", denominator, percent)
        return FileCoverageEntry(module = "root", path = "x", metrics = MetricSet(metric, metric, metric), lines = emptyList())
    }

    @Test
    fun `sums numerators and denominators across files, one division`() {
        val rollup = rollupFolder(listOf(fileWith(8, 10), fileWith(2, 10)), BadgeMetric.JACOCO_LINE)
        assertEquals(10, rollup.numerator)
        assertEquals(20, rollup.denominator)
        assertEquals(50.0, rollup.percent)
    }

    @Test
    fun `an empty file list has a null percent, not a divide-by-zero NaN`() {
        val rollup = rollupFolder(emptyList(), BadgeMetric.JACOCO_LINE)
        assertEquals(0, rollup.denominator)
        assertNull(rollup.percent)
    }

    @Test
    fun `a folder where every file has denominator 0 also stays null`() {
        val rollup = rollupFolder(listOf(fileWith(0, 0), fileWith(0, 0)), BadgeMetric.SONAR_COMPATIBLE)
        assertNull(rollup.percent)
    }
}
