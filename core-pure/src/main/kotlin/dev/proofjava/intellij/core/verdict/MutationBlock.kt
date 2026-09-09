package dev.proofjava.intellij.core.verdict

/**
 * `status` is deliberately kept as a free string, not an enum of PIT's own
 * `DetectionStatus` values - port of the same choice in `verdict/types.ts`
 * (`Mutant.status`): an unrecognized status must not reject the whole
 * document, `model` layer treats it as "unclassified" (hard rule 3a).
 */
data class Mutant(
    val mutator: String,
    val line: Int,
    val status: String,
    /** `fullMutationMatrix` is on, so **every** test that killed this mutant is here, not just the first. */
    val killingTests: List<String>,
)

data class MutatedMethod(
    val className: String,
    val methodName: String,
    /** JVM descriptor, e.g. `(II)I` - the only way to disambiguate same-named overloads. */
    val methodDescription: String,
    val firstLine: Int,
    val lastLine: Int,
    val mutants: List<Mutant>,
)

data class MutationModuleEvidence(
    val id: String,
    val methods: List<MutatedMethod>,
)

/** Present only when `--mutation-report` was passed. Wire format may intern test ids the same way [dev.proofjava.intellij.core.verdict.PerTestBlock] does (D-86) - already resolved to plain strings by [VerdictParser] before this type is constructed. */
data class MutationBlock(
    val engine: String,
    val engineVersion: String,
    val modules: List<MutationModuleEvidence>,
)
