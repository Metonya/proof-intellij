package dev.proofjava.intellij.engine.java.maven

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Kotlin port of `proof-vscode/src/test/unit/cli/mavenErrorInterpreter.test.ts`.
 * The fixtures are the same real captured output the TS source's own tests
 * use (D-67's gson `test-jpms` failures) - not invented for this port.
 */
class MavenErrorInterpreterTest {

    private val gsonTestJpmsFailure = "\n[ERROR] Failed to execute goal org.apache.maven.plugins:maven-dependency-plugin:3.6.1:build-classpath (default-cli) on project test-jpms: " +
        "Could not resolve dependencies for project com.google.code.gson:test-jpms:jar:2.14.1-SNAPSHOT: The following artifacts could not be resolved: " +
        "com.google.code.gson:gson:jar:2.14.1-SNAPSHOT (absent): Could not find artifact com.google.code.gson:gson:jar:2.14.1-SNAPSHOT\n"

    @Test
    fun `unresolvedReactorSibling on gson's real test-jpms failure`() {
        val result = interpretMavenFailure(gsonTestJpmsFailure)
        assertEquals(MavenFailureKind.UNRESOLVED_REACTOR_SIBLING, result?.kind)
        assertTrue(result!!.detail.contains("com.google.code.gson:gson:jar:2.14.1-SNAPSHOT"))
        assertTrue(result.detail.contains("mvn install -DskipTests"))
    }

    @Test
    fun `Could not resolve dependencies alone (no artifact line) is not enough to diagnose`() {
        assertNull(interpretMavenFailure("[ERROR] Could not resolve dependencies for project com.example:foo:jar:1.0"))
    }

    @Test
    fun `noPluginPrefix`() {
        val result = interpretMavenFailure(
            "[ERROR] No plugin found for prefix 'jacoco' in the current project and in the plugin groups [org.apache.maven.plugins, org.codehaus.mojo] available from the repositories",
        )
        assertEquals(MavenFailureKind.NO_PLUGIN_PREFIX, result?.kind)
        assertTrue(result!!.detail.contains("jacoco"))
    }

    @Test
    fun `enforcerJdk quotes Maven's own sentence verbatim`() {
        val result = interpretMavenFailure(
            "[WARNING] Rule 0: org.apache.maven.enforcer.rules.version.RequireJavaVersion failed with message:\n" +
                "Detected JDK Version: 25.0.1 is not in the allowed range [17,22).",
        )
        assertEquals(MavenFailureKind.ENFORCER_JDK, result?.kind)
        assertTrue(result!!.detail.contains("Detected JDK Version: 25.0.1 is not in the allowed range [17,22)."))
    }

    private val gsonTestJpmsModuleNotFound = "\n[ERROR] COMPILATION ERROR :\n" +
        "[ERROR] /repo/gson/test-jpms/src/test/java/module-info.java:[19,22] module not found: com.google.gson\n" +
        "[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.15.0:testCompile (default-testCompile) on project test-jpms: Compilation failure\n"

    @Test
    fun `unresolvedJpmsModule on gson's real test-jpms module-info failure`() {
        val result = interpretMavenFailure(gsonTestJpmsModuleNotFound)
        assertEquals(MavenFailureKind.UNRESOLVED_JPMS_MODULE, result?.kind)
        assertTrue(result!!.detail.contains("com.google.gson"))
        assertTrue(result.detail.contains("package"))
    }

    @Test
    fun `an unrecognized failure returns null, never a guess`() {
        assertNull(interpretMavenFailure("[ERROR] Some completely different Maven failure nobody has seen before."))
    }

    @Test
    fun `empty output returns null`() {
        assertNull(interpretMavenFailure(""))
    }
}
