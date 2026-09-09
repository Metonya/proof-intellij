package dev.proofjava.intellij.engine.java.mutation

import dev.proofjava.intellij.core.verdict.MutatedMethod
import dev.proofjava.intellij.core.verdict.MutationBlock
import dev.proofjava.intellij.core.verdict.MutationModuleEvidence
import dev.proofjava.intellij.core.verdict.Mutant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Kotlin port of `proof-vscode/src/test/unit/model/mutationModel.test.ts`, test-by-test, same real playground fixture data (2026-08-28 runs). */
class MutationModelTest {

    private fun mutant(status: String, line: Int = 10, killingTests: List<String> = emptyList()) =
        Mutant("org.pitest.mutationtest.engine.gregor.mutators.returns.PrimitiveReturnsMutator", line, status, killingTests)

    @Test
    fun `PIT's nine statuses map to three`() {
        assertEquals(MutantBucket.KILLED, bucketOf("KILLED"))
        assertEquals(MutantBucket.KILLED, bucketOf("TIMED_OUT"), "PIT's own convention: an infinite-loop mutant was detected")
        assertEquals(MutantBucket.SURVIVED, bucketOf("SURVIVED"))
        for (status in listOf("NON_VIABLE", "MEMORY_ERROR", "NOT_STARTED", "STARTED", "RUN_ERROR", "NO_COVERAGE")) {
            assertEquals(MutantBucket.INDETERMINATE, bucketOf(status), "$status is neither killed nor survived")
        }
    }

    @Test
    fun `an unrecognized status is indeterminate, never folded into killed or survived`() {
        assertEquals(MutantBucket.INDETERMINATE, bucketOf("SOMETHING_NEW_IN_PIT_2"))
        assertEquals(MutantBucket.INDETERMINATE, bucketOf(""))
    }

    @Test
    fun `score - indeterminates are excluded from the denominator but still counted`() {
        val score = scoreOf(listOf(mutant("KILLED"), mutant("SURVIVED"), mutant("NO_COVERAGE"), mutant("RUN_ERROR")))
        assertEquals(1, score.killed)
        assertEquals(1, score.survived)
        assertEquals(2, score.indeterminate)
        assertEquals(50.0, score.percent, "denominator is 2 (decided), not 4")
    }

    @Test
    fun `no decided mutant means percent is null, not zero`() {
        val score = scoreOf(listOf(mutant("NO_COVERAGE"), mutant("NON_VIABLE")))
        assertNull(score.percent)
        assertEquals(2, score.indeterminate)
    }

    @Test
    fun `no mutants at all also means percent is null`() {
        assertNull(scoreOf(emptyList()).percent)
    }

    private fun method(className: String, methodName: String, firstLine: Int, mutants: List<Mutant>, methodDescription: String = "(I)I") =
        MutatedMethod(className, methodName, methodDescription, firstLine, firstLine, mutants)

    /**
     * Real trap from a live run (2026-08-28): PIT mutates test classes too
     * - a single `--mutation-target root=...Calculator` run produced 16
     * methods, 8 of them from test classes.
     */
    private val block = MutationBlock(
        engine = "pitest",
        engineVersion = "1.15.8",
        modules = listOf(
            MutationModuleEvidence(
                id = "root",
                methods = listOf(
                    method("dev.proofjava.playground.Calculator", "square", 37, listOf(mutant("SURVIVED", 37))),
                    method("dev.proofjava.playground.Calculator", "divide", 22, listOf(mutant("KILLED", 22, listOf("CalcTest#divideNarrow()"))), "(II)I"),
                    method("dev.proofjava.playground.CalculatorSubsumedTest", "divideNarrow", 19, listOf(mutant("SURVIVED", 19))),
                ),
            ),
        ),
    )

    @Test
    fun `classesOf - a production filter excludes test classes`() {
        val classes = classesOf(block) { it == "dev.proofjava.playground.Calculator" }
        assertEquals(listOf("dev.proofjava.playground.Calculator"), classes.map { it.className })
        assertEquals(2, classes[0].methods.size)
    }

    @Test
    fun `classesOf - no filter means nothing is excluded, incomplete information must not destroy evidence`() {
        assertEquals(2, classesOf(block).size)
    }

    @Test
    fun `classesOf - methods sort by line, classes sort by name`() {
        val methods = classesOf(block) { true }.find { it.className.endsWith("Calculator") }!!.methods
        assertEquals(listOf(22, 37), methods.map { it.firstLine })
    }

