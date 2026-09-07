package dev.proofjava.intellij.core.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import dev.proofjava.intellij.core.cli.ClasspathBinding
import dev.proofjava.intellij.core.cli.EvidenceInput
import dev.proofjava.intellij.core.engine.EvidenceInputsResult
import dev.proofjava.intellij.core.engine.EvidenceKind
import dev.proofjava.intellij.core.engine.ModuleBinding
import dev.proofjava.intellij.core.engine.ReportBindingResult
import dev.proofjava.intellij.core.model.PerTestState
import dev.proofjava.intellij.core.model.coverageStateFrom
import dev.proofjava.intellij.core.state.CoverageStateService
import dev.proofjava.intellij.core.state.PerTestStateService
import dev.proofjava.intellij.core.ui.gutter.applyGutterCoverage

private const val DEFAULT_PER_TEST_TIMEOUT_SECONDS = 120

/**
 * "Deep Scan" - port of `proof-vscode`'s `proof.analyzePerTest` command
 * (`ui/commands.ts`, the `--per-test-report` shape of `runAnalyzeCore`).
 * Reuses [runAnalyzeCore] (the exact same report-binding/run/parse
 * pipeline Quick Scan uses) with `perTest` evidence flags added - the
 * classpath list(s) that evidence needs come from
 * [dev.proofjava.intellij.core.engine.Engine.resolveEvidenceInputs]
 * (`engine-java`'s `doctor --fix` orchestration, M6), reached only through
 * the `Engine` interface, same boundary discipline as every other action.
 *
 * Simplification disclosed: the TS source's diff-free single-class/whole-
 * module-no-diff variants (`proof.perTestForFile`/`perTestForModuleAll`,
 * right-click-driven) are not built yet - this is only the diff-scoped
 * "Deep Scan" command. `targets` is therefore always empty (diff-derived),
 * matching [PerTestState.targets]'s own "empty means diff-derived"
 * contract.
 */
class DeepScanAction : AnAction("Proof: Deep Scan") {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        object : Task.Backgroundable(project, "Proof: deep scanning", true) {
            override fun run(indicator: ProgressIndicator) = runDeepScan(project, indicator)
        }.queue()
    }
}

fun runDeepScan(project: Project, indicator: ProgressIndicator) {
    val engine = requireEngine(project) ?: return
    val cli = requireCli(project, engine) ?: return
    val repo = project.basePath ?: return

    // The modules to resolve evidence classpaths for are exactly the ones
    // Quick Scan would bind coverage to - not a separate discoverModules()
    // call, which could name modules with no report at all.
    val binding = engine.resolveReportBinding(project, DEFAULT_REPORT_PATH)
    val modules = when (binding) {
        is ReportBindingResult.SingleModule -> listOf(ModuleBinding("root", "."))
        is ReportBindingResult.MultiModule -> binding.modules.map { ModuleBinding(it.id, it.root) }
        is ReportBindingResult.NotFound -> {
            showErrorLater(project, "No coverage report found at $DEFAULT_REPORT_PATH - run your tests with JaCoCo first (Proof: Run Tests).")
            return
        }
    }

    indicator.text = "resolving classpath list(s)"
    val evidence = engine.resolveEvidenceInputs(project, modules, EvidenceKind.PER_TEST, indicator)
    val classpaths = when (evidence) {
        is EvidenceInputsResult.Unavailable -> {
            showErrorLater(project, "Proof: Deep Scan can't run without a classpath list - the scan was never started.")
            return
        }
        is EvidenceInputsResult.Resolved -> {
            if (evidence.missingModuleRoots.isNotEmpty()) {
                showWarningLater(project, "Proof: deep evidence won't be collected for these modules (classpath couldn't be generated): ${evidence.missingModuleRoots.joinToString(", ")}. Coverage will still be computed.")
            }
            evidence.classpaths
        }
    }
    if (classpaths.isEmpty()) {
        showErrorLater(project, "Proof: Deep Scan can't run without a classpath list - the scan was never started.")
        return
    }

    val perTest = EvidenceInput(
        classpaths = classpaths.map { ClasspathBinding(it.moduleId, it.path) },
        timeoutSeconds = DEFAULT_PER_TEST_TIMEOUT_SECONDS,
    )
    val document = runAnalyzeCore(project, indicator, engine, cli, repo, perTest) ?: return

    ApplicationManager.getApplication().invokeLater {
        val coverageState = coverageStateFrom(repo, document)
        CoverageStateService.getInstance(project).publish(coverageState)
        coverageState.fileCoverage?.let { applyGutterCoverage(project, repo, it) }

        PerTestStateService.getInstance(project).publish(
            PerTestState(perTest = document.perTest, warnings = document.warnings, targets = emptyList(), ranAt = System.currentTimeMillis()),
        )
    }
}

private fun showWarningLater(project: Project, message: String) {
    ApplicationManager.getApplication().invokeLater {
        com.intellij.openapi.ui.Messages.showWarningDialog(project, message, "Proof")
    }
}
