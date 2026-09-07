package dev.proofjava.intellij.core.model

import dev.proofjava.intellij.core.verdict.LineTuple

/**
 * Port of `proof-vscode/src/verdict/coverageMapping.ts`. Turns proof-java's
 * `[line, mi, ci, mb, cb]` tuples into the classification the gutter paints
 * and the Coverage tree rolls up percentages from - one implementation, so
 * there is no second classifier that could quietly drift from it.
 *
 * A line is [LineState.PARTIAL] when it executed but not every instruction
 * or branch on it did (`missedInstructions>0 || missedBranches>0`) - the
 * same real JaCoCo data the CLI's own `sonar-compatible` metric counts
 * (D-04), never a synthesized approximation.
 */

data class MappedLine(val line: Int, val executed: Boolean, val partial: Boolean)

enum class LineState { COVERED, PARTIAL, UNCOVERED }

fun mapLines(lines: List<LineTuple>): List<MappedLine> = lines.map(::mapLine)

fun classifyLine(mapped: MappedLine): LineState = when {
    !mapped.executed -> LineState.UNCOVERED
    mapped.partial -> LineState.PARTIAL
    else -> LineState.COVERED
}

private fun mapLine(tuple: LineTuple): MappedLine {
    val executed = tuple.coveredInstructions > 0
    val partial = executed && (tuple.missedInstructions > 0 || tuple.missedBranches > 0)
    return MappedLine(tuple.line, executed, partial)
}
