package dev.proofjava.intellij.core.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import dev.proofjava.intellij.core.cli.ClasspathBinding
import dev.proofjava.intellij.core.cli.DiffMode
import dev.proofjava.intellij.core.cli.EvidenceInput
import dev.proofjava.intellij.core.cli.TargetBinding
import dev.proofjava.intellij.core.engine.ClassTarget
import dev.proofjava.intellij.core.engine.EvidenceInputsResult
import dev.proofjava.intellij.core.engine.EvidenceKind
import dev.proofjava.intellij.core.engine.ModuleBinding
import dev.proofjava.intellij.core.engine.bindTargetsToModules
import dev.proofjava.intellij.core.model.MutationState
import dev.proofjava.intellij.core.model.coverageStateFrom
import dev.proofjava.intellij.core.settings.ProofSettingsState
import dev.proofjava.intellij.core.state.CoverageStateService
import dev.proofjava.intellij.core.state.MutationStateService
import dev.proofjava.intellij.core.ui.gutter.applyGutterCoverage
import dev.proofjava.intellij.core.util.proofStorageFile
import dev.proofjava.intellij.core.verdict.MutationSnapshot
import dev.proofjava.intellij.core.verdict.writeMutationSnapshotJson

private const val DEFAULT_MUTATION_TIMEOUT_SECONDS = 300

/**
 * "Mutation Testing (Module)" - port of `proof-vscode`'s `proof.mutationForModule`
 * command (`ui/commands.ts`'s `runMutationForModule`/`runMutation`, the
 * `--mutation-report` shape of `runAnalyzeCore`). Never auto-triggered,
 * always confirmed first - runtime grows linearly with the target count
 * and the user must knowingly opt in, same reasoning `commands.ts` itself
 * gives.
 *
 * [MutationForModuleAllAction] (below, in this same file) is the diff-free
 * sibling - port of `commands.ts`'s `proof.mutationForModuleAll`, sharing
 * [runMutationCore] with [DeepScanAction]'s own `runDeepScanCore` sibling
 * pattern (M6/M7). [MutationForFileAction] is the single-class sibling -
 * port of `commands.ts`'s `proof.mutationForFile`, the TS source's own
 * primary recommended entry point (cheapest to run, no diff needed) -
 * built in M7 part 4 once [activeFileClassTarget] existed.
 */
class MutationForModuleAction : AnAction("Proof: Mutation Testing (Module)") {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val choice = Messages.showYesNoDialog(
            project,
            "This run can take a while - potentially over an hour for a large module. The mutation timeout is not a total budget, it is an \"idle\" timeout: if this much time passes without a class finishing, the run stops; as long as classes keep finishing, it continues regardless of elapsed time. If it stops, results are still shown as partial.\n\n" +
                "For a single class instead, use \"Mutation Testing For This Class\".",
            "Mutation testing will run for the entire module",
            "Continue",
            "Cancel",
            Messages.getWarningIcon(),
        )
        if (choice != Messages.YES) return

        object : Task.Backgroundable(project, "Proof: mutation testing (module)", true) {
            // No target given: the CLI targets the changed production classes in the diff.
            override fun run(indicator: ProgressIndicator) = runMutation(project, indicator, targets = emptyList())
        }.queue()
    }
}

/**
 * Single-class sibling of [MutationForModuleAction]/[MutationForModuleAllAction] -
 * port of `commands.ts`'s `proof.mutationForFile`. No diff needed, no
 * confirmation dialog either (unlike its module-wide siblings) - a single
 * class is usually seconds, the same reasoning the TS source itself gives
 * for why this is its own recommended default.
 */
class MutationForFileAction : AnAction("Proof: Mutation Testing For This Class") {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val engine = requireEngine(project) ?: return
        val classTarget = activeFileClassTarget(project, engine)
        if (classTarget == null) {
            showErrorLater(project, "Proof: open a Java file to run mutation testing.")
            return
        }
        object : Task.Backgroundable(project, "Proof: mutation testing ${classTarget.fqcn}", true) {
            override fun run(indicator: ProgressIndicator) = runMutationForFile(project, indicator, classTarget)
        }.queue()
    }
}

/**
 * Diff-free sibling of [MutationForModuleAction] - port of `commands.ts`'s
 * `proof.mutationForModuleAll`. Needs a prior Quick Scan (the production
 * file list comes from its `fileCoverage` block), same requirement
 * [DeepScanWholeModuleAction] has.
 */
class MutationForModuleAllAction : AnAction("Proof: Mutation Testing (Module, No Diff)") {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
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

