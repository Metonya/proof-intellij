package dev.proofjava.intellij.core.verdict

/**
 * Port of `proof-vscode/src/verdict/types.ts`'s `Metric`/`MetricSet`/
 * `NewCodeCoverage`. `percent` is `Double?` because a zero-denominator
 * metric legitimately has no percentage - never coerce it to 0.0.
 */
data class Metric(
    val numeratorName: String,
    val numerator: Long,
    val denominatorName: String,
    val denominator: Long,
    val percent: Double?,
)

data class MetricSet(
    val jacocoLine: Metric,
    val strictLine: Metric,
    val sonarCompatible: Metric,
)

/** `coverage.newCode` is either a real `MetricSet` or, when the diff produced nothing measurable, a bare `{status}` explanation - never both, never neither. */
sealed interface NewCodeCoverage {
    data class Metrics(val metricSet: MetricSet) : NewCodeCoverage
    data class Status(val status: String) : NewCodeCoverage
}
