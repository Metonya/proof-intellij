package dev.proofjava.intellij.engine.java.mutation

import dev.proofjava.intellij.core.model.MutationState
import dev.proofjava.intellij.core.verdict.MutatedMethod
import dev.proofjava.intellij.core.verdict.Mutant

/**
 * Port of `proof-vscode/src/ui/treeViews/mutationView.ts`'s node model
 * only (Faz 20/22/24/26/31) - the Swing wiring (`ui.MutationToolWindowPanel`)
 * is a separate, thin file, same split M6's `linetests` package already
 * established.
 *
 * Hierarchy is class -> method -> mutant -> killing test. Score at every
 * level is `killed / (killed + survived)`; indeterminate mutants never
 * enter the denominator and are always counted separately (hard rule 3a,
 * see `MutationModel.kt`). A survived mutant is a real finding (the code
 * broke and nothing noticed), so classes/methods with survivors are not
 * reordered to the top by default - the CLI's own order is kept
 * (deterministic output) - survivor counts show up in every level's own
 * description text instead.
 *
 * Disclosed, not ported: the two right-click cross-view bridge commands
 * ("Show in Test Quality" from a method with a `PSEUDO_TESTED_METHOD`
 * finding, "Show in Line → Tests" from a mutant with per-test evidence).
 * The first has no real destination yet - no Test Quality tree view
 * exists in this plugin at all. The second's destination does exist
 * (M6's Line → Tests tab), but a right-click context menu on this tree is
 * more infrastructure (an `ActionGroup` + `PopupHandler`) than this slice
 * needs yet, unlike every other tree/navigation feature ported so far,
 * which only ever needed double-click.
 */
sealed interface MutationNode {
    data class Empty(val message: String) : MutationNode
    /** Faz 22: "what/when is this result?" - real feedback in the TS source's own history: the panel stayed the same across file switches with nothing indicating which run it showed. */
    data class Header(val text: String) : MutationNode
    data class ClassNode(val className: String, val methods: List<MutatedMethod>) : MutationNode
    data class MethodNode(val className: String, val method: MutatedMethod, val siblings: List<MutatedMethod>) : MutationNode
    data class MutantNode(val className: String, val mutant: Mutant) : MutationNode
    data class KillingTestNode(val rawTestId: String) : MutationNode
}

fun mutationRootChildren(state: MutationState?, survivorsOnly: Boolean, isProductionClass: ((String) -> Boolean)?): List<MutationNode> {
    if (state == null) {
        return listOf(MutationNode.Empty("No mutation test has run yet. Use Mutation Testing (Module), or Mutation Testing (Module, No Diff)."))
    }
    val mutation = state.mutation ?: return listOf(MutationNode.Empty(noMutationEvidenceMessage(state)))

    val header = MutationNode.Header(headerText(state, System.currentTimeMillis()))
    val classes = classesOf(mutation, isProductionClass)
        .map { it.copy(methods = visibleMethods(it.methods, survivorsOnly)) }
        .filter { it.methods.isNotEmpty() }

    if (classes.isEmpty()) {
        val empty = if (survivorsOnly) {
            MutationNode.Empty("No surviving mutants - every generated mutant was caught by at least one test. (Toggle the survivors-only filter to remove this filter.)")
        } else {
            val message = if (hasNoChangedTargetsWarning(state)) {
                noMutationEvidenceMessage(state)
            } else {
                "No mutants were generated for any production method in this run. The targeted classes may not contain mutable code. (Test classes' own mutants are deliberately not shown.)"
            }
            MutationNode.Empty(message)
        }
        return listOf(header, empty)
    }
    return listOf(header) + classes.map { MutationNode.ClassNode(it.className, it.methods) }
}

fun mutationClassChildren(node: MutationNode.ClassNode, survivorsOnly: Boolean): List<MutationNode.MethodNode> =
    visibleMethods(node.methods, survivorsOnly).map { MutationNode.MethodNode(node.className, it, node.methods) }

fun mutationMethodChildren(node: MutationNode.MethodNode): List<MutationNode.MutantNode> =
    node.method.mutants.map { MutationNode.MutantNode(node.className, it) }

fun mutationMutantChildren(node: MutationNode.MutantNode): List<MutationNode.KillingTestNode> =
    node.mutant.killingTests.map { MutationNode.KillingTestNode(it) }

private fun visibleMethods(methods: List<MutatedMethod>, survivorsOnly: Boolean): List<MutatedMethod> =
    if (survivorsOnly) methods.filter { m -> m.mutants.any { bucketOf(it.status) == MutantBucket.SURVIVED } } else methods

/** Faz 31: the shared check both `!state.mutation` and an empty class list consult - see [mutationRootChildren]'s own use of it. */
private fun hasNoChangedTargetsWarning(state: MutationState): Boolean = state.warnings.any { it.code == "MUTATION_NO_CHANGED_TARGETS" }

