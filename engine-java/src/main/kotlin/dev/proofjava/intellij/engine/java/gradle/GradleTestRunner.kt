package dev.proofjava.intellij.engine.java.gradle

import com.intellij.openapi.progress.ProgressIndicator
import dev.proofjava.intellij.core.cli.RunOptions
import dev.proofjava.intellij.core.cli.run
import dev.proofjava.intellij.core.engine.TestRunResult
import dev.proofjava.intellij.engine.java.buildtool.resolveGradleWrapper

/**
 * Port of `proof-vscode/src/ui/gradleTestTask.ts`. Only ever runs a
 * project's own committed wrapper (`gradlew`/`gradlew.bat`), never a bare
 * `gradle` on PATH - the wrapper pins the exact Gradle version the project
 * was actually built with. Same visible-terminal simplification as
 * [dev.proofjava.intellij.engine.java.maven.runMavenTests] - captured
 * output only, no live console yet.
 */
fun runGradleTests(projectRoot: String, moduleRoots: List<String>, coverageTask: String?, indicator: ProgressIndicator): TestRunResult {
    val wrapper = resolveGradleWrapper(projectRoot) ?: return TestRunResult.NotRun

    val unsafe = unsafeModuleRoots(moduleRoots)
    if (unsafe.isNotEmpty()) {
        indicator.text = "scoping dropped - unsafe module root(s): ${unsafe.joinToString(", ")}"
    }

    val args = buildGradleTestArgs(GradleTestArgsInput(moduleRoots = moduleRoots, coverageTask = coverageTask))
    indicator.text = "gradlew ${args.joinToString(" ")}"
    val handle = run(RunOptions(executable = wrapper, args = args, workDirectory = projectRoot))
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
