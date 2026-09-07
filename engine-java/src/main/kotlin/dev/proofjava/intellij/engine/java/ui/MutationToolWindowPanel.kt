package dev.proofjava.intellij.engine.java.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import dev.proofjava.intellij.core.model.CoverageState
import dev.proofjava.intellij.core.state.CoverageStateService
import dev.proofjava.intellij.core.state.MutationStateService
import dev.proofjava.intellij.engine.java.linetests.locateTestFile
import dev.proofjava.intellij.engine.java.mutation.MutantBucket
import dev.proofjava.intellij.engine.java.mutation.MutationNode
import dev.proofjava.intellij.engine.java.mutation.bucketOf
import dev.proofjava.intellij.engine.java.mutation.methodLabel
import dev.proofjava.intellij.engine.java.mutation.mutatorLabel
import dev.proofjava.intellij.engine.java.mutation.mutationClassChildren
import dev.proofjava.intellij.engine.java.mutation.mutationMethodChildren
import dev.proofjava.intellij.engine.java.mutation.mutationMutantChildren
import dev.proofjava.intellij.engine.java.mutation.mutationRootChildren
import dev.proofjava.intellij.engine.java.mutation.scoreOf
import dev.proofjava.intellij.engine.java.mutation.scoreOfMethods
import dev.proofjava.intellij.engine.java.mutation.scoreText
import dev.proofjava.intellij.engine.java.source.buildProductionClassIndex
import dev.proofjava.intellij.engine.java.source.productionClassFilter
import dev.proofjava.intellij.engine.java.source.productionSourceRoots
import dev.proofjava.intellij.engine.java.source.sourceModuleRoots
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
 * Swing wiring for `mutation.MutationTree`'s node list, port of
 * `proof-vscode/src/ui/treeViews/mutationView.ts`. Double-click navigates
 * (a method/mutant to its source line, a killing test to its own file) -
 * same mechanism `LineTestsToolWindowPanel` already uses, not the TS
 * source's `.command`-per-item.
 *
 * The survivors-only toggle is a checkbox at the top of the panel, not a
 * tool-window title-bar action: `ToolWindow.setTitleActions` (M6's
 * `CoverageToolWindowFactory`) is shared across every tab of the "Proof"
 * window, but this toggle is Mutation-tab-specific.
 */
class MutationToolWindowPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private val tree = Tree(DefaultTreeModel(DefaultMutableTreeNode("Mutation")))
    private val survivorsOnlyCheckBox = JBCheckBox("Survivors only")

    init {
        add(survivorsOnlyCheckBox, BorderLayout.NORTH)
        add(JBScrollPane(tree), BorderLayout.CENTER)
        tree.isRootVisible = false
        survivorsOnlyCheckBox.addActionListener { refresh() }
        tree.addMouseListener(
            object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount != 2) return
                    openIfNavigable(tree.getPathForLocation(e.x, e.y)?.lastPathComponent as? DefaultMutableTreeNode ?: return)
                }
            },
        )
        CoverageStateService.getInstance(project).addListener(this) { refresh() }
        MutationStateService.getInstance(project).addListener(this) { refresh() }
        refresh()
    }

    override fun dispose() = Unit

    fun refresh() {
        val mutationState = MutationStateService.getInstance(project).state
        val coverageState = CoverageStateService.getInstance(project).state
        val survivorsOnly = survivorsOnlyCheckBox.isSelected

        val root = DefaultMutableTreeNode("Mutation")
        mutationRootChildren(mutationState, survivorsOnly, productionClassFilter(coverageState)).forEach { root.add(buildNode(it, survivorsOnly)) }
        tree.model = DefaultTreeModel(root)
        for (row in 0 until tree.rowCount) {
            tree.expandRow(row)
        }
    }

    private fun buildNode(node: MutationNode, survivorsOnly: Boolean): DefaultMutableTreeNode {
        val treeNode = DefaultMutableTreeNode(Entry(labelFor(node), node))
        val children: List<MutationNode> = when (node) {
            is MutationNode.ClassNode -> mutationClassChildren(node, survivorsOnly)
            is MutationNode.MethodNode -> mutationMethodChildren(node)
            is MutationNode.MutantNode -> mutationMutantChildren(node)
            else -> emptyList()
        }
        children.forEach { treeNode.add(buildNode(it, survivorsOnly)) }
        return treeNode
    }

    private fun openIfNavigable(treeNode: DefaultMutableTreeNode) {
        val entry = treeNode.userObject as? Entry ?: return
        val coverageState = CoverageStateService.getInstance(project).state ?: return
        when (val node = entry.node) {
            is MutationNode.MethodNode -> openClassLine(coverageState, node.className, node.method.firstLine)
            is MutationNode.MutantNode -> openClassLine(coverageState, node.className, node.mutant.line)
            is MutationNode.KillingTestNode -> openKillingTest(coverageState, node.rawTestId)
            else -> Unit
        }
    }

    private fun openClassLine(coverageState: CoverageState, className: String, line: Int) {
        val fileCoverage = coverageState.fileCoverage ?: return
        val path = buildProductionClassIndex(fileCoverage, productionSourceRoots(sourceModuleRoots(coverageState))).byClassName[className] ?: return
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(toAbsolutePath(coverageState.projectRoot, path)) ?: return
        OpenFileDescriptor(project, virtualFile, line - 1, 0).navigate(true)
    }

    private fun openKillingTest(coverageState: CoverageState, rawTestId: String) {
        val identity = parseTestIdentity(rawTestId)
        val className = identity.className ?: return
        val relativePath = locateTestFile(coverageState.projectRoot, testSourceRoots(sourceModuleRoots(coverageState)), className, null) ?: return
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(toAbsolutePath(coverageState.projectRoot, relativePath)) ?: return
        OpenFileDescriptor(project, virtualFile, 0, 0).navigate(true)
    }

    private fun labelFor(node: MutationNode): String = when (node) {
        is MutationNode.Empty -> node.message
        is MutationNode.Header -> node.text
        is MutationNode.ClassNode -> "${shortName(node.className)} (${scoreText(scoreOfMethods(node.methods))})"
        is MutationNode.MethodNode -> "${methodLabel(node.method, node.siblings)} (${scoreText(scoreOf(node.method.mutants))})"
        is MutationNode.MutantNode -> "${bucketIcon(node.mutant.status)} Line ${node.mutant.line} · ${mutatorLabel(node.mutant.mutator)} (${node.mutant.status})"
        is MutationNode.KillingTestNode -> "✓ ${parseTestIdentity(node.rawTestId).display}"
    }

    private fun bucketIcon(status: String): String = when (bucketOf(status)) {
        MutantBucket.KILLED -> "✓"
        MutantBucket.SURVIVED -> "⚠"
        MutantBucket.INDETERMINATE -> "?"
    }

    private fun shortName(fqcn: String): String {
        val dot = fqcn.lastIndexOf('.')
        return if (dot < 0) fqcn else fqcn.substring(dot + 1)
    }

    private class Entry(private val label: String, val node: MutationNode) {
        override fun toString(): String = label
    }
}
