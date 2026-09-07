package dev.proofjava.intellij.engine.java.source

import dev.proofjava.intellij.core.model.CoverageState

/**
 * Port of `proof-vscode/src/model/pathIndex.ts`'s Java-source-specific
 * pieces (FQCN <-> path, test-vs-production classification) - the
 * `.java`/dot-to-slash convention a future engine's own source layout
 * would not share. The repo-relative <-> absolute filesystem path math
 * this file used to also carry moved to `core.util` (M7 part 4, same
 * names `toAbsolutePath`/`toRepoRelativePath`) once a `core`-side caller
 * needed it - none of that half was ever actually Java-specific.
 * proof-java's own paths are always forward-slash and repo-relative.
 */

/**
 * An outer FQCN (nested-class suffix already stripped by the caller) to a
 * repo-relative `.java` path under one `sourceRoot`/`testRoot` - mirrors
 * proof-java-cli's `ChangedClassTargets.forEachMappedFile` exactly
 * (dot-to-slash, `.java` suffix). Used to locate a test's own source file
 * when no `Finding` already carries its path - the caller tries each
 * declared root and keeps the first one that exists on disk.
 */
fun fqcnToRootRelativePath(root: String, fqcn: String): String {
    val prefix = if (root.endsWith("/")) root else "$root/"
    return "$prefix${fqcn.replace('.', '/')}.java"
}

/** The reverse of [fqcnToRootRelativePath]: a repo-relative `.java` path to its FQCN, given the module's `sourceRoots`. Used to build a `className -> path` index straight from `fileCoverage.files[]`. */
fun classNameFromPath(repoRelativePath: String, sourceRoots: List<String>): String? {
    if (!repoRelativePath.endsWith(".java")) {
        return null
    }
    for (sourceRoot in sourceRoots) {
        val prefix = if (sourceRoot.endsWith("/")) sourceRoot else "$sourceRoot/"
        if (repoRelativePath.startsWith(prefix)) {
            return repoRelativePath.substring(prefix.length, repoRelativePath.length - ".java".length).replace('/', '.')
        }
    }
    return null
}

/**
 * Whether a file is test source, production source, or neither - per the
 * CLI's own `inputs.modules[].testRoots`/`sourceRoots` declaration.
 * [SourceKind.UNKNOWN] is a real answer, not a polite fallback for
 * production (hard rule 3a): if there's no module declaration, or the file
 * is under no declared root at all, the caller must handle that knowingly.
 */
enum class SourceKind { TEST, PRODUCTION, UNKNOWN }

data class SourceModuleRoots(val sourceRoots: List<String> = emptyList(), val testRoots: List<String> = emptyList())

/** Bridges `core.model.CoverageState`'s generic `ModuleInput.sourceRoots`/`testRoots` to the [SourceModuleRoots] shape this file's own functions expect - shared by every `engine-java` caller that needs both, so it exists once, not once per caller. */
fun sourceModuleRoots(coverageState: CoverageState): List<SourceModuleRoots> =
    coverageState.modules.map { SourceModuleRoots(it.sourceRoots, it.testRoots) }

fun classifySourcePath(repoRelativePath: String, modules: List<SourceModuleRoots>): SourceKind {
    // testRoots checked first: if one root is declared as a subdirectory of
    // the other (e.g. sourceRoot `src`, testRoot `src/test/java`), the more
    // specific one must win.
    for (module in modules) {
        if (module.testRoots.any { isUnderRoot(repoRelativePath, it) }) {
            return SourceKind.TEST
        }
    }
    for (module in modules) {
        if (module.sourceRoots.any { isUnderRoot(repoRelativePath, it) }) {
            return SourceKind.PRODUCTION
        }
    }
    return SourceKind.UNKNOWN
}

private fun isUnderRoot(repoRelativePath: String, root: String): Boolean {
    val prefix = if (root.endsWith("/")) root else "$root/"
    return repoRelativePath.startsWith(prefix)
}
