package dev.proofjava.intellij.core.model

import dev.proofjava.intellij.core.verdict.MutationBlock
import dev.proofjava.intellij.core.verdict.Reason

/**
 * Port of `proof-vscode/src/model/store.ts`'s `MutationState` - the last
 * mutation-testing run's L3 evidence, kept independently of
 * [CoverageState]/[PerTestState] (each has its own lifecycle - a fresh
 * Quick Scan or Deep Scan must not silently invalidate a mutation result).
 */
data class MutationState(
    val mutation: MutationBlock?,
    val warnings: List<Reason>,
    /** What was asked for, not derived from the result - empty means diff-derived (the CLI picked the targets). */
    val targets: List<String>,
    /** This client's own timestamp, set at write time - the CLI's own output carries none. `null` after a restore with no recorded time. */
    val ranAt: Long?,
)
