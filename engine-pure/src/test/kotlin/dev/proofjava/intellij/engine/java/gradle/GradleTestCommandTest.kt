package dev.proofjava.intellij.engine.java.gradle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Kotlin port of `proof-vscode/src/test/unit/cli/gradleTestCommand.test.ts`. */
class GradleTestCommandTest {

    @Test
    fun `no scope at all keeps the historical whole-build argv`() {
        assertEquals(listOf("test", "jacocoTestReport"), buildGradleTestArgs(GradleTestArgsInput()))
        assertEquals(listOf("test", "jacocoTestReport"), buildGradleTestArgs(GradleTestArgsInput(moduleRoots = emptyList())))
    }

    @Test
    fun `the root project is scoped with a leading colon, never the bare task name`() {
        assertEquals(listOf(":test", ":jacocoTestReport"), buildGradleTestArgs(GradleTestArgsInput(moduleRoots = listOf("."))))
    }

    @Test
    fun `a subproject root becomes a qualified Gradle task path`() {
        assertEquals(listOf(":core:test", ":core:jacocoTestReport"), buildGradleTestArgs(GradleTestArgsInput(moduleRoots = listOf("core"))))
    }

    @Test
    fun `a nested subproject keeps every segment as a Gradle path separator`() {
        assertEquals(
            listOf(":modules:service-a:test", ":modules:service-a:jacocoTestReport"),
            buildGradleTestArgs(GradleTestArgsInput(moduleRoots = listOf("modules/service-a"))),
        )
    }

    @Test
    fun `several modules are requested in the order they were picked, tests before reports per module`() {
        assertEquals(
            listOf(":core:test", ":core:jacocoTestReport", ":extras:test", ":extras:jacocoTestReport"),
            buildGradleTestArgs(GradleTestArgsInput(moduleRoots = listOf("core", "extras"))),
        )
    }

    @Test
    fun `a shell-unsafe module root drops scoping entirely rather than being quoted or split`() {
        assertEquals(listOf("test", "jacocoTestReport"), buildGradleTestArgs(GradleTestArgsInput(moduleRoots = listOf("my module"))))
        assertEquals(listOf("test", "jacocoTestReport"), buildGradleTestArgs(GradleTestArgsInput(moduleRoots = listOf("core", "evil & rm"))))
    }

    @Test
    fun `isShellSafeModuleRoot accepts ordinary module paths and rejects shell metacharacters`() {
        assertTrue(isShellSafeModuleRoot("core"))
        assertTrue(isShellSafeModuleRoot("modules/service-a"))
        assertTrue(isShellSafeModuleRoot("."))
        assertFalse(isShellSafeModuleRoot("my module"))
        assertFalse(isShellSafeModuleRoot("a&b"))
        assertFalse(isShellSafeModuleRoot("a\"b"))
    }

    @Test
    fun `unsafeModuleRoots names exactly the roots that caused scoping to be dropped`() {
        assertEquals(listOf("my module", "a&b"), unsafeModuleRoots(listOf("core", "my module", "a&b")))
        assertEquals(emptyList<String>(), unsafeModuleRoots(listOf("core")))
        assertEquals(emptyList<String>(), unsafeModuleRoots(emptyList()))
    }

    @Test
    fun `the coverage task defaults to jacocoTestReport when unset, blank, or whitespace`() {
        assertEquals("jacocoTestReport", DEFAULT_COVERAGE_TASK)
        assertEquals(listOf(":core:test", ":core:jacocoTestReport"), buildGradleTestArgs(GradleTestArgsInput(moduleRoots = listOf("core"))))
        assertEquals(listOf(":core:test", ":core:jacocoTestReport"), buildGradleTestArgs(GradleTestArgsInput(moduleRoots = listOf("core"), coverageTask = "")))
        assertEquals(listOf(":core:test", ":core:jacocoTestReport"), buildGradleTestArgs(GradleTestArgsInput(moduleRoots = listOf("core"), coverageTask = "   ")))
    }

    @Test
    fun `a configured coverage task replaces jacocoTestReport in a scoped run`() {
        assertEquals(
            listOf(":core:data:test", ":core:data:createDemoDebugUnitTestCoverageReport"),
            buildGradleTestArgs(GradleTestArgsInput(moduleRoots = listOf("core/data"), coverageTask = "createDemoDebugUnitTestCoverageReport")),
        )
    }

    @Test
    fun `a configured coverage task is used by an unscoped run too`() {
        assertEquals(
            listOf("test", "createDemoDebugUnitTestCoverageReport"),
            buildGradleTestArgs(GradleTestArgsInput(coverageTask = "createDemoDebugUnitTestCoverageReport")),
        )
    }
}
