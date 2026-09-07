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
import dev.proofjava.intellij.core.cli.TargetBinding
import dev.proofjava.intellij.core.engine.EvidenceInputsResult
import dev.proofjava.intellij.core.engine.EvidenceKind
import dev.proofjava.intellij.core.engine.ModuleBinding
import dev.proofjava.intellij.core.engine.ReportBindingResult
import dev.proofjava.intellij.core.engine.bindTargetsToModules
import dev.proofjava.intellij.core.model.PerTestState
import dev.proofjava.intellij.core.model.coverageStateFrom
import dev.proofjava.intellij.core.state.CoverageStateService
import dev.proofjava.intellij.core.state.PerTestStateService
import dev.proofjava.intellij.core.ui.gutter.applyGutterCoverage
import dev.proofjava.intellij.core.util.proofStorageFile
import dev.proofjava.intellij.core.verdict.PerTestSnapshot
import dev.proofjava.intellij.core.verdict.writePerTestSnapshotJson

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
 * [DeepScanWholeModuleAction] (below, in this same file) is the diff-free
 * sibling - port of `commands.ts`'s `proof.perTestForModuleAll`. `targets`
 * is empty (diff-derived) for this one, matching [PerTestState.targets]'s
 * own "empty means diff-derived" contract; the single-class
 * (`proof.perTestForFile`, right-click-driven) variant is still not built -
 * a real, disclosed gap, not an oversight.
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

/**
 * Diff-free sibling of [DeepScanAction] - port of `commands.ts`'s
 * `proof.perTestForModuleAll`: scans every production class this run's
 * own `fileCoverage` already knows about, not just the ones the diff
 * touched. Needs a prior Quick Scan (the production file list comes from
 * its `fileCoverage` block, not a fresh filesystem walk) - the same
 * requirement the TS source has.
 */
class DeepScanWholeModuleAction : AnAction("Proof: Deep Scan Whole Module (no diff)") {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        object : Task.Backgroundable(project, "Proof: deep scanning (whole module)", true) {
            override fun run(indicator: ProgressIndicator) = runDeepScanWholeModule(project, indicator)
        }.queue()
    }
}

fun runDeepScan(project: Project, indicator: ProgressIndicator) {
    val engine = requireEngine(project) ?: return
    // The modules to resolve evidence classpaths for are exactly the ones
    // Quick Scan would bind coverage to - not a separate discoverModules()
    // call, which could name modules with no report at all.
    val modules = resolveModulesOrShowError(project, engine.resolveReportBinding(project, DEFAULT_REPORT_PATH)) ?: return
    runDeepScanCore(project, indicator, modules, targets = emptyList())
}

fun runDeepScanWholeModule(project: Project, indicator: ProgressIndicator) {
    val engine = requireEngine(project) ?: return
    val coverageState = CoverageStateService.getInstance(project).state
    if (coverageState?.fileCoverage == null) {
        showErrorLater(project, "Proof: run Quick Scan first - scanning the whole module without a diff needs the production file list.")
        return
    }
    val classTargets = engine.productionClassTargets(coverageState)
    if (classTargets.isEmpty()) {
        showErrorLater(project, "Proof: no targetable production class found in this module.")
        return
    }
    val modules = resolveModulesOrShowError(project, engine.resolveReportBinding(project, DEFAULT_REPORT_PATH)) ?: return
    val targets = bindTargetsToModules(classTargets, modules)
    if (targets.isEmpty()) {
        showErrorLater(project, "Proof: could not determine which module the target class(es) belong to.")
        return
    }
    runDeepScanCore(project, indicator, modules, targets)
}

internal fun resolveModulesOrShowError(project: Project, binding: ReportBindingResult): List<ModuleBinding>? = when (binding) {
    is ReportBindingResult.SingleModule -> listOf(ModuleBinding("root", "."))
    is ReportBindingResult.MultiModule -> binding.modules.map { ModuleBinding(it.id, it.root) }
    is ReportBindingResult.NotFound -> {
        showErrorLater(project, "No coverage report found at $DEFAULT_REPORT_PATH - run your tests with JaCoCo first (Proof: Run Tests).")
        null
    }
}

private fun runDeepScanCore(project: Project, indicator: ProgressIndicator, modules: List<ModuleBinding>, targets: List<TargetBinding>) {
    val engine = requireEngine(project) ?: return
    val cli = requireCli(project, engine) ?: return
    val repo = project.basePath ?: return

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
        targets = targets,
        timeoutSeconds = DEFAULT_PER_TEST_TIMEOUT_SECONDS,
    )
    val document = runAnalyzeCore(project, indicator, engine, cli, repo, perTest) ?: return

    val perTestBlock = document.perTest
    val targetFqcns = targets.map { it.fqcn }
    val ranAt = System.currentTimeMillis()
    // Best-effort, same as proof-vscode's own writeJsonSnapshot: a failed
    // write does not fail the scan itself - the result is already
    // published in memory, only a later window reload would lose it.
    // Written on this background thread (already off the EDT, same
    // Task.Backgroundable contract every action here follows), not
    // inside the invokeLater below - no reason to make the UI thread
    // wait on disk I/O.
    if (perTestBlock != null) {
        runCatching {
            proofStorageFile(repo, "pertest-current.json")
                .writeText(writePerTestSnapshotJson(PerTestSnapshot(perTestBlock, document.warnings, targetFqcns, ranAt)))
        }
    }

    ApplicationManager.getApplication().invokeLater {
        val coverageState = coverageStateFrom(repo, document)
        CoverageStateService.getInstance(project).publish(coverageState)
        coverageState.fileCoverage?.let { applyGutterCoverage(project, repo, it) }

        PerTestStateService.getInstance(project).publish(
            PerTestState(perTest = perTestBlock, warnings = document.warnings, targets = targetFqcns, ranAt = ranAt),
        )
        if (perTestBlock == null) {
            showWarningLater(project, "Proof: no per-test evidence for this run - see the Warnings section in the Coverage view for PER_TEST_* detail.")
        }
    }
}

internal fun showWarningLater(project: Project, message: String) {
    ApplicationManager.getApplication().invokeLater {
        com.intellij.openapi.ui.Messages.showWarningDialog(project, message, "Proof")
    }
}
