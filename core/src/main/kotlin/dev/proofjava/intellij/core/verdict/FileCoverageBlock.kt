package dev.proofjava.intellij.core.verdict

/** Port of `verdict/types.ts`'s `LineTuple` - `[line, missedInstructions, coveredInstructions, missedBranches, coveredBranches]`, same order as proof-java's own `LineCoverage`. */
data class LineTuple(
    val line: Int,
    val missedInstructions: Int,
    val coveredInstructions: Int,
    val missedBranches: Int,
    val coveredBranches: Int,
)

data class FileCoverageEntry(
    val module: String,
    val path: String,
    val metrics: MetricSet,
    val lines: List<LineTuple>,
)

/** Present only when `--file-coverage` was passed (opt-in contract) - absent (never an empty block) otherwise. */
data class FileCoverageBlock(
    val files: List<FileCoverageEntry>,
    val excluded: List<String>,
)
