package dev.proofjava.intellij.core.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import dev.proofjava.intellij.core.cli.AnalyzeArgsInput
import dev.proofjava.intellij.core.cli.DiffMode
import dev.proofjava.intellij.core.cli.EvidenceInput
import dev.proofjava.intellij.core.cli.ModuleReportBinding
import dev.proofjava.intellij.core.cli.RunOptions
import dev.proofjava.intellij.core.cli.buildAnalyzeArgs
import dev.proofjava.intellij.core.cli.parseDoctorProgressLine
import dev.proofjava.intellij.core.cli.parseProgressLine
import dev.proofjava.intellij.core.cli.progressMessage
import dev.proofjava.intellij.core.cli.run as runCli
import dev.proofjava.intellij.core.engine.CliLocation
import dev.proofjava.intellij.core.engine.Engine
import dev.proofjava.intellij.core.engine.EngineRegistry
import dev.proofjava.intellij.core.engine.ReportBindingResult
import dev.proofjava.intellij.core.model.coverageStateFrom
import dev.proofjava.intellij.core.state.CoverageStateService
import dev.proofjava.intellij.core.ui.gutter.applyGutterCoverage
import dev.proofjava.intellij.core.verdict.AnalysisStatus
import dev.proofjava.intellij.core.verdict.ParseResult
import dev.proofjava.intellij.core.verdict.VerdictDocument
import dev.proofjava.intellij.core.verdict.parseVerdict
import java.io.File

/**
 * "Quick Scan" - port of `proof-vscode`'s `proof.analyze` command
 * (`ui/commands.ts`'s `runAnalyzeCore`, the no-`--per-test-report`/
 * no-`--mutation-report` shape). The first vertical slice proving the
 * `core`/`engine-java` boundary works end to end: [EngineRegistry] (core)
 * ->  `Engine.locateCli`/`resolveReportBinding` (implemented by
 * `engine-java.JavaEngine`, reached only through the `Engine` interface) ->
 * [buildAnalyzeArgs]/[runCli] (core) -> [parseVerdict] (core) -> gutter +
 * Coverage tool window (core). Not one line here imports `engine-java`
 * directly - if it ever needs to, the boundary this milestone exists to
 * prove has failed.
 *
 * [runQuickScan] and [runAnalyzeCore] are plain functions, not just the
 * action body, so [RunTestsAction] can trigger the same scan after a
 * successful test run, and [DeepScanAction] (M6) can reuse the exact same
 * report-binding/run/parse pipeline with `--per-test-report` added,
 * without either synthesizing a fake [AnActionEvent] (same reasoning as
 * [toggleCoverage]'s own split).
 *
 * Simplifications explicit to this milestone, not oversights:
 * - [DEFAULT_REPORT_PATH] is a hardcoded constant (matches proof-vscode's
 *   own `proof.reportPath` default) - no settings service/Configurable
 *   exists yet to make it configurable.
 * - Diff mode is always [DiffMode.Uncommitted] - the diff-mode setting is
 *   the same "no settings service yet" gap.
 */
const val DEFAULT_REPORT_PATH = "target/site/jacoco/jacoco.xml"

class QuickScanAction : AnAction("Proof: Quick Scan") {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        object : Task.Backgroundable(project, "Proof: analyzing", true) {
            override fun run(indicator: ProgressIndicator) = runQuickScan(project, indicator)
        }.queue()
    }
}

/** Runs on the caller's own background thread - callers already own a `Task.Backgroundable`, same contract as [dev.proofjava.intellij.core.engine.Engine.runTests]. */
fun runQuickScan(project: Project, indicator: ProgressIndicator) {
    val engine = requireEngine(project) ?: return
    val cli = requireCli(project, engine) ?: return
    val repo = project.basePath ?: return
    val document = runAnalyzeCore(project, indicator, engine, cli, repo, perTest = null) ?: return
    publishCoverage(project, repo, document)
}

/**
 * The shared pipeline: resolve where to read the coverage report from,
 * build `analyze` argv (with [perTest] evidence flags when given), run,
 * parse the result. Returns `null` on any failure - the failure has
 * already been shown to the user. Publishing the result into
 * [CoverageStateService]/[dev.proofjava.intellij.core.state.PerTestStateService]
 * is deliberately the caller's job, not this function's - Quick Scan and
 * Deep Scan publish different things from the same document.
 */
