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

/**
 * The first mode is named after the engine whose headline counter it
 * reproduces exactly: `jacoco-line` from proof-java, `coverage-line` from
 * proof-python (proof-java D-99). The schema requires exactly one of the two,
 * so [engineModeId] carries which one this document actually used and every
 * surface labels it with that rather than assuming an engine.
 */
data class MetricSet(
    val engineModeId: String,
    val engineLine: Metric,
    val strictLine: Metric,
    val sonarCompatible: Metric,
) {
    companion object {
        const val JACOCO_LINE = "jacoco-line"
        const val COVERAGE_LINE = "coverage-line"
        const val STRICT_LINE = "strict-line"
        const val SONAR_COMPATIBLE = "sonar-compatible"
    }
}

/** `coverage.newCode` is either a real `MetricSet` or, when the diff produced nothing measurable, a bare `{status}` explanation - never both, never neither. */
sealed interface NewCodeCoverage {
    data class Metrics(val metricSet: MetricSet) : NewCodeCoverage
    data class Status(val status: String) : NewCodeCoverage
}
