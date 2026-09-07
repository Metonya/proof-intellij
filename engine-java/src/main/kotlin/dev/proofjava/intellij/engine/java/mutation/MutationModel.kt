package dev.proofjava.intellij.engine.java.mutation

import dev.proofjava.intellij.core.verdict.MutatedMethod
import dev.proofjava.intellij.core.verdict.MutationBlock
import dev.proofjava.intellij.core.verdict.Mutant
import dev.proofjava.intellij.engine.java.verdict.parseTestIdentity

/**
 * Port of `proof-vscode/src/model/mutationModel.ts` (Faz 20/22/24/30) -
 * lives in `engine-java`, not `core.model`, because [findKillContribution]
 * needs [parseTestIdentity] (JUnit5 `UniqueId`/`Class#method()` parsing,
 * a JVM-specific test-id convention), the exact same reason
 * `linetests.LineIndex`/`linetests.TestQuality` live here rather than in
 * `core` (M6). No IntelliJ Platform SDK import anywhere in this file -
 * unit-testable with plain JUnit, same as the rest of this package.
 *
 * Bucket decision (plan §8.3, user-approved in the TS source's own
 * history): PIT's nine `DetectionStatus` values collapse to three -
 * `KILLED`/`TIMED_OUT` -> killed (PIT's own convention: a mutant that
 * sent the code into an infinite loop **was** detected), `SURVIVED` ->
 * survived, everything else (including any status this port doesn't
 * recognize - hard rule 3a) -> indeterminate. Indeterminate never folds
 * into killed or survived and is never hidden.
 */
enum class MutantBucket { KILLED, SURVIVED, INDETERMINATE }

private val KILLED_STATUSES = setOf("KILLED", "TIMED_OUT")
private val SURVIVED_STATUSES = setOf("SURVIVED")

fun bucketOf(status: String): MutantBucket = when {
    KILLED_STATUSES.contains(status) -> MutantBucket.KILLED
    SURVIVED_STATUSES.contains(status) -> MutantBucket.SURVIVED
    else -> MutantBucket.INDETERMINATE
}

/**
 * @param percent `killed / (killed + survived)`, 0-100, not rounded to an
 * integer. Indeterminate mutants never enter the denominator - nothing is
 * known about them either way. `null` (not zero) when the denominator is
 * zero: no score, not a zero score.
 */
data class MutationScore(val killed: Int, val survived: Int, val indeterminate: Int, val percent: Double?)

fun scoreOf(mutants: List<Mutant>): MutationScore {
    var killed = 0
    var survived = 0
    var indeterminate = 0
    for (mutant in mutants) {
        when (bucketOf(mutant.status)) {
            MutantBucket.KILLED -> killed++
            MutantBucket.SURVIVED -> survived++
            MutantBucket.INDETERMINATE -> indeterminate++
        }
    }
    val decided = killed + survived
    val percent = if (decided == 0) null else (killed.toDouble() / decided) * 100
    return MutationScore(killed, survived, indeterminate, percent)
}

fun scoreOfMethods(methods: List<MutatedMethod>): MutationScore = scoreOf(methods.flatMap { it.mutants })

/**
 * Faz 24 (§7.6 madde 7): "no score" alone doesn't say *why* - `NO_COVERAGE`,
 * `RUN_ERROR`, `NON_VIABLE` all fell into the same bucket. When every
 * generated mutant is `NO_COVERAGE`, the reason is unambiguous and derived
 * from the data (real playground data: `negate()`'s one mutant was
 * `NO_COVERAGE`, `describe()`'s was mixed `NO_COVERAGE`+`SURVIVED` - hence
 * `all`, not `any`; in the mixed case, "nothing reaches this" would be
 * false).
 */
fun allMutantsNoCoverage(mutants: List<Mutant>): Boolean = mutants.isNotEmpty() && mutants.all { it.status == "NO_COVERAGE" }

/** A class's methods, grouped from `mutation.modules[].methods[]`. Classes and methods sorted - the CLI already emits them sorted, but this does not rely on that. */
data class MutatedClass(val className: String, val methods: List<MutatedMethod>)

/** Faz 30: merges every bound module's methods - `mutation.modules` may hold several in a multi-module run. */
fun classesOf(mutation: MutationBlock, isProductionClass: ((String) -> Boolean)? = null): List<MutatedClass> {
    val byClass = linkedMapOf<String, MutableList<MutatedMethod>>()
    for (method in mutation.modules.flatMap { it.methods }) {
        // Faz 20: PIT mutates test classes too - a real playground run had
        // 8 of 16 methods from test classes (CalculatorSubsumedTest,
        // CalculatorGoodTest, ...). A test's own mutant score tells the
        // user nothing and pollutes the production score - the mutation
        // side of the same trap `linetests.LineIndex` filters on the
        // `perTest` side. No filter given means nothing is excluded -
        // filtering with incomplete information destroys evidence.
        if (isProductionClass != null && !isProductionClass(method.className)) continue
        byClass.getOrPut(method.className) { mutableListOf() }.add(method)
    }
    return byClass.entries
        .map { (className, methods) -> MutatedClass(className, methods.sortedWith(compareBy({ it.firstLine }, { it.methodName }))) }
        .sortedBy { it.className }
}

/**
 * Faz 22: the mutation panel's "what/when is this result?" header. Real
 * user feedback (TS source history): the panel stayed the same across
 * file switches, with no indication of which class's result it showed.
 */