    @Test
    fun `classesOf - multiple modules' methods merge`() {
        val twoModules = MutationBlock(
            engine = "pitest",
            engineVersion = "1.15.8",
            modules = listOf(
                block.modules[0],
                MutationModuleEvidence(id = "gson", methods = listOf(method("com.example.Other", "run", 5, listOf(mutant("KILLED", 5))))),
            ),
        )
        val classes = classesOf(twoModules)
        assertEquals(
            listOf("com.example.Other", "dev.proofjava.playground.Calculator", "dev.proofjava.playground.CalculatorSubsumedTest"),
            classes.map { it.className }.sorted(),
        )
    }

    @Test
    fun `mutatorLabel shortens a real PIT class name`() {
        assertEquals("PrimitiveReturns", mutatorLabel("org.pitest.mutationtest.engine.gregor.mutators.returns.PrimitiveReturnsMutator"))
        assertEquals("VoidMethodCall", mutatorLabel("org.pitest.mutationtest.engine.gregor.mutators.VoidMethodCallMutator"))
    }

    @Test
    fun `mutatorLabel passes a short or unexpected name through unchanged`() {
        assertEquals("TRUE_RETURNS", mutatorLabel("TRUE_RETURNS"))
        assertEquals("Mutator", mutatorLabel("Mutator"), "the raw name is kept when stripping the suffix would leave nothing")
    }

    @Test
    fun `methodLabel only shows a descriptor for a genuinely overloaded method`() {
        val single = method("C", "square", 1, emptyList())
        assertEquals("square()", methodLabel(single, listOf(single)))

        val a = method("C", "add", 1, emptyList(), "(II)I")
        val b = method("C", "add", 5, emptyList(), "(DD)D")
        assertEquals("add(II)I", methodLabel(a, listOf(a, b)))
    }

    @Test
    fun `targetSummary - a single target shows its short class name`() {
        assertEquals("Calculator", targetSummary(listOf("dev.proofjava.playground.Calculator")))
    }

    @Test
    fun `targetSummary - multiple targets show a count, not a list`() {
        assertEquals("3 class(es)", targetSummary(listOf("a.B", "a.C", "a.D")))
    }

    @Test
    fun `targetSummary - empty targets (whole module, diff-derived) says so`() {
        assertEquals("changed classes in the diff", targetSummary(emptyList()))
    }

    @Test
    fun `formatRelativeTime - under a minute is just now`() {
        val now = 1788000000000L
        assertEquals("just now", formatRelativeTime(now - 30_000, now))
    }

    @Test
    fun `formatRelativeTime - minute, hour, day thresholds`() {
        val now = 1788000000000L
        assertEquals("5 minute(s) ago", formatRelativeTime(now - 5 * 60_000, now))
        assertEquals("2 hour(s) ago", formatRelativeTime(now - 90 * 60_000, now), "90 minutes rounds to the nearest hour")
        assertEquals("2 day(s) ago", formatRelativeTime(now - 50 * 3600_000, now))
    }

    @Test
    fun `formatRelativeTime - a future time (clock skew) does not go negative`() {
        val now = 1788000000000L
        assertEquals("just now", formatRelativeTime(now + 10_000, now))
    }

    @Test
    fun `parseProductionMethod parses the real shape (FQCN#method(desc)returnType)`() {
        assertEquals(
            ProductionMethodIdentity("dev.proofjava.playground.Calculator", "square", "(I)I"),
            parseProductionMethod("dev.proofjava.playground.Calculator#square(I)I"),
        )
    }

    @Test
    fun `parseProductionMethod splits a no-arg void-ish method's descriptor correctly too`() {
        assertEquals(
            ProductionMethodIdentity("dev.proofjava.playground.Calculator", "describe", "()Ljava/lang/String;"),
            parseProductionMethod("dev.proofjava.playground.Calculator#describe()Ljava/lang/String;"),
        )
    }

    @Test
    fun `parseProductionMethod returns null, never a guess, when there's no hash or paren`() {
        assertNull(parseProductionMethod("no hash here"))
        assertNull(parseProductionMethod("dev.proofjava.playground.Calculator#square"))
    }

    @Test
    fun `productionMethodKey is the exact reverse of parseProductionMethod, matching finding_productionMethod`() {
        assertEquals("dev.proofjava.playground.Calculator#square(I)I", productionMethodKey("dev.proofjava.playground.Calculator", "square", "(I)I"))
    }

