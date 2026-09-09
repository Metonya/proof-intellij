package dev.proofjava.intellij.core.verdict

enum class AnalysisStatus(val wireValue: String) {
    COMPLETE("complete"),
    INCOMPLETE("incomplete"),
    ;

    companion object {
        fun fromWireValue(value: String): AnalysisStatus? = entries.find { it.wireValue == value }
    }
}

data class ToolInfo(val name: String, val version: String)

data class Analysis(
    val status: AnalysisStatus,
    val exitCode: Int,
    /**
     * Real bug fixed 2026-09-07, caught by a live Deep Scan that came
     * back with no per-test evidence and a genuinely useful reason
     * (`PER_TEST_JDK_UNSUPPORTED`) nowhere visible: this was typed
     * `List<String>` and parsed with `it.toString()` (the raw JSON
     * element's own text, e.g. `{"code":"...","message":"..."}` as one
     * ugly string) - the schema (`$defs/reason`, same shape `warnings[]`
     * uses) and `AnalysisReason.java` both confirm these are real
     * `Reason`s, not opaque strings. `proof-vscode` itself never actually
     * reads this field either (`readonly unknown[]` in its own
     * `types.ts`, never surfaced in any UI) - not a regression to match,
     * a real gap worth actually closing here.
     */
    val incompleteReasons: List<Reason>,
)

data class Inputs(val modules: List<ModuleInput>)

data class Coverage(val overall: MetricSet, val newCode: NewCodeCoverage)

/**
 * Kotlin port of the TypeScript shape of `schema/proof-verdict.schema.json`
 * (`proof-vscode/src/verdict/types.ts`). Grown to cover the same fields the
 * TS side reads - no `com.intellij.*` import anywhere in this file or its
 * siblings in this package, so it stays usable outside an IDE process
 * (unit-testable with plain JUnit, no platform test fixture).
 */
data class VerdictDocument(
    val schemaVersion: String,
    val tool: ToolInfo,
    val analysis: Analysis,
    val inputs: Inputs,
    val coverage: Coverage,
    val changedFiles: List<ChangedFile>,
    val findings: List<Finding>,
    val warnings: List<Reason>,
    /** Absent (never null) unless `--file-coverage` was passed. */
    val fileCoverage: FileCoverageBlock? = null,
    /** Absent (never null) unless `--per-test-report` was passed. */
    val perTest: PerTestBlock? = null,
    /** Absent (never null) unless `--mutation-report` was passed. */
    val mutation: MutationBlock? = null,
)
