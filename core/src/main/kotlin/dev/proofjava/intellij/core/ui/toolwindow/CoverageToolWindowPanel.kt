package dev.proofjava.intellij.core.ui.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import dev.proofjava.intellij.core.model.CoverageNode
import dev.proofjava.intellij.core.model.CoverageSectionId
import dev.proofjava.intellij.core.model.CoverageState
import dev.proofjava.intellij.core.model.coverageChangedFileChildren
import dev.proofjava.intellij.core.model.coverageRootChildren
import dev.proofjava.intellij.core.model.coverageSectionChildren
import dev.proofjava.intellij.core.model.metricTooltip
import dev.proofjava.intellij.core.model.newCodeStatusText
import dev.proofjava.intellij.core.model.percentText
import dev.proofjava.intellij.core.model.warningInfo
import dev.proofjava.intellij.core.state.CoverageStateService
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Swing wiring for `core.model.CoverageTree`'s node list - port of
 * `proof-vscode/src/ui/treeViews/coverageView.ts`. Rebuilds the whole tree
 * on every [refresh] (a `DefaultTreeModel`, not `AbstractTreeStructure`'s
 * lazy-loading machinery) - the Coverage tree is small (a handful of
 * sections/metrics/files), so eager rebuild is simpler and adequate; a
 * lazy structure would be premature for this data size.
 */
class CoverageToolWindowPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private val tree = Tree(DefaultTreeModel(DefaultMutableTreeNode("Coverage")))

    init {
        add(JBScrollPane(tree), BorderLayout.CENTER)
        tree.isRootVisible = false
        tree.addMouseListener(
            object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount != 2) return
                    openIfRange(tree.getPathForLocation(e.x, e.y)?.lastPathComponent as? DefaultMutableTreeNode ?: return)
                }
            },
        )
        CoverageStateService.getInstance(project).addListener(this) { refresh() }
        refresh()
    }

    override fun dispose() = Unit

    fun refresh() {
        val state = CoverageStateService.getInstance(project).state
        val root = DefaultMutableTreeNode("Coverage")
        coverageRootChildren(state).forEach { root.add(buildNode(it, state)) }
        tree.model = DefaultTreeModel(root)
        for (row in tree.rowCount - 1 downTo 0) {
            val node = tree.getPathForRow(row)?.lastPathComponent as? DefaultMutableTreeNode ?: continue
            val entry = node.userObject as? Entry ?: continue
            if (entry.node is CoverageNode.Section && entry.node.id != CoverageSectionId.UNCOVERED && entry.node.id != CoverageSectionId.WARNINGS) {
                tree.expandRow(row)
            }
        }
    }

    private fun buildNode(node: CoverageNode, state: CoverageState?): DefaultMutableTreeNode {
        val treeNode = DefaultMutableTreeNode(Entry(labelFor(node), node))
        val children = when (node) {
            is CoverageNode.Section -> state?.let { coverageSectionChildren(node.id, it) } ?: emptyList()
            is CoverageNode.ChangedFileNode -> coverageChangedFileChildren(node.file)
            else -> emptyList()
        }
        children.forEach { treeNode.add(buildNode(it, state)) }
        return treeNode
    }

    private fun openIfRange(treeNode: DefaultMutableTreeNode) {
        val entry = treeNode.userObject as? Entry ?: return
        val range = entry.node as? CoverageNode.RangeNode ?: return
        val projectRoot = CoverageStateService.getInstance(project).state?.projectRoot ?: return
        val absolutePath = File(projectRoot, range.file.path).path
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(absolutePath) ?: return
        OpenFileDescriptor(project, virtualFile, range.range.first - 1, 0).navigate(true)
    }

    private fun labelFor(node: CoverageNode): String = when (node) {
        is CoverageNode.Empty -> node.message
        is CoverageNode.Section -> sectionLabel(node.id)
        is CoverageNode.MetricNode -> "${node.name}: ${percentText(node.metric)}"
        is CoverageNode.NewCodeStatus -> newCodeStatusText(node.status) + (node.detail?.let { " ($it)" } ?: "")
        is CoverageNode.ChangedFileNode -> "${node.file.path} (${node.file.uncoveredNewRanges?.size ?: 0} uncovered range(s))"
        is CoverageNode.RangeNode -> {
            val (start, end) = node.range
            if (start == end) "Line $start" else "Line $start-$end"
        }
        // Real bug caught in a live runIde run: a project with several
        // UNTRACKED_NON_JAVA_FILE warnings (a real, common code - not in
        // either this or the TS source's own WarningCatalog) rendered as a
        // wall of identical unlabeled rows, since the title alone is the
        // bare code for an unrecognized one. coverageView.ts's own
        // warningItem only puts `reason.path` in a hover tooltip, which
        // doesn't help a Swing tree the same way a VS Code hover would -
        // appending it to the label itself is a deliberate improvement
        // over the TS source, not a straight port.
        is CoverageNode.WarningNode -> {
            val title = warningInfo(node.reason).title
            node.reason.path?.let { "$title: $it" } ?: title
        }
    }

    private fun sectionLabel(id: CoverageSectionId): String = when (id) {
        CoverageSectionId.OVERALL -> "Overall (whole repo)"
        CoverageSectionId.NEW_CODE -> "New Code (only lines in this diff)"
        CoverageSectionId.UNCOVERED -> "Uncovered New Lines"
        CoverageSectionId.WARNINGS -> "Warnings"
    }

    /** Wraps a [CoverageNode] with its rendered label - [toString] drives the default tree cell renderer's text, so no custom `TreeCellRenderer` is needed for M3. Metric tooltips ([metricTooltip]) are not wired here yet - a later polish pass. */
    private class Entry(private val label: String, val node: CoverageNode) {
        override fun toString(): String = label
    }
}
