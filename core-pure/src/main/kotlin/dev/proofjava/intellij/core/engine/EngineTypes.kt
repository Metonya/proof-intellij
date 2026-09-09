package dev.proofjava.intellij.core.engine

import dev.proofjava.intellij.core.cli.TargetBinding

/**
 * Marker for whatever "kind of project" an [dev.proofjava.intellij.core.engine.Engine] detected -
 * deliberately opaque here. Java/Maven/Gradle are already engine-specific concepts (a
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
    /** A real multi-module discovery found one or more reports, each bound to a real module id (M5) - engines override [dev.proofjava.intellij.core.engine.Engine.resolveReportBinding] to produce this. */
    data class MultiModule(val modules: List<ModuleReportBinding>) : ReportBindingResult
    data object NotFound : ReportBindingResult
}

/** A discovered module the user can pick to scope a test run to - id + repo-relative root, nothing else (an [dev.proofjava.intellij.core.engine.Engine]'s own module-discovery result before any report exists). */
data class ModuleBinding(val id: String, val root: String)

sealed interface TestRunResult {
    /** The task ran to completion (Maven/Gradle itself decided success or failure) - [capturedOutput] is handed to [dev.proofjava.intellij.core.engine.Engine.interpretTestFailure] on failure. */
    data class Completed(val success: Boolean, val capturedOutput: String) : TestRunResult
    /** The task never ran at all (the user declined a warning dialog, or opened a file to fix by hand instead) - distinct from a real failure. */
    data object NotRun : TestRunResult
}

enum class EvidenceKind { PER_TEST, MUTATION }

/** One module's resolved classpath-list path, repo-relative - the shape `--per-test-classpath`/`--mutation-classpath` bindings need. */
data class EvidenceClasspath(val moduleId: String, val path: String)

sealed interface EvidenceInputsResult {
    /** [classpaths] is what was actually resolved (possibly a subset of the requested modules); [missingModuleRoots] names the rest so the caller can say evidence won't be collected for them, without failing the whole scan (L1 coverage stays complete either way). */
    data class Resolved(val classpaths: List<EvidenceClasspath>, val missingModuleRoots: List<String>) : EvidenceInputsResult
    /** Nothing could be resolved at all, or the user declined to generate what was missing - the caller must not proceed with L2/L3. */
    data object Unavailable : EvidenceInputsResult
}

/** One production class a "whole module, no diff" scan can target directly - [repoRelativePath] is the file it came from (used to bind it to a module via [bindTargetsToModules]), [fqcn] is what actually goes on the CLI's `--per-test-target`/`--mutation-target` flag. */
data class ClassTarget(val repoRelativePath: String, val fqcn: String)

/**
 * Which bound module a repo-relative path falls under - longest-root-
 * prefix wins (a nested module's own root must outrank its parent's),
 * `root == "."` is the lowest-priority fallback since it matches
 * everything. `null` means the path is not under any bound module's root
 * at all - callers must not guess (hard rule 3a). Port of
 * `cli/reportDiscovery.ts`'s `moduleForPath`, `core`-scoped (unlike
 * `engine-java`'s own near-identical copy over its own `ModuleRoot` type)
 * since binding a target to a module is not engine-specific.
 */
private fun moduleForPath(repoRelativePath: String, modules: List<ModuleBinding>): String? {
    var bestId: String? = null
    var bestPrefixLength = -1
    for (module in modules) {
        if (module.root == ".") {
            if (bestId == null) {
                bestId = module.id
                bestPrefixLength = 0
            }
            continue
        }
        val prefix = "${module.root}/"
        if (repoRelativePath.startsWith(prefix) && prefix.length > bestPrefixLength) {
            bestId = module.id
            bestPrefixLength = prefix.length
        }
    }
    return bestId
}

/** [targets] paired with the [ModuleBinding] each one's own file falls under - a target under no declared module root is dropped, not guessed. */
fun bindTargetsToModules(targets: List<ClassTarget>, modules: List<ModuleBinding>): List<TargetBinding> =
    targets.mapNotNull { target ->
        moduleForPath(target.repoRelativePath, modules)?.let { moduleId -> TargetBinding(moduleId, target.fqcn) }
    }
