package dev.proofjava.intellij.engine.java.maven

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import dev.proofjava.intellij.core.cli.RunOptions
import dev.proofjava.intellij.core.cli.run
import dev.proofjava.intellij.core.engine.TestRunResult
import dev.proofjava.intellij.engine.java.discovery.findPomFiles
import java.io.File

/**
 * Port of `proof-vscode/src/ui/mavenTestTask.ts`: drives Maven to
 * (re)produce a JaCoCo report. Deliberate scope simplification from the TS
 * source, disclosed: `mavenTestTask.ts` runs inside a **visible terminal**
 * (a `vscode.Task`/`Pseudoterminal`) so the user sees Maven's own output
 * live; this port runs under the caller's `Task.Backgroundable` progress
 * indicator instead (no console/terminal tool window exists yet in this
 * plugin) - captured output is still fully available afterward (shown on
 * failure via [dev.proofjava.intellij.core.engine.Engine.interpretTestFailure]
 * or the raw text), just not streamed live to a visible pane. A real,
 * disclosed gap for a later milestone, not an oversight.
 */

private fun mavenExecutable(): String = if (System.getProperty("os.name").lowercase().contains("win")) "mvn.cmd" else "mvn"

private data class ReactorPomFacts(val hasJacocoPlugin: Boolean, val literalArgLine: LiteralArgLineLocation?)

private data class LiteralArgLineLocation(val file: String, val line: Int, val text: String)

private fun scanPoms(projectRoot: String): ReactorPomFacts {
    val perPomFacts = findPomFiles(projectRoot).mapNotNull { relativePath ->
        val xml = try {
            File(projectRoot, relativePath).readText()
        } catch (e: Exception) {
            return@mapNotNull null
        }
        relativePath to inspectPom(xml)
    }
    val hasJacocoPlugin = perPomFacts.any { (_, facts) -> facts.hasJacocoPlugin }
    val literalArgLine = perPomFacts.firstNotNullOfOrNull { (relativePath, facts) ->
        facts.literalArgLine?.let { LiteralArgLineLocation(relativePath, it.line, it.text) }
    }
    return ReactorPomFacts(hasJacocoPlugin, literalArgLine)
}

/**
 * Runs on the caller's own thread (already off the EDT inside a
 * `Task.Backgroundable`) - the literal-argLine warning dialog is the one
 * exception, explicitly marshalled onto the EDT and waited for.
 */
fun runMavenTests(
    project: Project,
    projectRoot: String,
    moduleRoots: List<String>,
    phase: MavenTestPhase,
    jacocoPluginVersion: String,
    indicator: ProgressIndicator,
): TestRunResult {
    val facts = scanPoms(projectRoot)

    if (facts.literalArgLine != null) {
        val choice = showLiteralArgLineWarningOnEdt(project, facts.literalArgLine)
        if (choice == OPEN_POM) {
            openPomAtLine(project, projectRoot, facts.literalArgLine)
        }
        if (choice != RUN_ANYWAY) {
            return TestRunResult.NotRun
        }
    }

    val args = buildMavenTestArgs(MavenTestArgsInput(phase, injectJacocoGoals = !facts.hasJacocoPlugin, jacocoPluginVersion = jacocoPluginVersion, moduleRoots = moduleRoots))
    indicator.text = "mvn ${args.joinToString(" ")}"
    val handle = run(RunOptions(executable = mavenExecutable(), args = args, workDirectory = projectRoot))
    while (!handle.result.isDone) {
        if (indicator.isCanceled) {
            handle.cancel()
            return TestRunResult.NotRun
        }
        Thread.sleep(100)
    }
    val result = handle.result.get()
    return TestRunResult.Completed(success = result.exitCode == 0, capturedOutput = result.stdout + result.stderr)
}

private const val OPEN_POM = 0
private const val RUN_ANYWAY = 1

private fun openPomAtLine(project: Project, projectRoot: String, argLine: LiteralArgLineLocation) {
    ApplicationManager.getApplication().invokeLater {
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(File(projectRoot, argLine.file).path) ?: return@invokeLater
        OpenFileDescriptor(project, virtualFile, argLine.line - 1, 0).navigate(true)
    }
}

private fun showLiteralArgLineWarningOnEdt(project: Project, argLine: LiteralArgLineLocation): Int {
    val app = ApplicationManager.getApplication()
    var choice = -1
    app.invokeAndWait {
        choice = Messages.showDialog(
            project,
            "The surefire configuration in ${argLine.file} (line ${argLine.line}) writes <argLine> as a literal string.\n\n" +
                "When the JaCoCo agent is bound from the command line, this line drops the agent - no jacoco.exec is produced, no coverage data comes out. " +
                "This can't be fixed from the command line (-DargLine=... has no effect, because the pom's <configuration><argLine> always wins). " +
                "You need to edit the line in the pom to include @{argLine}, e.g.: <argLine>@{argLine} ${argLine.text.replace(Regex("</?argLine>"), "")}</argLine>",
            "Proof: literal <argLine> found",
            arrayOf("Open pom.xml", "Run Anyway", "Cancel"),
            2,
            Messages.getWarningIcon(),
        )
    }
    return choice
}
