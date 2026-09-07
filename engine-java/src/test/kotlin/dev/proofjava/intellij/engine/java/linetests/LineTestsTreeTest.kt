package dev.proofjava.intellij.engine.java.linetests

import dev.proofjava.intellij.core.verdict.Confidence
import dev.proofjava.intellij.core.verdict.Finding
import dev.proofjava.intellij.core.verdict.PerTestBlock
import dev.proofjava.intellij.core.verdict.PerTestEntry
import dev.proofjava.intellij.core.verdict.PerTestLine
import dev.proofjava.intellij.core.verdict.PerTestModuleEvidence
import dev.proofjava.intellij.core.verdict.RuleId
import dev.proofjava.intellij.core.verdict.Severity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LineTestsTreeTest {

    @Test
    fun `no perTest block at all yields a single empty node`() {
        val children = lineTestsRootChildren(null, null)
        assertEquals(1, children.size)
        assertTrue(children[0] is LineTestsNode.Empty)
    }

    @Test
    fun `an empty modules list yields a single empty node, distinct wording from no-block`() {
        val children = lineTestsRootChildren(PerTestBlock("pitest", "1.15.8", emptyList()), null)
        assertEquals(1, children.size)
        assertTrue((children[0] as LineTestsNode.Empty).message.contains("No line records"))
    }

    private val block = PerTestBlock(
        "pitest", "1.15.8",
        listOf(
            PerTestModuleEvidence(
                "root",
                listOf(PerTestEntry("dev.example.Calculator", "add", listOf(PerTestLine(7, listOf("CalcTest#addsTwoNumbers()"))))),
                emptyList(),
            ),
        ),
    )

    @Test
    fun `one class in perTest yields one ClassNode`() {
        val children = lineTestsRootChildren(block, null)
        assertEquals(1, children.size)
        val classNode = children[0] as LineTestsNode.ClassNode
        assertEquals("dev.example.Calculator", classNode.className)
    }

    @Test
    fun `a class node's children are its grouped production lines`() {
        val classNode = lineTestsRootChildren(block, null)[0] as LineTestsNode.ClassNode
        val lineNodes = lineTestsClassChildren(classNode)
        assertEquals(1, lineNodes.size)
        val prodLine = lineNodes[0] as LineTestsNode.ProdLineNode
        assertEquals(7, prodLine.startLine)
        assertEquals(listOf("CalcTest#addsTwoNumbers()"), prodLine.tests)
    }

    @Test
    fun `a prod-line node's children are per-test verdict nodes`() {
        val finding = Finding(
            rule = RuleId.NO_RECOGNIZED_ORACLE, severity = Severity.WARNING, confidence = Confidence.HIGH, module = "root",
            path = "x", startLine = 1, endLine = 1, message = "m", suggestedAction = "a", fingerprint = "f",
            testMethod = "CalcTest#addsTwoNumbers()",
        )
        val findingsByTestMethod = indexFindingsByTestMethod(listOf(finding))
        val classNode = lineTestsRootChildren(block, null)[0] as LineTestsNode.ClassNode
        val prodLine = lineTestsClassChildren(classNode)[0] as LineTestsNode.ProdLineNode
        val testNodes = lineTestsProdLineChildren(prodLine, findingsByTestMethod)
        assertEquals(1, testNodes.size)
        val testNode = testNodes[0] as LineTestsNode.ProdTestNode
        assertEquals(TestVerdict.NO_ORACLE, testNode.verdict)
        assertEquals("NO_RECOGNIZED_ORACLE", testNode.finding?.rule?.name)
    }

    @Test
    fun `a production-class filter drops test classes from the root`() {
        val withTestClass = PerTestBlock(
            "pitest", "1.15.8",
            listOf(
                PerTestModuleEvidence(
                    "root",
                    listOf(
                        PerTestEntry("dev.example.Calculator", "add", listOf(PerTestLine(7, listOf("CalcTest#addsTwoNumbers()")))),
                        PerTestEntry("dev.example.CalcTest", "addsTwoNumbers", listOf(PerTestLine(5, listOf("CalcTest#addsTwoNumbers()")))),
                    ),
                    emptyList(),
                ),
            ),
        )
        val children = lineTestsRootChildren(withTestClass) { it == "dev.example.Calculator" }
        assertEquals(1, children.size)
        assertEquals("dev.example.Calculator", (children[0] as LineTestsNode.ClassNode).className)
    }
}
