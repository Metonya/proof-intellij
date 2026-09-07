package dev.proofjava.intellij.engine.java.source

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.File

/** Kotlin port of `proof-vscode/src/test/unit/model/pathIndex.test.ts`. */
class PathIndexTest {

    private val projectRoot = File("C:", "repo").path

    @Test
    fun `toAbsolutePath joins a repo-relative forward-slash path onto the project root`() {
        val result = toAbsolutePath(projectRoot, "src/main/java/com/example/Calc.java")
        val expected = File(File(File(File(File(File(projectRoot, "src"), "main"), "java"), "com"), "example"), "Calc.java").path
        assertEquals(expected, result)
    }

    @Test
    fun `toRepoRelativePath is the inverse of toAbsolutePath`() {
        val absolute = toAbsolutePath(projectRoot, "src/main/java/Calc.java")
        assertEquals("src/main/java/Calc.java", toRepoRelativePath(projectRoot, absolute))
    }

    @Test
    fun `a path outside the project root is null, not a guessed relative path`() {
        val outside = File(File("C:", "elsewhere"), "Calc.java").path
        assertNull(toRepoRelativePath(projectRoot, outside))
    }

    @Test
    fun `fqcnToRootRelativePath joins a root and dotted FQCN into a repo-relative java path`() {
        assertEquals("src/test/java/dev/proofjava/playground/CalcTest.java", fqcnToRootRelativePath("src/test/java", "dev.proofjava.playground.CalcTest"))
    }

    @Test
    fun `fqcnToRootRelativePath tolerates a root with a trailing slash`() {
        assertEquals("src/test/java/dev/example/CalcTest.java", fqcnToRootRelativePath("src/test/java/", "dev.example.CalcTest"))
    }

    @Test
    fun `classNameFromPath is the inverse of fqcnToRootRelativePath for a path under one of the given sourceRoots`() {
        val path = fqcnToRootRelativePath("src/main/java", "dev.proofjava.playground.Calculator")
        assertEquals("dev.proofjava.playground.Calculator", classNameFromPath(path, listOf("src/main/java")))
    }

    @Test
    fun `classNameFromPath returns null for a path under none of the given sourceRoots (never guesses)`() {
        assertNull(classNameFromPath("other/Calc.java", listOf("src/main/java")))
    }

    @Test
    fun `classNameFromPath returns null for a non-java path`() {
        assertNull(classNameFromPath("src/main/java/Calc.txt", listOf("src/main/java")))
    }

    private val singleModule = listOf(SourceModuleRoots(sourceRoots = listOf("src/main/java"), testRoots = listOf("src/test/java")))

    @Test
    fun `classifySourcePath calls a file under testRoots a test`() {
        assertEquals(SourceKind.TEST, classifySourcePath("src/test/java/dev/proofjava/playground/CalcTest.java", singleModule))
    }

    @Test
    fun `classifySourcePath calls a file under sourceRoots production`() {
        assertEquals(SourceKind.PRODUCTION, classifySourcePath("src/main/java/dev/proofjava/playground/Calculator.java", singleModule))
    }

    @Test
    fun `classifySourcePath returns unknown for a file under no declared root - never a silent production`() {
        assertEquals(SourceKind.UNKNOWN, classifySourcePath("tools/Generate.java", singleModule))
    }

    @Test
    fun `classifySourcePath returns unknown when there are no modules at all`() {
        assertEquals(SourceKind.UNKNOWN, classifySourcePath("src/test/java/CalcTest.java", emptyList()))
    }

    @Test
    fun `classifySourcePath prefers the more specific testRoot when a sourceRoot is its ancestor`() {
        val nested = listOf(SourceModuleRoots(sourceRoots = listOf("src"), testRoots = listOf("src/test/java")))
        assertEquals(SourceKind.TEST, classifySourcePath("src/test/java/CalcTest.java", nested))
        assertEquals(SourceKind.PRODUCTION, classifySourcePath("src/main/java/Calc.java", nested))
    }

    @Test
    fun `classifySourcePath searches every declared module, not just the first`() {
        val multi = listOf(
            SourceModuleRoots(sourceRoots = listOf("core/src/main/java"), testRoots = listOf("core/src/test/java")),
            SourceModuleRoots(sourceRoots = listOf("api/src/main/java"), testRoots = listOf("api/src/test/java")),
        )
        assertEquals(SourceKind.TEST, classifySourcePath("api/src/test/java/ApiTest.java", multi))
        assertEquals(SourceKind.PRODUCTION, classifySourcePath("api/src/main/java/Api.java", multi))
    }

    @Test
    fun `classifySourcePath does not treat a sibling directory with a shared prefix as being under the root`() {
        assertEquals(SourceKind.UNKNOWN, classifySourcePath("src/test/javafx/Thing.java", singleModule))
    }
}
