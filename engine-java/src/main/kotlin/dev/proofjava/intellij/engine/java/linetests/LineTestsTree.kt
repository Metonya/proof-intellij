package dev.proofjava.intellij.engine.java.linetests

import dev.proofjava.intellij.core.verdict.Finding
import dev.proofjava.intellij.core.verdict.PerTestBlock

/**
 * Node-building logic for the Line → Tests tool window - port of the
 * "all classes, no active file needed" landing mode from
 * `proof-vscode/src/ui/treeViews/lineTestsView.ts` (its own Faz 31: "always
 * show the whole run", same pattern the Mutation view uses). Kept pure
 * (Swing-free), matching `core.model.CoverageTree`'s own split between
 * "what the tree contains" and "how it's painted".
 *
 * Disclosed simplification from the TS source: no active-editor tracking
 * (the TS source's `setActiveDocument`-driven directional view - jump
 * straight to the open class, or reverse-direction from an open test
 * file) - this always shows the "all classes" root. A real, valuable
 * enhancement left for later, not attempted here. Also not ported: the L3
 * mutation-contradiction bridge (`findContradictionEvidence`) - the
 * Mutation view itself does not exist yet (M7).
 */

sealed interface LineTestsNode {
    data class Empty(val message: String) : LineTestsNode
    data class ClassNode(val className: String, val linesToTests: Map<Int, List<String>>, val linesToMethod: Map<Int, String>) : LineTestsNode
    data class ProdLineNode(val startLine: Int, val endLine: Int, val tests: List<String>, val methodName: String?) : LineTestsNode
    data class ProdTestNode(val rawTestId: String, val verdict: TestVerdict, val finding: Finding?) : LineTestsNode
}

fun lineTestsRootChildren(perTest: PerTestBlock?, isProductionClass: ((String) -> Boolean)?): List<LineTestsNode> {
    if (perTest == null) {
        return listOf(LineTestsNode.Empty("No per-test evidence for this run - run Deep Scan first."))
    }
    val classes = allClasses(perTest, isProductionClass)
    return if (classes.isEmpty()) {
        listOf(LineTestsNode.Empty("No line records for any class in this run."))
    } else {
        classes.map { LineTestsNode.ClassNode(it.className, it.linesToTests, it.linesToMethod) }
    }
}

fun lineTestsClassChildren(node: LineTestsNode.ClassNode): List<LineTestsNode> =
    groupConsecutiveLines(node.linesToTests, node.linesToMethod).map {
        LineTestsNode.ProdLineNode(it.startLine, it.endLine, it.tests, it.methodName)
    }

fun lineTestsProdLineChildren(node: LineTestsNode.ProdLineNode, findingsByTestMethod: Map<String, Finding>): List<LineTestsNode> =
    lineQuality(node.tests, findingsByTestMethod).tests.map { LineTestsNode.ProdTestNode(it.rawTestId, it.verdict, it.finding) }
