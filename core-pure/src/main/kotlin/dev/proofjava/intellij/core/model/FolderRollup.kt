package dev.proofjava.intellij.core.model

import dev.proofjava.intellij.core.verdict.FileCoverageEntry
import dev.proofjava.intellij.core.verdict.Metric
import dev.proofjava.intellij.core.verdict.MetricSet

/**
 * Port of `proof-vscode/src/model/metrics.ts` - the one arithmetic this
 * plugin is allowed to do itself: a *folder's* percentage, by summing the
 * files under it. A *file's* percentage is never recomputed, only read
 * from the CLI's own already-computed `Metric.percent` (BigDecimal
 * HALF_UP on the CLI side is not float-safely reproducible here).
 */

/** One of the three metric modes a badge/status-bar/gutter can be driven from. */
/** ENGINE_LINE is whichever engine-named first mode the document carries (proof-java D-99). */
enum class BadgeMetric { ENGINE_LINE, STRICT_LINE, SONAR_COMPATIBLE }

data class FolderRollup(val numerator: Long, val denominator: Long, val percent: Double?)

fun rollupFolder(files: List<FileCoverageEntry>, metric: BadgeMetric): FolderRollup {
    var numerator = 0L
    var denominator = 0L
    for (file in files) {
        val m = file.metrics.forBadgeMetric(metric)
        numerator += m.numerator
        denominator += m.denominator
    }
    // Math.round(x * 1000) / 10.0 mirrors the TS source's own rounding
    // (one decimal place) exactly, including its half-up-at-.5 behavior.
    val percent = if (denominator == 0L) null else Math.round((numerator.toDouble() / denominator) * 1000) / 10.0
    return FolderRollup(numerator, denominator, percent)
}

fun MetricSet.forBadgeMetric(metric: BadgeMetric): Metric = when (metric) {
    BadgeMetric.ENGINE_LINE -> engineLine
    BadgeMetric.STRICT_LINE -> strictLine
    BadgeMetric.SONAR_COMPATIBLE -> sonarCompatible
}