        val choice = Messages.showYesNoDialog(
            project,
            "This run can take a while - potentially over an hour for a large module. Results are still shown as partial if it stops early.",
            "Mutation testing will run for the entire module (no diff)",
            "Continue",
            "Cancel",
            Messages.getWarningIcon(),
        )
        if (choice != Messages.YES) return

        object : Task.Backgroundable(project, "Proof: mutation testing (module, no diff)", true) {
            override fun run(indicator: ProgressIndicator) = runMutationCore(project, indicator, modules, targets)
        }.queue()
    }
}

fun runMutationForFile(project: Project, indicator: ProgressIndicator, classTarget: ClassTarget) {
    val engine = requireEngine(project) ?: return
    val modules = resolveModulesOrShowError(project, engine.resolveReportBinding(project, DEFAULT_REPORT_PATH)) ?: return
    val targets = bindTargetsToModules(listOf(classTarget), modules)
    if (targets.isEmpty()) {
        showErrorLater(project, "Proof: could not determine which module ${classTarget.fqcn} belongs to.")
        return
    }
    runMutationCore(project, indicator, modules, targets)
}

private fun runMutation(project: Project, indicator: ProgressIndicator, targets: List<TargetBinding>) {
    val engine = requireEngine(project) ?: return
    if (targets.isEmpty() && ProofSettingsState.getInstance(project).toDiffMode() is DiffMode.NoVcs) {
        showErrorLater(project, "Proof: whole-module mutation requires a diff - there is no changed class to target while the diff mode is \"no-vcs\". For a single class, use \"Mutation Testing For This Class\".")
        return
    }
    val modules = resolveModulesOrShowError(project, engine.resolveReportBinding(project, DEFAULT_REPORT_PATH)) ?: return
    runMutationCore(project, indicator, modules, targets)
}

private fun runMutationCore(project: Project, indicator: ProgressIndicator, modules: List<ModuleBinding>, targets: List<TargetBinding>) {
    val engine = requireEngine(project) ?: return
    val cli = requireCli(project, engine) ?: return
    val repo = project.basePath ?: return

    indicator.text = "resolving classpath list(s)"
    val evidence = engine.resolveEvidenceInputs(project, modules, EvidenceKind.MUTATION, indicator)
    val classpaths = when (evidence) {
        is EvidenceInputsResult.Unavailable -> {
            showErrorLater(project, "Proof: mutation testing can't run without a classpath list - the run was never started.")
            return
        }
        is EvidenceInputsResult.Resolved -> {
            if (evidence.missingModuleRoots.isNotEmpty()) {
                showWarningLater(project, "Proof: mutation evidence won't be collected for these modules (classpath couldn't be generated): ${evidence.missingModuleRoots.joinToString(", ")}. Coverage will still be computed.")
            }
            evidence.classpaths
        }
    }
    if (classpaths.isEmpty()) {
        showErrorLater(project, "Proof: mutation testing can't run without a classpath list - the run was never started.")
        return
    }

    val mutation = EvidenceInput(
        classpaths = classpaths.map { ClasspathBinding(it.moduleId, it.path) },
        targets = targets,
        timeoutSeconds = DEFAULT_MUTATION_TIMEOUT_SECONDS,
    )
    val document = runAnalyzeCore(project, indicator, engine, cli, repo, perTest = null, mutation = mutation) ?: return

    val mutationBlock = document.mutation
    val targetFqcns = targets.map { it.fqcn }
    val ranAt = System.currentTimeMillis()
    // Best-effort, same as DeepScanAction's own pertest-current.json write
    // - a failed write does not fail the run itself.
    if (mutationBlock != null) {
        runCatching {
            proofStorageFile(repo, "mutation-current.json")
                .writeText(writeMutationSnapshotJson(MutationSnapshot(mutationBlock, document.warnings, targetFqcns, ranAt)))
        }
    }

    ApplicationManager.getApplication().invokeLater {
        val coverageState = coverageStateFrom(repo, document)
        CoverageStateService.getInstance(project).publish(coverageState)
        coverageState.fileCoverage?.let { applyGutterCoverage(project, repo, it) }

        MutationStateService.getInstance(project).publish(
            MutationState(mutation = mutationBlock, warnings = document.warnings, targets = targetFqcns, ranAt = ranAt),
        )
        if (mutationBlock == null) {
            showWarningLater(project, "Proof: no mutation evidence for this run - see the Warnings section in the Coverage view for MUTATION_* detail.")
        }
    }
}