internal fun runAnalyzeCore(
    project: Project,
    indicator: ProgressIndicator,
    engine: Engine,
    cli: CliLocation,
    repo: String,
    perTest: EvidenceInput?,
): VerdictDocument? {
    val binding = engine.resolveReportBinding(project, DEFAULT_REPORT_PATH)
    val (reportPath, modules) = when (binding) {
        is ReportBindingResult.SingleModule -> binding.reportPath to emptyList<ModuleReportBinding>()
        is ReportBindingResult.MultiModule -> null to binding.modules.map { ModuleReportBinding(it.id, it.root, it.reportPath) }
        is ReportBindingResult.NotFound -> {
            showErrorLater(project, "No coverage report found at $DEFAULT_REPORT_PATH - run your tests with JaCoCo first (Proof: Run Tests).")
            return null
        }
    }

    indicator.isIndeterminate = true
    val outFile = File(File(repo, ".proof"), "verdict-current.json").apply { parentFile.mkdirs() }
    val args = buildAnalyzeArgs(
        AnalyzeArgsInput(
            repo = repo,
            diffMode = DiffMode.Uncommitted,
            reportPath = reportPath,
            modules = modules,
            outPath = outFile.path,
            fileCoverage = true,
            perTest = perTest,
        ),
    )

    val handle = runCli(
        RunOptions(
            executable = cli.executable,
            jarPath = cli.jarPath,
            args = args,
            workDirectory = repo,
            onStderrLine = { line ->
                parseProgressLine(line)?.let { indicator.text = progressMessage(it) }
                    ?: parseDoctorProgressLine(line)?.let { indicator.text = "doctor: fixing '${it.moduleId}'" }
            },
        ),
    )
    // Task.Backgroundable's own thread is already off the EDT, so
    // cancellation is polled right here rather than on a second watcher
    // thread - no separate thread to leak if the task finishes normally.
    while (!handle.result.isDone) {
        if (indicator.isCanceled) {
            handle.cancel()
            return null // user-initiated cancel - not a failure, say nothing
        }
        Thread.sleep(100)
    }
    val result = try {
        handle.result.get()
    } catch (ex: Exception) {
        showErrorLater(project, "Proof: couldn't run \"${cli.executable}\": ${ex.message}")
        return null
    }
    // exit 3 (incomplete) still writes a real document - read it rather
    // than treating it as failure (hard rule 3a).
    if (result.exitCode != 0 && result.exitCode != 3) {
        showErrorLater(project, "Proof: analysis failed (exit code ${result.exitCode}).")
        return null
    }

    val raw = try {
        outFile.readText()
    } catch (ex: Exception) {
        showErrorLater(project, "Proof: could not read the verdict file: ${ex.message}")
        return null
    }
    val parsed = parseVerdict(raw)
    if (parsed !is ParseResult.Ok) {
        showErrorLater(project, "Proof: could not parse the verdict file: ${(parsed as ParseResult.Error).message}")
        return null
    }
    if (parsed.value.analysis.status == AnalysisStatus.INCOMPLETE) {
        val document = parsed.value
        ApplicationManager.getApplication().invokeLater {
            Messages.showWarningDialog(project, "Analysis completed incompletely - see the Warnings section in the Coverage view.", "Proof")
        }
        return document
    }
    return parsed.value
}

private fun publishCoverage(project: Project, repo: String, document: VerdictDocument) {
    ApplicationManager.getApplication().invokeLater {
        val state = coverageStateFrom(repo, document)
        CoverageStateService.getInstance(project).publish(state)
        state.fileCoverage?.let { applyGutterCoverage(project, repo, it) }
    }
}

internal fun requireEngine(project: Project): Engine? {
    val engine = EngineRegistry.firstRegisteredEngine()
    if (engine == null) {
        showErrorLater(project, "No engine is registered - this is a packaging bug, not a project problem.")
    }
    return engine
}

internal fun requireCli(project: Project, engine: Engine): CliLocation? {
    val cli = engine.locateCli(project)
    if (cli?.jarPath == null) {
        showErrorLater(project, "proof-java.jar was not found (checked .proof-java/ and ~/.proof-java/). Place it there and try again.")
        return null
    }
    return cli
}

internal fun showErrorLater(project: Project, message: String) {
    ApplicationManager.getApplication().invokeLater { Messages.showErrorDialog(project, message, "Proof") }
}
