package dev.proofjava.intellij.core.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import dev.proofjava.intellij.core.engine.TestRunResult
import dev.proofjava.intellij.core.ui.dialogs.ModulePickerDialog
import dev.proofjava.intellij.core.ui.dialogs.ModulePickerItem

/**
 * "Run Tests" - port of `proof-vscode/src/ui/preflight.ts`'s
 * `resolveRunTestsModuleScope` + `offerToRunTestsNow`'s "run the tests
 * ourselves" gesture, and `ui/mavenTestTask.ts`/`ui/gradleTestTask.ts`'s
 * task drivers, reached only through [dev.proofjava.intellij.core.engine.Engine]
 * (`discoverModules`/`runTests`/`interpretTestFailure`) - the first
 * substantial `engine-java`-only milestone the plan calls for. On success,
 * triggers the same [runQuickScan] `Quick Scan` uses (mirrors the TS
 * source's own `runTestsTask` -> auto `runAnalyze` chain).
 *
 * Module-picker simplification, disclosed: the TS source silently infers
 * scope from the active editor's file (no prompt at all) before falling
 * back to a picker. That signal needs an editor-to-module mapping this
 * milestone does not build yet - every multi-module run here always shows
 * the picker.
 */
class RunTestsAction : AnAction("Proof: Run Tests") {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val engine = requireEngine(project) ?: return

        val modules = engine.discoverModules(project)
        val moduleRoots: List<String> = when {
            modules.size <= 1 -> emptyList()
            else -> {
                val items = modules.map {
                    ModulePickerItem(root = it.root, label = if (it.root == ".") ". (project root)" else it.root, description = null, initiallyChecked = true)
                }
                val dialog = ModulePickerDialog(project, "Proof: Which Module(s) Should the Tests Run In?", items)
                if (!dialog.showAndGet()) return
                val picked = dialog.selectedRoots()
                if (picked.isEmpty()) return
                if (picked.size == modules.size) emptyList() else picked
            }
        }

        object : Task.Backgroundable(project, "Proof: running tests", true) {
            override fun run(indicator: ProgressIndicator) {
                val result = engine.runTests(project, moduleRoots, indicator)
                when (result) {
                    is TestRunResult.NotRun -> Unit // already explained itself (a declined dialog, or nothing to run) - no further message
                    is TestRunResult.Completed -> {
                        if (!result.success) {
                            val interpretation = engine.interpretTestFailure(result.capturedOutput)
                            val suffix = interpretation?.let { " Reason: $it" } ?: " See the log for detail."
                            showErrorLater(project, "Proof: the build failed.$suffix")
                            return
                        }
                        runQuickScan(project, indicator)
                    }
                }
            }
        }.queue()
    }
}