    @Test
    fun `findMutatedMethod - found when method name and description both match, real playground shape`() {
        val classes = classesOf(
            MutationBlock(
                engine = "pitest",
                engineVersion = "1.15.8",
                modules = listOf(
                    MutationModuleEvidence(
                        id = "root",
                        methods = listOf(
                            MutatedMethod("dev.proofjava.playground.Calculator", "square", "(I)I", 37, 37, emptyList()),
                            MutatedMethod("dev.proofjava.playground.Calculator", "add", "(II)I", 6, 8, emptyList()),
                        ),
                    ),
                ),
            ),
        )
        val found = findMutatedMethod(classes, "dev.proofjava.playground.Calculator", "square", "(I)I")
        assertTrue(found != null)
        assertEquals("square", found!!.method.methodName)
        assertEquals("dev.proofjava.playground.Calculator", found.cls.className)
    }

    @Test
    fun `findMutatedMethod - no match (a stale mutation result) returns null, not a guess`() {
        val classes = classesOf(MutationBlock("pitest", "1.15.8", listOf(MutationModuleEvidence("root", emptyList()))))
        assertNull(findMutatedMethod(classes, "dev.proofjava.playground.Calculator", "square", "(I)I"))
    }

    /**
     * Real playground contradiction (2026-08-28, `--mutation-report
     * root=...Calculator`) - `CalculatorUnresolvedOracleTest#addCheckedViaLocalSoftAssertions()`
     * was L0 `NO_RECOGNIZED_ORACLE` (INCONCLUSIVE) but genuinely appears
     * in `add()`'s one mutant's `killingTests`, in raw JUnit5 UniqueId form.
     */
    private val addMutationWithContradiction = MutationBlock(
        engine = "pitest",
        engineVersion = "1.15.8",
        modules = listOf(
            MutationModuleEvidence(
                id = "root",
                methods = listOf(
                    MutatedMethod(
                        className = "dev.proofjava.playground.Calculator",
                        methodName = "add",
                        methodDescription = "(II)I",
                        firstLine = 6,
                        lastLine = 8,
                        mutants = listOf(
                            Mutant(
                                mutator = "org.pitest.mutationtest.engine.gregor.mutators.returns.PrimitiveReturnsMutator",
                                line = 7,
                                status = "KILLED",
                                killingTests = listOf(
                                    "dev.proofjava.playground.CalculatorGoodTest.[engine:junit-jupiter]/[class:dev.proofjava.playground.CalculatorGoodTest]/[method:addWorksCorrectly()]",
                                    "dev.proofjava.playground.CalculatorUnresolvedOracleTest.[engine:junit-jupiter]/[class:dev.proofjava.playground.CalculatorUnresolvedOracleTest]/[method:addCheckedViaLocalSoftAssertions()]",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun `findKillContribution - a test statically INCONCLUSIVE is found in a real mutant's killingTests - the contradiction is real`() {
        val found = findKillContribution(addMutationWithContradiction, "dev.proofjava.playground.CalculatorUnresolvedOracleTest", "addCheckedViaLocalSoftAssertions")
        assertEquals(KillContribution("dev.proofjava.playground.Calculator", "add", "(II)I", 7), found)
    }

    @Test
    fun `findKillContribution - a test that never killed anything means null, no contradiction to report`() {
        assertNull(findKillContribution(addMutationWithContradiction, "dev.proofjava.playground.CalculatorNoOracleTest", "subtractHasNoAssertion"))
    }

    @Test
    fun `findKillContribution - an empty modules list means null, not an error`() {
        val empty = MutationBlock("pitest", "1.15.8", emptyList())
        assertNull(findKillContribution(empty, "dev.proofjava.playground.CalculatorUnresolvedOracleTest", "addCheckedViaLocalSoftAssertions"))
    }

    @Test
    fun `allMutantsNoCoverage - real negate() shape - one NO_COVERAGE mutant - true`() {
        assertTrue(allMutantsNoCoverage(listOf(mutant("NO_COVERAGE"))))
    }

    @Test
    fun `allMutantsNoCoverage - real describe() shape - mixed NO_COVERAGE and SURVIVED - false`() {
        assertFalse(allMutantsNoCoverage(listOf(mutant("NO_COVERAGE"), mutant("SURVIVED"))), "a mixed case must not claim nothing reaches this")
    }

    @Test
    fun `allMutantsNoCoverage - no mutants at all is false - not the same claim as all NO_COVERAGE`() {
        assertFalse(allMutantsNoCoverage(emptyList()))
    }
}
