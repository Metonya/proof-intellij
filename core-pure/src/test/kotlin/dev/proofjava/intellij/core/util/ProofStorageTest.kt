package dev.proofjava.intellij.core.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ProofStorageTest {

    @Test
    fun `creates the storage directory and a self-ignoring gitignore on first use`(@TempDir tempDir: File) {
        val file = proofStorageFile(tempDir.path, "verdict-current.json")

        assertEquals("verdict-current.json", file.name)
        assertTrue(File(tempDir, ".proof").isDirectory)
        assertEquals("*\n", File(tempDir, ".proof/.gitignore").readText())
    }

    @Test
    fun `an existing gitignore is left alone`(@TempDir tempDir: File) {
        val proofDir = File(tempDir, ".proof").apply { mkdirs() }
        File(proofDir, ".gitignore").writeText("custom\n")

        proofStorageFile(tempDir.path, "pertest-current.json")

        assertEquals("custom\n", File(proofDir, ".gitignore").readText())
    }
}
