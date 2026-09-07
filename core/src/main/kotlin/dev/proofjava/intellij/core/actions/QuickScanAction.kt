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
import dev.proofjava.intellij.core.cli.RunOptions
import dev.proofjava.intellij.core.cli.buildAnalyzeArgs
import dev.proofjava.intellij.core.cli.parseDoctorProgressLine
import dev.proofjava.intellij.core.cli.parseProgressLine
import dev.proofjava.intellij.core.cli.progressMessage
import dev.proofjava.intellij.core.cli.run as runCli
import dev.proofjava.intellij.core.engine.EngineRegistry
import dev.proofjava.intellij.core.engine.ReportBindingResult
import dev.proofjava.intellij.core.model.coverageStateFrom
import dev.proofjava.intellij.core.state.CoverageStateService
import dev.proofjava.intellij.core.ui.gutter.applyGutterCoverage
import dev.proofjava.intellij.core.verdict.AnalysisStatus
import dev.proofjava.intellij.core.verdict.ParseResult
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
 * Simplifications explicit to this milestone, not oversights:
 * - [DEFAULT_REPORT_PATH] is a hardcoded constant (matches proof-vscode's
 *   own `proof.reportPath` default) - no settings service/Configurable
 *   exists yet to make it configurable.
 * - Diff mode is always [DiffMode.Uncommitted] - the diff-mode setting is
 *   the same "no settings service yet" gap.
 * - Only the single-module report-binding fast path is reachable
 *   ([Engine.resolveReportBinding]'s default) - multi-module discovery is
 *   M5 work.
 */
private const val DEFAULT_REPORT_PATH = "target/site/jacoco/jacoco.xml"

class QuickScanAction : AnAction("Proof: Quick Scan") {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val engine = EngineRegistry.firstRegisteredEngine()
        if (engine == null) {
            Messages.showErrorDialog(project, "No engine is registered - this is a packaging bug, not a project problem.", "Proof")
            return
        }
        val cli = engine.locateCli(project)
        if (cli?.jarPath == null) {
            Messages.showErrorDialog(project, "proof-java.jar was not found (checked .proof-java/ and ~/.proof-java/). Place it there and try again.", "Proof")
            return
        }
        val binding = engine.resolveReportBinding(project, DEFAULT_REPORT_PATH)
        if (binding !is ReportBindingResult.SingleModule) {
            Messages.showErrorDialog(project, "No coverage report found at $DEFAULT_REPORT_PATH - run your tests with JaCoCo first.", "Proof")
            return
        }
        val repo = project.basePath ?: return
        val outFile = File(File(repo, ".proof"), "verdict-current.json").apply { parentFile.mkdirs() }
        val args = buildAnalyzeArgs(
            AnalyzeArgsInput(
                repo = repo,
                diffMode = DiffMode.Uncommitted,
                reportPath = binding.reportPath,
                outPath = outFile.path,
                fileCoverage = true,
            ),
        )

        object : Task.Backgroundable(project, "Proof: analyzing", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
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
                // cancellation is polled right here rather than on a second
                // watcher thread - no separate thread to leak if the task
                // finishes normally.
                while (!handle.result.isDone) {
                    if (indicator.isCanceled) {
                        handle.cancel()
                        return // user-initiated cancel - not a failure, say nothing
                    }
                    Thread.sleep(100)
                }
                val result = try {
                    handle.result.get()
                } catch (ex: Exception) {
                    showErrorLater(project, "Proof: couldn't run \"${cli.executable}\": ${ex.message}")
                    return
                }
                // exit 3 (incomplete) still writes a real document - read it
                // rather than treating it as failure (hard rule 3a).
                if (result.exitCode != 0 && result.exitCode != 3) {
                    showErrorLater(project, "Proof: analysis failed (exit code ${result.exitCode}).")
                    return
                }

                val raw = try {
                    outFile.readText()
                } catch (ex: Exception) {
                    showErrorLater(project, "Proof: could not read the verdict file: ${ex.message}")
                    return
                }
                val parsed = parseVerdict(raw)
                if (parsed !is ParseResult.Ok) {
                    showErrorLater(project, "Proof: could not parse the verdict file: ${(parsed as ParseResult.Error).message}")
                    return
                }

                ApplicationManager.getApplication().invokeLater {
                    val state = coverageStateFrom(repo, parsed.value)
                    CoverageStateService.getInstance(project).publish(state)
                    state.fileCoverage?.let { applyGutterCoverage(project, repo, it) }
                    if (parsed.value.analysis.status == AnalysisStatus.INCOMPLETE) {
                        Messages.showWarningDialog(project, "Analysis completed incompletely - see the Warnings section in the Coverage view.", "Proof")
                    }
                }
            }
        }.queue()
    }
}

private fun showErrorLater(project: Project, message: String) {
    ApplicationManager.getApplication().invokeLater { Messages.showErrorDialog(project, message, "Proof") }
}
