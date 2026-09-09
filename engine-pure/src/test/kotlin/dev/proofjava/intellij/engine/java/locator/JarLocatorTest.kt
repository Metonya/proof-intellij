package dev.proofjava.intellij.engine.java.locator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Real filesystem, no mocking - matches proof-vscode's own testing
 * discipline for `jarLocator.ts` (impure functions are exercised against
 * real temp directories, not fakes of `fs.existsSync`).
 */
class JarLocatorTest {

    // No "nothing found anywhere returns null" case here: the last default
    // candidate is the real `~/.proof-java/proof-java.jar` (`userJarPath()`),
    // not scoped to any test-controlled directory - this machine genuinely
    // has one installed (from real proof-vscode usage), so asserting
    // "nothing is found" would depend on uncontrolled real machine state.
    // proof-vscode's own `jarLocator.ts` has no unit test file for the same
    // reason - it is exercised through real usage, not isolated here.

    @Test
    fun `finds the dev-repo layout candidate first`(@TempDir tempDir: Path) {
        val jar = File(tempDir.toFile(), "proof-java-cli/target/proof-java.jar")
        jar.parentFile.mkdirs()
        jar.writeText("fake jar")
        assertEquals(jar.path, locateJar(tempDir.toString()))
    }

    @Test
    fun `falls back to the workspace-scope jar when the dev-repo path is absent`(@TempDir tempDir: Path) {
        val jar = File(workspaceJarPath(tempDir.toString()))
        jar.parentFile.mkdirs()
        jar.writeText("fake jar")
        assertEquals(jar.path, locateJar(tempDir.toString()))
    }

    @Test
    fun `an explicit configured relative path is resolved against the project root`(@TempDir tempDir: Path) {
        val jar = File(tempDir.toFile(), "lib/custom.jar")
        jar.parentFile.mkdirs()
        jar.writeText("fake jar")
        assertEquals(jar.path, locateJar(tempDir.toString(), configuredPath = "lib/custom.jar"))
    }

    @Test
    fun `an explicit configured path that does not exist yields null, no fallback to the default search`(@TempDir tempDir: Path) {
        val jar = File(tempDir.toFile(), "proof-java-cli/target/proof-java.jar")
        jar.parentFile.mkdirs()
        jar.writeText("this must not be found")
        assertNull(locateJar(tempDir.toString(), configuredPath = "nowhere.jar"))
    }

    @Test
    fun `an absolute configured path is used as-is`(@TempDir tempDir: Path) {
        val jar = File(tempDir.toFile(), "somewhere/proof-java.jar")
        jar.parentFile.mkdirs()
        jar.writeText("fake jar")
        assertEquals(jar.path, locateJar("/irrelevant/root", configuredPath = jar.absolutePath))
    }

    @Test
    fun `a blank configured path is treated as absent, falling through to the default search`(@TempDir tempDir: Path) {
        val jar = File(tempDir.toFile(), "proof-java-cli/target/proof-java.jar")
        jar.parentFile.mkdirs()
        jar.writeText("fake jar")
        assertEquals(jar.path, locateJar(tempDir.toString(), configuredPath = "   "))
    }
}
