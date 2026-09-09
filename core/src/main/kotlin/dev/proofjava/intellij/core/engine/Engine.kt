package dev.proofjava.intellij.core.engine

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import dev.proofjava.intellij.core.model.CoverageState
import java.io.File

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
 *
 * The pure supporting types ([ProjectKind], [CliLocation], [ModuleBinding],
 * [TestRunResult], [ClassTarget], [bindTargetsToModules], ...) live in
 * `:core-pure`'s own `EngineTypes.kt` - only this interface itself needs
 * the IntelliJ Platform SDK (`Project`/`ProgressIndicator`).
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

    /**
     * Resolves the classpath list(s) L2/L3 evidence collection needs, one
     * per module in [modules] - generating missing ones (typically via this
     * engine's own `doctor --fix`-equivalent) is this method's job, not the
     * caller's. Runs on the caller's own background thread, same contract
     * as [runTests]; may show a confirmation dialog before generating
     * anything (a real build-tool invocation, not a silent side effect).
     */
    fun resolveEvidenceInputs(project: Project, modules: List<ModuleBinding>, kind: EvidenceKind, indicator: ProgressIndicator): EvidenceInputsResult

    /**
     * Every production class this engine can name from [state]'s last
     * scan, [ClassTarget.repoRelativePath] repo-relative - the "whole
     * module, no diff" Deep Scan/Mutation entry point's target list (port
     * of `ui/commands.ts`'s `allProductionTargets`). Naming a class from a
     * file path is engine-specific (a future Python engine's own module-
     * to-FQCN convention would differ), unlike binding a target to the
     * module that owns it ([bindTargetsToModules], `core`-side - a path-
     * prefix match is not engine-specific). Empty when [state] has no
     * `fileCoverage` block yet - the caller must have run Quick Scan
     * first, same requirement `commands.ts`'s own `runAnalyzePerTestAll`
     * has.
     */
    fun productionClassTargets(state: CoverageState): List<ClassTarget>

    /** Whether [fileName] (a bare name, e.g. `"Calculator.java"`) is a source file this engine understands at all - the "open a Java file to run this" gate the single-file Deep Scan/Mutation actions need (M7 part 4), by extension since no PSI/file-type dependency exists in this plugin. */
    fun ownsFile(fileName: String): Boolean

    /** The FQCN this engine would detect for a source file's own text - pure and engine-specific (a future Python engine's own module-naming convention would differ), the single-file Deep Scan/Mutation actions' target. Same text-based detection `hover/HoverContent.kt`'s className lookup already uses, exposed through the `Engine` interface now that a `core`-side caller needs it too. */
    fun classNameFor(fileText: String, fileBaseNameWithoutExtension: String): String
}
