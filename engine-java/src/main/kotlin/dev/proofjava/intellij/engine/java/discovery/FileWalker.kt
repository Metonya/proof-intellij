package dev.proofjava.intellij.engine.java.discovery

import java.io.File

/**
 * IntelliJ-Platform-free directory walk used by module/report discovery -
 * the counterpart to `vscode.workspace.findFiles`'s glob in the TS source.
 * Excludes the same kind of heavy/irrelevant directories a real glob
 * exclusion (a `node_modules` pattern) would skip, plus the build-output
 * directories a Maven/Gradle repo itself produces (walking into `target`/
 * `build` would find generated `pom.xml` copies and stale reports).
 */
private val EXCLUDED_DIR_NAMES = setOf(".git", "node_modules", "target", "build", ".gradle", ".idea", "out")

/** Every `pom.xml` under [root], repo-relative, forward-slash, capped at [limit] like the TS source's own glob call. */
fun findPomFiles(root: String, limit: Int = 200): List<String> = walk(root, "pom.xml", limit)

/** Every file under [root] whose path ends with one of [suffixes] (the known JaCoCo report locations), repo-relative, forward-slash. */
fun findReportFiles(root: String, suffixes: List<String>, limit: Int = 200): List<String> {
    val results = mutableListOf<String>()
    val rootFile = File(root)
    if (!rootFile.isDirectory) return results
    walkDirs(rootFile) { file ->
        if (results.size >= limit) return@walkDirs false
        val relative = file.path.replace('\\', '/').removePrefix(root.replace('\\', '/')).trimStart('/')
        if (suffixes.any { relative.endsWith(it.trimStart('/')) }) {
            results += relative
        }
        true
    }
    return results
}

private fun walk(root: String, fileName: String, limit: Int): List<String> {
    val results = mutableListOf<String>()
    val rootFile = File(root)
    if (!rootFile.isDirectory) return results
    walkDirs(rootFile) { file ->
        if (results.size >= limit) return@walkDirs false
        if (file.name == fileName) {
            results += file.path.replace('\\', '/').removePrefix(root.replace('\\', '/')).trimStart('/')
        }
        true
    }
    return results
}

/** Depth-first walk skipping [EXCLUDED_DIR_NAMES]; [visit] returns `false` to stop the whole walk early (limit reached) - the stop signal is propagated back up through every recursive level, not just the innermost one. */
private fun walkDirs(dir: File, visit: (File) -> Boolean): Boolean {
    val children = dir.listFiles() ?: return true
    for (child in children) {
        if (child.isDirectory) {
            if (child.name in EXCLUDED_DIR_NAMES || child.name.startsWith(".")) continue
            if (!walkDirs(child, visit)) return false
        } else {
            if (!visit(child)) return false
        }
    }
    return true
}