fun targetSummary(targets: List<String>): String = when {
    targets.isEmpty() -> "changed classes in the diff"
    targets.size == 1 -> shortClassName(targets[0])
    else -> "${targets.size} class(es)"
}

private fun shortClassName(fqcn: String): String {
    val dot = fqcn.lastIndexOf('.')
    return if (dot < 0) fqcn else fqcn.substring(dot + 1)
}

/**
 * When [fromMs] is unknown (a result restored from disk - the CLI's own
 * output carries no timestamp, D-xx; "when did this run" only lives in
 * the plugin's own in-session memory), the caller must not call this at
 * all - an estimated duration is more misleading than a real one (hard
 * rule 3a).
 */
fun formatRelativeTime(fromMs: Long, nowMs: Long): String {
    val diffSeconds = maxOf(0L, Math.round((nowMs - fromMs) / 1000.0))
    if (diffSeconds < 60) return "just now"
    val minutes = Math.round(diffSeconds / 60.0)
    if (minutes < 60) return "$minutes minute(s) ago"
    val hours = Math.round(minutes / 60.0)
    if (hours < 24) return "$hours hour(s) ago"
    val days = Math.round(hours / 24.0)
    return "$days day(s) ago"
}

fun methodLabel(method: MutatedMethod, siblings: List<MutatedMethod>): String {
    val overloaded = siblings.count { it.methodName == method.methodName } > 1
    return if (overloaded) "${method.methodName}${method.methodDescription}" else "${method.methodName}()"
}

/**
 * PIT emits `mutator` as a fully qualified class name - real data showed
 * `org.pitest.mutationtest.engine.gregor.mutators.returns.PrimitiveReturnsMutator`
 * (not the schema golden example's short `TRUE_RETURNS` form, verified
 * 2026-08-28). Unreadable in full in a tree label - last segment kept,
 * `Mutator` suffix dropped. The full name stays available in a tooltip
 * elsewhere - this is a display choice, not data loss.
 */
fun mutatorLabel(mutator: String): String {
    val last = mutator.substring(mutator.lastIndexOf('.') + 1)
    val trimmed = if (last.endsWith("Mutator")) last.substring(0, last.length - "Mutator".length) else last
    return trimmed.ifEmpty { mutator }
}

data class ProductionMethodIdentity(val className: String, val methodName: String, val methodDescription: String)

/**
 * Faz 24 (§7.6 madde 5): [dev.proofjava.intellij.core.verdict.Finding.productionMethod]'s
 * real shape is `"FQCN#methodName(descriptor)returnType"` (verified against
 * a live CLI run, 2026-08-28: `"dev.proofjava.playground.Calculator#square(I)I"`).
 * The `(` is always where a JVM descriptor starts, not a guess - it's the
 * shape itself. `null` (never guessed) when unparseable.
 */
fun parseProductionMethod(productionMethod: String): ProductionMethodIdentity? {
    val hashIndex = productionMethod.indexOf('#')
    val parenIndex = productionMethod.indexOf('(', hashIndex)
    if (hashIndex < 0 || parenIndex < 0) return null
    return ProductionMethodIdentity(
        className = productionMethod.substring(0, hashIndex),
        methodName = productionMethod.substring(hashIndex + 1, parenIndex),
        methodDescription = productionMethod.substring(parenIndex),
    )
}

/** The reverse of [parseProductionMethod] - a [MutatedMethod]'s own key, directly comparable to `Finding.productionMethod`. */
fun productionMethodKey(className: String, methodName: String, methodDescription: String): String = "$className#$methodName$methodDescription"

data class FoundMutatedMethod(val cls: MutatedClass, val method: MutatedMethod)

/** Finds the method under a class matching [parseProductionMethod]'s own identity exactly - the Test Quality -> Mutation view bridge. */
fun findMutatedMethod(classes: List<MutatedClass>, className: String, methodName: String, methodDescription: String): FoundMutatedMethod? {
    val cls = classes.find { it.className == className } ?: return null
    val method = cls.methods.find { it.methodName == methodName && it.methodDescription == methodDescription } ?: return null
    return FoundMutatedMethod(cls, method)
}

data class KillContribution(val className: String, val methodName: String, val methodDescription: String, val mutantLine: Int)

/**
 * Faz 24 (§7.6 madde 6): when L0's static oracle scan couldn't resolve a
 * test and called it `INCONCLUSIVE`, but L3 mutation evidence shows that
 * exact test killed a mutant, that's a real refutation - the test really
 * does observe behavior. Real playground data, 2026-08-28:
 * `CalculatorUnresolvedOracleTest#addCheckedViaLocalSoftAssertions()`
 * (an AssertJ soft assertion the static scanner couldn't resolve) is
 * among the tests that killed `add()`'s one mutant.
 *
 * `killingTests` carries raw JUnit5 `UniqueId`s - reduced to the same
 * `Class#method()` identity via [parseTestIdentity] (the same match
 * `linetests.TestQuality` uses) before comparing. No match -> `null`,
 * never guessed.
 */
fun findKillContribution(mutation: MutationBlock, testClassName: String, testMethodName: String): KillContribution? {
    for (method in mutation.modules.flatMap { it.methods }) {
        for (mutant in method.mutants) {
            for (rawTestId in mutant.killingTests) {
                val identity = parseTestIdentity(rawTestId)
                if (identity.className == testClassName && identity.methodName == testMethodName) {
                    return KillContribution(method.className, method.methodName, method.methodDescription, mutant.line)
                }
            }
        }
    }
    return null
}
