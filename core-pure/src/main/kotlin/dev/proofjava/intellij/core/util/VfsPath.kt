package dev.proofjava.intellij.core.util

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

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

/**
 * Port of `proof-vscode/src/model/pathIndex.ts`'s repo-relative <-> absolute
 * path math - `core`-scoped (moved here from `engine-java.source.PathIndex`,
 * same names, when a `core`-side caller needed it, M7 part 4) since none of
 * it is actually Java-specific: no FQCN, no `.java` suffix, just filesystem
 * path arithmetic. [toAbsolutePath] keeps the OS-native separator (matches
 * `java.io.File`/`LocalFileSystem.findFileByPath`, both of which resolve
 * either separator correctly on Windows too, verified against real
 * IntelliJ Community source, M6's hover work) - use [absoluteVfsPath]
 * instead when the result must equal a real `VirtualFile.path`.
 */
fun toAbsolutePath(projectRoot: String, repoRelativePath: String): String {
    var result = File(projectRoot)
    for (segment in repoRelativePath.split("/")) {
        result = File(result, segment)
    }
    return result.path
}

/** `null` when [absolutePath] is outside [projectRoot] (or on an unrelated filesystem root, e.g. a different Windows drive letter). */
fun toRepoRelativePath(projectRoot: String, absolutePath: String): String? {
    val rootPath: Path = Paths.get(projectRoot).normalize()
    val targetPath: Path = Paths.get(absolutePath).normalize()
    val relative = try {
        rootPath.relativize(targetPath)
    } catch (e: IllegalArgumentException) {
        return null // different roots entirely (e.g. different drive letters)
    }
    val relativeString = relative.toString()
    if (relativeString.startsWith("..")) {
        return null // outside the project root
    }
    return relativeString.replace(File.separatorChar, '/')
}
