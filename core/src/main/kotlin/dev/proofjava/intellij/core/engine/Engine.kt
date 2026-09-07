package dev.proofjava.intellij.core.engine

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

sealed interface ReportBindingResult {
    /** The configured (or default) report already exists exactly where expected - no discovery needed. */
    data class SingleModule(val reportPath: String) : ReportBindingResult
    data object NotFound : ReportBindingResult
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
}
