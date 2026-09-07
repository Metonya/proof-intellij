package dev.proofjava.intellij.core.engine

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Marker for whatever "kind of project" an [Engine] detected - deliberately
 * opaque here. Java/Maven/Gradle are already engine-specific concepts (a
 * future Python engine would have poetry/pip/hatch instead), so `core`
 * must never know the concrete values - only the engine that produced one
 * interprets it (`engine-java`'s own `JavaProjectKind`).
 */
interface ProjectKind

/** Where to run the CLI from - `executable` is `"java"` for proof-java today, but a future engine's own binary need not go through a JVM at all, hence the separate [jarPath]. */
data class CliLocation(val executable: String, val jarPath: String? = null)

data class ModuleReportBinding(val id: String, val root: String, val reportPath: String)

sealed interface ReportBindingResult {
    /** The configured (or default) report already exists exactly where expected - no discovery needed. */
    data class SingleModule(val reportPath: String) : ReportBindingResult
    /** A real multi-module discovery found one or more reports, each bound to a real module id (M5) - engines override [Engine.resolveReportBinding] to produce this. */
    data class MultiModule(val modules: List<ModuleReportBinding>) : ReportBindingResult
    data object NotFound : ReportBindingResult
}

/** A discovered module the user can pick to scope a test run to - id + repo-relative root, nothing else (an [Engine]'s own module-discovery result before any report exists). */
data class ModuleBinding(val id: String, val root: String)

sealed interface TestRunResult {
    /** The task ran to completion (Maven/Gradle itself decided success or failure) - [capturedOutput] is handed to [Engine.interpretTestFailure] on failure. */
    data class Completed(val success: Boolean, val capturedOutput: String) : TestRunResult
    /** The task never ran at all (the user declined a warning dialog, or opened a file to fix by hand instead) - distinct from a real failure. */
    data object NotRun : TestRunResult
}

/**
 * The extension point a language/build-tool backend implements to plug into
 * proof-intellij's language-agnostic `core` - `JavaEngine` (`:engine-java`)
 * today, a future `proof-python` engine later without `core` needing to
 * change. Grounded directly in what `proof-vscode`'s `ui/preflight.ts`/
 * `ui/mavenTestTask.ts`/`ui/gradleTestTask.ts` already do today - every
 * method here corresponds to something that codebase actually implements,
 * not a speculative surface. Grows incrementally as later milestones need
 * more (module discovery, running tests, evidence-classpath resolution) -
 * adding those now, before there's a concrete caller, would be exactly the
 * kind of ahead-of-need work this plan avoids elsewhere.
 */
interface Engine {
    val id: String
    val displayName: String

    /** Search order and what counts as "found" are entirely engine-specific (a jar for Java, a venv-installed binary for Python, ...). */
    fun locateCli(project: Project): CliLocation?

    /** `null` when none of this engine's build tool(s) are present in [project] at all. */
    fun detectProjectKind(project: Project): ProjectKind?

    /**
     * Where to read coverage evidence from. The single-module fast path -
     * does [configuredReportPath] already exist at the project root? - is
     * entirely build-tool-agnostic (mirrors `ui/preflight.ts`'s
     * `resolveReportBinding`, whose own single-module branch has zero
     * Maven/Gradle-specific logic), so it is the default here. An engine
     * overrides this once it implements module discovery (the multi-module
     * glob fallback - a later milestone).
     */
    fun resolveReportBinding(project: Project, configuredReportPath: String): ReportBindingResult {
        val root = project.basePath ?: return ReportBindingResult.NotFound
        return if (File(root, configuredReportPath).exists()) {
            ReportBindingResult.SingleModule(configuredReportPath)
        } else {
            ReportBindingResult.NotFound
        }
    }

    /** Every module this engine can find in [project], before any report or test run - the source a "Run Tests" module-scope picker lists. Empty when the build tool has nothing to discover (or none is present at all). */
    fun discoverModules(project: Project): List<ModuleBinding>

    /**
     * Drives this engine's build tool to (re)produce coverage evidence,
     * scoped to [moduleRoots] (empty means the whole project/reactor - the
     * only option on a first-ever run, before anything is known). Runs
     * synchronously on the calling thread - callers already run this
     * inside their own `Task.Backgroundable`, mirroring [dev.proofjava.intellij.core.cli.run]'s
     * own "the caller owns the background thread" contract.
     */
    fun runTests(project: Project, moduleRoots: List<String>, indicator: ProgressIndicator): TestRunResult

    /** Turns raw build-tool output into an actionable sentence, or `null` when the failure shape isn't recognized (hard rule 3a - never a guess). */
    fun interpretTestFailure(rawOutput: String): String?
}
