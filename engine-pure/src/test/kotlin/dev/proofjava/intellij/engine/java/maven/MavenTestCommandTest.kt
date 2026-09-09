package dev.proofjava.intellij.engine.java.maven

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Kotlin port of `proof-vscode/src/test/unit/cli/mavenTestCommand.test.ts`. */
class MavenTestCommandTest {

    @Test
    fun `-B always present, phase always present`() {
        assertEquals(listOf("-B", "test"), buildMavenTestArgs(MavenTestArgsInput(MavenTestPhase.TEST, injectJacocoGoals = false, jacocoPluginVersion = "0.8.13")))
    }

    @Test
    fun `verify phase is honored, never guessed at as test`() {
        assertEquals(listOf("-B", "verify"), buildMavenTestArgs(MavenTestArgsInput(MavenTestPhase.VERIFY, injectJacocoGoals = false, jacocoPluginVersion = "0.8.13")))
    }

    @Test
    fun `injectJacocoGoals wraps the phase with prepare-agent and report, full coordinates`() {
        val args = buildMavenTestArgs(MavenTestArgsInput(MavenTestPhase.TEST, injectJacocoGoals = true, jacocoPluginVersion = "0.8.13"))
        assertEquals(listOf("-B", "org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent", "test", "org.jacoco:jacoco-maven-plugin:0.8.13:report"), args)
    }

    @Test
    fun `the jacoco plugin version is exactly what was passed, not a hardcoded default`() {
        val args = buildMavenTestArgs(MavenTestArgsInput(MavenTestPhase.TEST, injectJacocoGoals = true, jacocoPluginVersion = "0.8.99"))
        assertTrue(args.contains("org.jacoco:jacoco-maven-plugin:0.8.99:prepare-agent"))
        assertTrue(args.contains("org.jacoco:jacoco-maven-plugin:0.8.99:report"))
    }

    @Test
    fun `no -pl or -am - the whole reactor builds, matching a user's own manual mvn test`() {
        val args = buildMavenTestArgs(MavenTestArgsInput(MavenTestPhase.VERIFY, injectJacocoGoals = true, jacocoPluginVersion = "0.8.13"))
        assertFalse(args.contains("-pl"))
        assertFalse(args.contains("-am"))
    }

    @Test
    fun `moduleRoots omitted or empty - still the whole reactor, the honest first-run default`() {
        assertFalse(buildMavenTestArgs(MavenTestArgsInput(MavenTestPhase.TEST, injectJacocoGoals = false, jacocoPluginVersion = "0.8.13")).contains("-pl"))
        assertFalse(buildMavenTestArgs(MavenTestArgsInput(MavenTestPhase.TEST, injectJacocoGoals = false, jacocoPluginVersion = "0.8.13", moduleRoots = emptyList())).contains("-pl"))
    }

    @Test
    fun `moduleRoots scopes the build via -pl roots -am, before the phase`() {
        val args = buildMavenTestArgs(MavenTestArgsInput(MavenTestPhase.TEST, injectJacocoGoals = false, jacocoPluginVersion = "0.8.13", moduleRoots = listOf("gson", "extras")))
        assertEquals(listOf("-B", "-pl", "gson,extras", "-am", "test"), args)
    }

    @Test
    fun `moduleRoots and injectJacocoGoals compose - -pl -am still comes before the injected goals`() {
        val args = buildMavenTestArgs(MavenTestArgsInput(MavenTestPhase.TEST, injectJacocoGoals = true, jacocoPluginVersion = "0.8.13", moduleRoots = listOf("gson")))
        assertEquals(
            listOf("-B", "-pl", "gson", "-am", "org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent", "test", "org.jacoco:jacoco-maven-plugin:0.8.13:report"),
            args,
        )
    }
}