/** Faz 20: no evidence - but why not? The CLI's own warning codes each name a different reason with a different fix; showing all of them as one "no result" would violate hard rule 3a. */
fun noMutationEvidenceMessage(state: MutationState): String {
    fun warningFor(code: String) = state.warnings.find { it.code == code }
    return when {
        warningFor("MUTATION_BUDGET_EXCEEDED") != null ->
            "The mutation run exceeded its time budget and stopped before producing a result. Raise the mutation timeout, or target a single class."
        warningFor("MUTATION_COLLECTION_FAILED") != null ->
            "The mutation run failed. See the log for the reason."
        warningFor("MUTATION_TARGET_UNRESOLVED") != null ->
            "The targeted class wasn't found under any source root - the class name or source roots may be different from expected."
        warningFor("MUTATION_CLASSPATH_MISSING") != null ->
            "No classpath list is bound for mutation. Re-run the command; the plugin will offer to generate the list."
        warningFor("MUTATION_NO_CHANGED_TARGETS") != null ->
            "No production class changed in this run, so there's no target to mutate. Use Mutation Testing (Module, No Diff) to scan the whole module regardless."
        warningFor("MUTATION_TRUNCATED") != null ->
            "Mutant records hit their upper limit - what's shown is incomplete. Re-run with a narrower target."
        else -> "This run produced no mutation evidence. See the log for the MUTATION_* warnings."
    }
}

/**
 * Faz 22: "Target: Calculator · 5 minute(s) ago", or "saved result - when
 * it ran in this window is unknown" when [MutationState.ranAt] is null (a
 * restored snapshot from before this plugin recorded a timestamp, or a
 * genuinely unknown case).
 */
fun headerText(state: MutationState, nowMs: Long): String {
    val target = targetSummary(state.targets)
    val whenText = state.ranAt?.let { formatRelativeTime(it, nowMs) } ?: "saved result - when it ran in this window is unknown"
    return "Target: $target · $whenText"
}

/**
 * The score is never shown as a bare percentage: without the
 * indeterminate count visible, "100%" would be misleading (hard rule
 * 3a). Faz 24 (§7.6 madde 7): when [allNoCoverage] is true (every
 * generated mutant for this method is `NO_COVERAGE`), the reason is
 * stated outright rather than hidden behind a generic "N inconclusive".
 */
fun scoreText(score: MutationScore, allNoCoverage: Boolean = false): String {
    if (score.percent == null && allNoCoverage) {
        return "no score - no test reaches this method"
    }
    val base = if (score.percent == null) {
        "no score"
    } else {
        "${Math.round(score.percent)}% · ${score.killed}/${score.killed + score.survived} killed"
    }
    return if (score.indeterminate > 0) "$base · ${score.indeterminate} inconclusive" else base
}

fun scoreTooltip(score: MutationScore, allNoCoverage: Boolean = false): String {
    val lines = mutableListOf(
        "Killed: ${score.killed}",
        "Survived: ${score.survived}",
        "Inconclusive: ${score.indeterminate}",
    )
    lines += if (score.percent == null && allNoCoverage) {
        "No score can be computed: every generated mutant is NO_COVERAGE - no test ever reaches this method, so the mutation engine can't even observe its behavior."
    } else if (score.percent == null) {
        "No score can be computed: no mutant has been decided (killed or survived)."
    } else {
        "Score = killed / (killed + survived) = ${"%.1f".format(score.percent)}%. Inconclusive mutants don't count toward the denominator."
    }
    return lines.joinToString("\n\n")
}

fun bucketText(bucket: MutantBucket, status: String): String = when (bucket) {
    MutantBucket.KILLED -> if (status == "TIMED_OUT") "killed (timed out)" else "killed"
    MutantBucket.SURVIVED -> "SURVIVED"
    MutantBucket.INDETERMINATE -> "inconclusive ($status)"
}

fun mutantTooltip(bucket: MutantBucket, mutant: Mutant): String {
    val head = "${mutatorLabel(mutant.mutator)} · line ${mutant.line} · ${mutant.status}\n\n${mutant.mutator}\n\n"
    return head + when (bucket) {
        MutantBucket.KILLED -> if (mutant.status == "TIMED_OUT") {
            "The mutation sent the code into an infinite loop and the run timed out. PIT counts this as killed: the behavior change was noticed."
        } else {
            "The code was broken and ${mutant.killingTests.size} test(s) caught it. This is the desired outcome."
        }
        MutantBucket.SURVIVED -> "We broke the code, and no test noticed. An assertion that actually verifies this line's behavior is missing."
        MutantBucket.INDETERMINATE -> "Nothing can be said about this mutant - it counts as neither killed nor survived, and doesn't factor into the score. " +
            "Common causes: NO_COVERAGE (no test reaches this line), NON_VIABLE (the JVM rejected the mutant), " +
            "RUN_ERROR/MEMORY_ERROR (the run crashed), NOT_STARTED/STARTED (never queued or left unfinished)."
    }
}
