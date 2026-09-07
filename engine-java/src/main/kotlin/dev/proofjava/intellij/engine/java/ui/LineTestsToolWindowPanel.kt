package dev.proofjava.intellij.engine.java.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import dev.proofjava.intellij.core.model.CoverageState
import dev.proofjava.intellij.core.state.CoverageStateService
import dev.proofjava.intellij.core.state.PerTestStateService
import dev.proofjava.intellij.core.verdict.Finding
import dev.proofjava.intellij.engine.java.linetests.LineTestsNode
import dev.proofjava.intellij.engine.java.linetests.TestVerdict
import dev.proofjava.intellij.engine.java.linetests.indexFindingsByTestMethod
import dev.proofjava.intellij.engine.java.linetests.lineTestsClassChildren
import dev.proofjava.intellij.engine.java.linetests.lineTestsProdLineChildren
import dev.proofjava.intellij.engine.java.linetests.lineTestsRootChildren
import dev.proofjava.intellij.engine.java.linetests.locateTestFile
import dev.proofjava.intellij.engine.java.source.SourceModuleRoots
import dev.proofjava.intellij.engine.java.source.buildProductionClassIndex
import dev.proofjava.intellij.engine.java.source.productionSourceRoots
import dev.proofjava.intellij.engine.java.source.testSourceRoots
import dev.proofjava.intellij.engine.java.source.toAbsolutePath
import dev.proofjava.intellij.engine.java.verdict.parseTestIdentity
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Swing wiring for `linetests.LineTestsTree`'s node list - "Line → Tests",
 * port of `proof-vscode/src/ui/treeViews/lineTestsView.ts` (the "all
 * classes, no active file" landing mode only - see `LineTestsTree.kt`'s
 * own doc comment for what's disclosed as deferred). Registered as its
 * own top-level tool window (`plugin.xml`), not a second tab merged into
 * `core.ui.toolwindow.CoverageToolWindowFactory`'s "Proof" window - `core`
 * cannot reference this `engine-java` class directly without breaking the
 * module boundary this whole plan is built around, and a real "pluggable
 * tab" extension point is more infrastructure than this milestone needs
 * for a second engine that does not exist yet.
 */
class LineTestsToolWindowPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private val tree = Tree(DefaultTreeModel(DefaultMutableTreeNode("Line → Tests")))

    init {
        add(JBScrollPane(tree), BorderLayout.CENTER)
        tree.isRootVisible = false
        tree.addMouseListener(
            object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount != 2) return
                    openIfTest(tree.getPathForLocation(e.x, e.y)?.lastPathComponent as? DefaultMutableTreeNode ?: return)
                }
            },
        )
        CoverageStateService.getInstance(project).addListener(this) { refresh() }
        PerTestStateService.getInstance(project).addListener(this) { refresh() }
        refresh()
    }

    override fun dispose() = Unit

    fun refresh() {
        val coverageState = CoverageStateService.getInstance(project).state
        val perTest = PerTestStateService.getInstance(project).state?.perTest
        val findingsByTestMethod = indexFindingsByTestMethod(coverageState?.findings ?: emptyList())
        val productionFilter = productionClassFilter(coverageState)

        val root = DefaultMutableTreeNode("Line → Tests")
        lineTestsRootChildren(perTest, productionFilter).forEach { root.add(buildNode(it, findingsByTestMethod)) }
        tree.model = DefaultTreeModel(root)
        for (row in 0 until tree.rowCount) {
            tree.expandRow(row)
        }
    }

    private fun buildNode(node: LineTestsNode, findingsByTestMethod: Map<String, Finding>): DefaultMutableTreeNode {
        val treeNode = DefaultMutableTreeNode(Entry(labelFor(node), node))
        val children = when (node) {
            is LineTestsNode.ClassNode -> lineTestsClassChildren(node)
            is LineTestsNode.ProdLineNode -> lineTestsProdLineChildren(node, findingsByTestMethod)
            else -> emptyList()
        }
        children.forEach { treeNode.add(buildNode(it, findingsByTestMethod)) }
        return treeNode
    }

    private fun openIfTest(treeNode: DefaultMutableTreeNode) {
        val entry = treeNode.userObject as? Entry ?: return
        val testNode = entry.node as? LineTestsNode.ProdTestNode ?: return
        val identity = parseTestIdentity(testNode.rawTestId)
        val className = identity.className ?: return
        val coverageState = CoverageStateService.getInstance(project).state ?: return
        val relativePath = locateTestFile(coverageState.projectRoot, testSourceRoots(sourceModuleRoots(coverageState)), className, testNode.finding?.path) ?: return
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(toAbsolutePath(coverageState.projectRoot, relativePath)) ?: return
        val startLine = testNode.finding?.startLine ?: 1
        OpenFileDescriptor(project, virtualFile, startLine - 1, 0).navigate(true)
    }

    private fun labelFor(node: LineTestsNode): String = when (node) {
        is LineTestsNode.Empty -> node.message
        is LineTestsNode.ClassNode -> shortName(node.className)
        is LineTestsNode.ProdLineNode -> {
            val base = if (node.startLine == node.endLine) "Line ${node.startLine}" else "Line ${node.startLine}-${node.endLine}"
            val withMethod = node.methodName?.let { "$base · $it()" } ?: base
            "$withMethod (${node.tests.size} test(s))"
        }
        is LineTestsNode.ProdTestNode -> "${verdictIcon(node.verdict)} ${parseTestIdentity(node.rawTestId).display}"
    }

    private fun verdictIcon(verdict: TestVerdict): String = when (verdict) {
        TestVerdict.OK -> "✓"
        TestVerdict.INCONCLUSIVE -> "?"
        else -> "⚠"
    }

    private fun shortName(fqcn: String): String {
        val dot = fqcn.lastIndexOf('.')
        return if (dot < 0) fqcn else fqcn.substring(dot + 1)
    }

    private class Entry(private val label: String, val node: LineTestsNode) {
        override fun toString(): String = label
    }
}

/** Bridges `core.model.CoverageState`'s generic `ModuleInput.sourceRoots`/`testRoots` to the Java-specific [SourceModuleRoots] shape `source.PathIndex`'s functions expect. */
private fun sourceModuleRoots(coverageState: CoverageState): List<SourceModuleRoots> =
    coverageState.modules.map { SourceModuleRoots(it.sourceRoots, it.testRoots) }

/**
 * Which classes are production, from `fileCoverage.files[]` (this run's own
 * authoritative list) - `null` (no filter, nothing excluded) when there is
 * no `fileCoverage` block to build one from, matching `lineTestsView.ts`'s
 * own "missing information must not silently delete evidence" rule. A
 * top-level function, not inlined into a `?.let { }` chain - Kotlin parsed
 * a bare trailing lambda there as an argument to `let` itself rather than
 * this function's return value, a real parse-ambiguity bug caught by the
 * compiler, not designed around in advance.
 */
private fun productionClassFilter(coverageState: CoverageState?): ((String) -> Boolean)? {
    val fileCoverage = coverageState?.fileCoverage ?: return null
    val index = buildProductionClassIndex(fileCoverage, productionSourceRoots(sourceModuleRoots(coverageState)))
    return { outerClassName -> index.byClassName.containsKey(outerClassName) }
}
