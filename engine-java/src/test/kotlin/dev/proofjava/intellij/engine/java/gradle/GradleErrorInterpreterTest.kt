package dev.proofjava.intellij.engine.java.gradle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Kotlin port of `proof-vscode/src/test/unit/cli/gradleErrorInterpreter.test.ts`.
 * Fixtures are the same literal captured output the TS source's own tests
 * use (Gradle 9.7.1 on real junit-framework/Now in Android runs).
 */
class GradleErrorInterpreterTest {

    private val realTaskNotFoundOutput = "\nFAILURE: Build failed with an exception.\n\n" +
        "* What went wrong:\n" +
        "Selection failed\n" +
        "  Cannot locate tasks that match ':junit-bom:test' as task 'test' not found in project ':junit-bom'.\n\n" +
        "* Try:\n" +
        "> Run gradlew tasks to get a list of available tasks.\n" +
        "> Run with --stacktrace option to get the stack trace.\n\n" +
        "BUILD FAILED in 7s\n"

    @Test
    fun `the real task-not-found output names the module and what to do about it`() {
        val interpretation = interpretGradleFailure(realTaskNotFoundOutput)
        assertEquals(GradleFailureKind.TASK_NOT_FOUND_IN_PROJECT, interpretation?.kind)
        assertTrue(interpretation!!.detail.contains("junit-bom"))
        assertTrue(interpretation.detail.contains("no `test` task"))
        assertTrue(interpretation.detail.contains("Uncheck it in the module picker"))
    }

    /**
     * The advice that used to be given for a missing coverage task -
     * "apply the jacoco plugin" - was measurably wrong on Android: Now in
     * Android already applies jacoco, and AGP still names its coverage
     * tasks per variant, so no `jacocoTestReport` will ever exist there.
     */
    @Test
    fun `a missing coverage task names the real causes, not no tests to run`() {
        val interpretation = interpretGradleFailure(
            "Cannot locate tasks that match ':core:common:jacocoTestReport' as task 'jacocoTestReport' not found in project ':core:common'.",
        )
        assertEquals(GradleFailureKind.TASK_NOT_FOUND_IN_PROJECT, interpretation?.kind)
        assertTrue(interpretation!!.detail.contains("core:common"))
        assertTrue(interpretation.detail.contains("Android"))
        assertFalse(interpretation.detail.contains("no tests to run"))
    }

    @Test
    fun `the same clause is recognized when it starts the sentence`() {
        val interpretation = interpretGradleFailure("Task 'test' not found in project ':core'.")
        assertEquals(GradleFailureKind.TASK_NOT_FOUND_IN_PROJECT, interpretation?.kind)
        assertTrue(interpretation!!.detail.contains("core"))
    }

    @Test
    fun `an unrelated Gradle failure returns null rather than a guessed cause`() {
        assertNull(interpretGradleFailure("> Task :core:test FAILED\n\n3 tests completed, 1 failed"))
        assertNull(interpretGradleFailure(""))
    }

    private val realSdkMissingOutput = "\nFAILURE: Build failed with an exception.\n\n" +
        "* What went wrong:\n" +
        "Could not determine the dependencies of task ':core:data:testDemoDebugUnitTest'.\n" +
        "> SDK location not found. Define a valid SDK location with an ANDROID_HOME environment variable or by setting the sdk.dir path in your project's local properties file at '/repo/local.properties'.\n\n" +
        "BUILD FAILED in 2s\n"

    @Test
    fun `a missing Android SDK is named as such, and as not being caused by Proof`() {
        val interpretation = interpretGradleFailure(realSdkMissingOutput)
        assertEquals(GradleFailureKind.ANDROID_SDK_MISSING, interpretation?.kind)
        assertTrue(interpretation!!.detail.contains("ANDROID_HOME"))
        assertTrue(interpretation.detail.contains("local.properties"))
    }

    @Test
    fun `a task-not-found failure is still reported as such, not as an SDK problem`() {
        assertEquals(GradleFailureKind.TASK_NOT_FOUND_IN_PROJECT, interpretGradleFailure(realTaskNotFoundOutput)?.kind)
    }
}
