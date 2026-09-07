package dev.proofjava.intellij.engine.java.locator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * [javaBinaryUnderSdkHome] only - the part of [locateJavaExecutable] that
 * doesn't need a real IntelliJ `Project`/`Sdk` (the `ProjectRootManager`
 * lookup itself, and the explicit-Settings-override short circuit ahead
 * of it, are thin platform glue, untested the same way `JarLocator.kt`'s
 * own `Project`-touching callers are).
 */
class JavaExecutableLocatorTest {

    @Test
    fun `a null sdk home means no candidate`() {
        assertNull(javaBinaryUnderSdkHome(null))
    }

    @Test
    fun `an sdk home with no bin java means no candidate, not a guess`(@TempDir tempDir: File) {
        assertNull(javaBinaryUnderSdkHome(tempDir.path))
    }

    @Test
    fun `finds the real java binary under an sdk home's bin directory`(@TempDir tempDir: File) {
        val binDir = File(tempDir, "bin").apply { mkdirs() }
        val binaryName = if (System.getProperty("os.name").lowercase().contains("win")) "java.exe" else "java"
        val javaFile = File(binDir, binaryName).apply { writeText("") }

        val found = javaBinaryUnderSdkHome(tempDir.path)

        assertEquals(javaFile.path, found)
    }
}
