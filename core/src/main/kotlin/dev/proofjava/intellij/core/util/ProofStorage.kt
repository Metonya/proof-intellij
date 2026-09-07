package dev.proofjava.intellij.core.util

import java.io.File

private const val STORAGE_DIR_NAME = ".proof"

/**
 * `repo/.proof/<fileName>`, creating the directory (and a self-ignoring
 * `.gitignore` inside it, once) if missing - port of
 * `proof-vscode/src/ui/commands.ts`'s `ensureStorageRoot`. `.proof/` is a
 * plain, repo-local directory (moved off VS Code's own hidden
 * `context.storageUri` on a real user request, Faz 33 there) - not
 * IntelliJ-specific, so a snapshot written by one editor is readable by
 * the other, which this project's own side-by-side VS Code/IntelliJ
 * testing relies on.
 */
fun proofStorageFile(repo: String, fileName: String): File {
    val dir = File(repo, STORAGE_DIR_NAME)
    dir.mkdirs()
    val gitignore = File(dir, ".gitignore")
    if (!gitignore.exists()) {
        gitignore.writeText("*\n")
    }
    return File(dir, fileName)
}
