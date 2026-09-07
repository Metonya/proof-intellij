package dev.proofjava.intellij.core.util

import java.io.File

/**
 * A repo-relative path joined onto [projectRoot] and normalized to
 * forward slashes - the convention `com.intellij.openapi.vfs.VirtualFile.getPath()`
 * always uses, on every OS. `java.io.File`'s own `.path` uses the
 * platform separator instead, so comparing/keying by a raw `File(...).path`
 * against a real `VirtualFile.path` silently never matches on Windows - a
 * real bug a live `runIde` run caught (the coverage gutter never painted a
 * single line on this machine because of exactly this mismatch). Still
 * built through `java.io.File` for correct path-joining semantics, just
 * normalized on the way out.
 */
fun absoluteVfsPath(projectRoot: String, repoRelativePath: String): String =
    File(projectRoot, repoRelativePath).path.replace(File.separatorChar, '/')
