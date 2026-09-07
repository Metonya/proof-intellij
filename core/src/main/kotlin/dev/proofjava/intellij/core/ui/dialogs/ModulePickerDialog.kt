package dev.proofjava.intellij.core.ui.dialogs

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CheckBoxList
import com.intellij.ui.components.JBScrollPane
import java.awt.Dimension
import javax.swing.JComponent

/**
 * Generic multi-select module picker - port of `proof-vscode/src/ui/preflight.ts`'s
 * `resolveRunTestsModuleScope` QuickPick, as a real `DialogWrapper` (no
 * IntelliJ Platform equivalent of VS Code's `showQuickPick` with
 * checkboxes and per-row descriptions exists as a one-line call, so this
 * is a small dedicated dialog rather than reaching for something that
 * doesn't quite fit). Lives in `core` - which items are offered and
 * whether one starts checked is entirely the caller's (an [dev.proofjava.intellij.core.engine.Engine]'s)
 * decision, not this dialog's.
 */
data class ModulePickerItem(val root: String, val label: String, val description: String?, val initiallyChecked: Boolean)

class ModulePickerDialog(project: Project, dialogTitle: String, private val items: List<ModulePickerItem>) : DialogWrapper(project) {
    private val list = CheckBoxList<ModulePickerItem>()

    init {
        title = dialogTitle
        for (item in items) {
            val text = if (item.description != null) "${item.label}  -  ${item.description}" else item.label
            list.addItem(item, text, item.initiallyChecked)
        }
        init()
    }

    override fun createCenterPanel(): JComponent {
        val scroll = JBScrollPane(list)
        scroll.preferredSize = Dimension(520, 320)
        return scroll
    }

    /** Roots the user left checked when they confirmed the dialog - call only after [showAndGet] returned `true`. */
    fun selectedRoots(): List<String> {
        val result = mutableListOf<String>()
        for (i in items.indices) {
            if (list.isItemSelected(i)) {
                items[i].let { result += it.root }
            }
        }
        return result
    }
}
