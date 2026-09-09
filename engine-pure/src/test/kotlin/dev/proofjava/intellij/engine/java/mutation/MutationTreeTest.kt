package dev.proofjava.intellij.engine.java.mutation

import dev.proofjava.intellij.core.model.MutationState
import dev.proofjava.intellij.core.verdict.MutatedMethod
import dev.proofjava.intellij.core.verdict.MutationBlock
import dev.proofjava.intellij.core.verdict.MutationModuleEvidence
import dev.proofjava.intellij.core.verdict.Mutant
import dev.proofjava.intellij.core.verdict.Reason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MutationTreeTest {

    @Test
    fun `no mutation state at all yields a single empty node`() {
        val children = mutationRootChildren(null, survivorsOnly = false, isProductionClass = null)
        assertEquals(1, children.size)
        assertTrue(children[0] is MutationNode.Empty)
    }

    @Test
    fun `a state with no mutation block yields an empty node explaining why, via warnings`() {
        val state = MutationState(mutation = null, warnings = listOf(Reason("MUTATION_NO_CHANGED_TARGETS", "nothing changed")), targets = emptyList(), ranAt = null)
        val children = mutationRootChildren(state, survivorsOnly = false, isProductionClass = null)
        assertEquals(1, children.size)
        assertTrue((children[0] as MutationNode.Empty).message.contains("No production class changed"))
    }

    private fun mutant(status: String, line: Int = 10, killingTests: List<String> = emptyList()) =
        Mutant("org.pitest.mutationtest.engine.gregor.mutators.returns.PrimitiveReturnsMutator", line, status, killingTests)

    private fun method(className: String, methodName: String, firstLine: Int, mutants: List<Mutant>) =
        MutatedMethod(className, methodName, "(I)I", firstLine, firstLine, mutants)

    private val block = MutationBlock(
        "pitest", "1.15.8",
        listOf(
            MutationModuleEvidence(
                "root",
                listOf(
                    method("dev.example.Calculator", "square", 37, listOf(mutant("SURVIVED", 37))),
                    method("dev.example.Calculator", "add", 7, listOf(mutant("KILLED", 7, listOf("CalcTest#addsTwoNumbers()")))),
                ),
            ),
        ),
    )

    private fun state(warnings: List<Reason> = emptyList(), targets: List<String> = emptyList(), ranAt: Long? = 1_757_000_000_000L) =
        MutationState(mutation = block, warnings = warnings, targets = targets, ranAt = ranAt)

    @Test
    fun `a real block yields a header plus one class node with all its methods`() {
        val children = mutationRootChildren(state(), survivorsOnly = false, isProductionClass = null)
        assertEquals(2, children.size)
        assertTrue(children[0] is MutationNode.Header)
        val classNode = children[1] as MutationNode.ClassNode
        assertEquals("dev.example.Calculator", classNode.className)
        assertEquals(2, classNode.methods.size)
    }

    @Test
    fun `survivorsOnly filters out methods with no surviving mutant, at every level`() {
        val children = mutationRootChildren(state(), survivorsOnly = true, isProductionClass = null)
        val classNode = children[1] as MutationNode.ClassNode
        assertEquals(1, classNode.methods.size, "only square() has a SURVIVED mutant")
        assertEquals("square", classNode.methods[0].methodName)

        val methodChildren = mutationClassChildren(classNode, survivorsOnly = true)
        assertEquals(1, methodChildren.size)
    }

    @Test
    fun `survivorsOnly with nothing surviving yields a distinct empty message`() {
        val allKilled = MutationState(
            mutation = MutationBlock("pitest", "1.15.8", listOf(MutationModuleEvidence("root", listOf(method("dev.example.Calculator", "add", 7, listOf(mutant("KILLED"))))))),
            warnings = emptyList(), targets = emptyList(), ranAt = null,
        )
        val children = mutationRootChildren(allKilled, survivorsOnly = true, isProductionClass = null)
        assertEquals(2, children.size)
        assertTrue((children[1] as MutationNode.Empty).message.contains("No surviving mutants"))
    }

    @Test
    fun `a production-class filter that excludes everything falls back to the no-changed-targets message when warned`() {
        val warned = state(warnings = listOf(Reason("MUTATION_NO_CHANGED_TARGETS", "nothing changed")))
        val children = mutationRootChildren(warned, survivorsOnly = false) { false }
        assertEquals(2, children.size)
        assertTrue((children[1] as MutationNode.Empty).message.contains("No production class changed"))
    }

    @Test
    fun `class children are method nodes, method children are mutant nodes, mutant children are killing tests`() {
        val classNode = (mutationRootChildren(state(), survivorsOnly = false, isProductionClass = null)[1]) as MutationNode.ClassNode
        val methodNode = mutationClassChildren(classNode, survivorsOnly = false).find { it.method.methodName == "add" }!!
        val mutantNode = mutationMethodChildren(methodNode)[0]
        assertEquals("KILLED", mutantNode.mutant.status)
        val killingTests = mutationMutantChildren(mutantNode)
        assertEquals(listOf("CalcTest#addsTwoNumbers()"), killingTests.map { it.rawTestId })
    }

    @Test
    fun `headerText shows a relative time when ranAt is known`() {
        val text = headerText(state(targets = listOf("dev.example.Calculator"), ranAt = 1000L), nowMs = 1000L + 5 * 60_000L)
        assertEquals("Target: Calculator · 5 minute(s) ago", text)
    }

    @Test
    fun `headerText falls back to a saved-result note when ranAt is unknown`() {
        val text = headerText(state(ranAt = null), nowMs = 1000L)
        assertTrue(text.contains("saved result"))
    }

    @Test
    fun `noMutationEvidenceMessage picks the specific reason from warnings, not a generic one`() {
        assertTrue(noMutationEvidenceMessage(state(warnings = listOf(Reason("MUTATION_BUDGET_EXCEEDED", "x")))).contains("time budget"))
        assertTrue(noMutationEvidenceMessage(state(warnings = listOf(Reason("MUTATION_CLASSPATH_MISSING", "x")))).contains("classpath"))
        assertTrue(noMutationEvidenceMessage(state(warnings = emptyList())).contains("MUTATION_*"))
    }

    @Test
    fun `scoreText never hides the indeterminate count behind a bare percentage`() {
        val score = scoreOf(listOf(mutant("KILLED"), mutant("NO_COVERAGE")))
        assertTrue(scoreText(score).contains("inconclusive"))
    }

    @Test
    fun `scoreText states the NO_COVERAGE reason outright when every mutant is NO_COVERAGE`() {
        val score = scoreOf(listOf(mutant("NO_COVERAGE")))
        assertEquals("no score - no test reaches this method", scoreText(score, allNoCoverage = true))
    }

    @Test
    fun `bucketText distinguishes a timed-out kill from an ordinary one`() {
        assertEquals("killed (timed out)", bucketText(MutantBucket.KILLED, "TIMED_OUT"))
        assertEquals("killed", bucketText(MutantBucket.KILLED, "KILLED"))
        assertEquals("SURVIVED", bucketText(MutantBucket.SURVIVED, "SURVIVED"))
        assertEquals("inconclusive (NO_COVERAGE)", bucketText(MutantBucket.INDETERMINATE, "NO_COVERAGE"))
    }
}
