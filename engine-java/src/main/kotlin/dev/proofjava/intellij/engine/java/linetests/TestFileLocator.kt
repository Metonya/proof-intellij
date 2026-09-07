package dev.proofjava.intellij.engine.java.linetests

import dev.proofjava.intellij.engine.java.source.fqcnToRootRelativePath
import dev.proofjava.intellij.core.util.toAbsolutePath
import java.io.File

/**
 * Port of `proof-vscode/src/ui/testFileLocator.ts`: resolve a test class's
 * own source file. Prefers a `Finding`'s own `path` (CLI-verified, exact)
 * when the caller already has one; otherwise tries each declared test root
 * and keeps the first candidate that actually exists on disk. Returns
 * `null` rather than guessing (hard rule 3a) - a link that might open the
 * wrong file is worse than no link at all. Synchronous (unlike the TS
 * source's `Promise`) - every caller here already runs on a background
 * thread (`Task.Backgroundable`).
 */
fun locateTestFile(projectRoot: String, testRoots: List<String>, outerClassName: String, findingPath: String?): String? {
    if (findingPath != null) return findingPath
    for (testRoot in testRoots) {
        val candidate = fqcnToRootRelativePath(testRoot, outerClassName)
        if (File(toAbsolutePath(projectRoot, candidate)).exists()) {
            return candidate
        }
    }
    return null
}
