package dev.proofjava.intellij.core.util

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regression test for a real bug a live `runIde` run caught: the gutter
 * never painted a single line on Windows, because the code compared a
 * `java.io.File(...).path` (backslash-separated on Windows) directly
 * against a real `VirtualFile.path` (always forward-slash) - they could
 * never be equal. [absoluteVfsPath] must never contain a backslash.
 */
class VfsPathTest {

    @Test
    fun `never contains a backslash, regardless of the host OS`() {
        val path = absoluteVfsPath("C:\\Users\\Mert\\repo", "src/main/java/Calculator.java")
        assertFalse(path.contains('\\'), "must be forward-slash-only to ever equal a real VirtualFile.path: $path")
        assertTrue(path.endsWith("src/main/java/Calculator.java"))
    }

    @Test
    fun `joins a forward-slash project root the same way`() {
        val path = absoluteVfsPath("/repo", "src/main/java/Calculator.java")
        assertTrue(path.endsWith("/repo/src/main/java/Calculator.java") || path == "/repo/src/main/java/Calculator.java")
    }
}
