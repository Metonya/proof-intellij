package dev.proofjava.intellij.core.model

import dev.proofjava.intellij.core.verdict.PerTestBlock
import dev.proofjava.intellij.core.verdict.Reason

/**
 * Port of `proof-vscode/src/model/store.ts`'s `PerTestState` - the last
 * Deep Scan's L2 evidence, kept independently of [CoverageState] (Quick
 * Scan and Deep Scan have separate lifecycles; a fresh Quick Scan must not
 * silently invalidate Deep Scan's own result).
 */
data class PerTestState(
    val perTest: PerTestBlock?,
    val warnings: List<Reason>,
    /** What was asked for, not derived from the result - empty means diff-derived (the CLI picked the targets). */
    val targets: List<String>,
    /** This client's own timestamp, set at write time - the CLI's own output carries none. `null` after a restore with no recorded time. */
    val ranAt: Long?,
)
