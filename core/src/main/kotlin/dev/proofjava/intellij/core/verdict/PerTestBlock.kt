package dev.proofjava.intellij.core.verdict

data class PerTestLine(
    val line: Int,
    val tests: List<String>,
)

data class PerTestEntry(
    val className: String,
    val methodName: String,
    val lines: List<PerTestLine>,
)

/** D-50: static-initializer coverage (`<clinit>`), never test-attributable, kept separate from [entries]. */
data class PerTestModuleEvidence(
    val id: String,
    val entries: List<PerTestEntry>,
    val ambient: List<PerTestEntry>,
)

/** Present only when `--per-test-report` was passed (D-46/D-55's opt-in contract). Wire format may intern test ids into a module-level `testIds` array with numeric indexes in [PerTestLine.tests] (D-86) - [VerdictParser] resolves those back to plain strings before this type is ever constructed, so every consumer here always sees plain strings. */
data class PerTestBlock(
    val engine: String,
    val engineVersion: String,
    val modules: List<PerTestModuleEvidence>,
)
