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
    /** Kept as raw JSON text per element (hard rule 3a: shape not yet needed by any consumer here, never guessed). */
    val incompleteReasons: List<String>,
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
