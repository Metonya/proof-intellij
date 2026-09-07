package dev.proofjava.intellij.engine.java.discovery

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Kotlin port of `proof-vscode/src/test/unit/cli/reportDiscovery.test.ts`, test-by-test. */
class ModuleDiscoveryTest {

    @Test
    fun `a report at the workspace root needs no module binding`() {
        val module = describeModuleForReport("target/site/jacoco/jacoco.xml")
        assertEquals("root", module.id)
        assertEquals(".", module.root)
    }

    @Test
    fun `a report under a Maven module subdirectory is bound to that module root`() {
        assertEquals("gson", describeModuleForReport("gson/target/site/jacoco/jacoco.xml").root)
    }

    @Test
    fun `nested module paths keep every segment in the root`() {
        assertEquals("modules/service-a", describeModuleForReport("modules/service-a/target/site/jacoco/jacoco.xml").root)
    }

    @Test
    fun `a report at an unfamiliar layout falls back to the repo root rather than guessing`() {
        assertEquals(".", describeModuleForReport("some/other/coverage/jacoco.xml").root)
    }

    @Test
    fun `a Gradle jacocoTestReport at the workspace root needs no module binding`() {
        val module = describeModuleForReport("build/reports/jacoco/test/jacocoTestReport.xml")
        assertEquals("root", module.id)
        assertEquals(".", module.root)
    }

    @Test
    fun `a Gradle jacocoTestReport under a subproject directory is bound to that subproject root`() {
        assertEquals("core", describeModuleForReport("core/build/reports/jacoco/test/jacocoTestReport.xml").root)
    }

    @Test
    fun `nested Gradle subproject paths keep every segment in the root`() {
        assertEquals("modules/service-a", describeModuleForReport("modules/service-a/build/reports/jacoco/test/jacocoTestReport.xml").root)
    }

    @Test
    fun `a Maven report and a Gradle report never collide on the same repo root binding`() {
        assertEquals("gson", describeModuleForReport("gson/target/site/jacoco/jacoco.xml").root)
        assertEquals("gson", describeModuleForReport("gson/build/reports/jacoco/test/jacocoTestReport.xml").root)
    }

    @Test
    fun `toRepoRelativePosix strips the repo root and normalizes backslashes`() {
        assertEquals("gson/target/site/jacoco/jacoco.xml", toRepoRelativePosix("""C:\repo\gson\target\site\jacoco\jacoco.xml""", """C:\repo"""))
    }

    @Test
    fun `toRepoRelativePosix on an already-relative-looking mismatch returns the normalized input unchanged`() {
        assertEquals("/elsewhere/jacoco.xml", toRepoRelativePosix("/elsewhere/jacoco.xml", """C:\repo"""))
    }

    @Test
    fun `isProjectRoot is true when a pom-xml marker is present`() {
        assertTrue(isProjectRoot(listOf("pom.xml")))
    }

    @Test
    fun `isProjectRoot is true for any recognized Gradle marker too`() {
        assertTrue(isProjectRoot(listOf("settings.gradle.kts")))
    }

    @Test
    fun `isProjectRoot is false with no markers at all`() {
        assertFalse(isProjectRoot(emptyList()))
    }

    @Test
    fun `describeSiblingProjects names every candidate and never picks for them`() {
        val message = describeSiblingProjects(listOf("assertj", "dropwizard", "gson", "junit-framework"))
        assertTrue(message.contains("assertj, dropwizard, gson, junit-framework"))
        assertTrue(message.contains("4"))
    }

    @Test
    fun `bindModules gives each discovered report a real id derived from its module root`() {
        val bound = bindModules(listOf("gson/target/site/jacoco/jacoco.xml", "extras/target/site/jacoco/jacoco.xml", "test-jpms/target/site/jacoco/jacoco.xml"))
        assertEquals(listOf("gson", "extras", "test-jpms"), bound.map { it.id })
        assertEquals(listOf("gson", "extras", "test-jpms"), bound.map { it.root })
    }

    @Test
    fun `bindModules a report at the workspace root gets id root`() {
        val bound = bindModules(listOf("target/site/jacoco/jacoco.xml"))
        assertEquals(listOf(DiscoveredModule("root", ".", "target/site/jacoco/jacoco.xml")), bound)
    }

    @Test
    fun `bindModules a real id collision gets a numeric suffix, never silently merges`() {
        val bound = bindModules(listOf("backend/util/target/site/jacoco/jacoco.xml", "frontend/util/target/site/jacoco/jacoco.xml"))
        assertEquals(listOf("util", "util-2"), bound.map { it.id })
        assertEquals(2, bound.map { it.id }.toSet().size)
    }

    @Test
    fun `bindModules unsafe characters in a module root are sanitized to a valid CLI token`() {
        val bound = bindModules(listOf("my module!/target/site/jacoco/jacoco.xml"))
        assertEquals("my-module-", bound[0].id)
    }

    @Test
    fun `describeModuleForPom a root pom-xml is module root dot`() {
        assertEquals(".", describeModuleForPom("pom.xml"))
    }

    @Test
    fun `describeModuleForPom a submodule pom-xml names its own directory as root`() {
        assertEquals("test-jpms", describeModuleForPom("test-jpms/pom.xml"))
        assertEquals("modules/service-a", describeModuleForPom("modules/service-a/pom.xml"))
    }

    @Test
    fun `discoverModuleRootsFromPoms gives each discovered pom a real id derived from its module root`() {
        val modules = discoverModuleRootsFromPoms(listOf("pom.xml", "gson/pom.xml", "test-jpms/pom.xml"))
        assertEquals(listOf(ModuleRoot("root", "."), ModuleRoot("gson", "gson"), ModuleRoot("test-jpms", "test-jpms")), modules)
    }

    @Test
    fun `discoverModuleRootsFromPoms a real id collision gets a numeric suffix, same rule as bindModules`() {
        val modules = discoverModuleRootsFromPoms(listOf("backend/util/pom.xml", "frontend/util/pom.xml"))
        assertEquals(listOf("util", "util-2"), modules.map { it.id })
    }

    @Test
    fun `moduleForPath a file under a module root resolves to that module`() {
        val modules = listOf(ModuleRoot("gson", "gson"), ModuleRoot("extras", "extras"))
        assertEquals("gson", moduleForPath("gson/src/test/java/com/google/gson/GsonTest.java", modules))
        assertEquals("extras", moduleForPath("extras/src/test/java/com/google/gson/extras/ExtraTest.java", modules))
    }

    @Test
    fun `moduleForPath a nested module's own root outranks its parent's`() {
        val modules = listOf(ModuleRoot("root", "."), ModuleRoot("nested", "gson/nested"))
        assertEquals("nested", moduleForPath("gson/nested/src/main/java/Foo.java", modules))
        assertEquals("root", moduleForPath("gson/src/main/java/Bar.java", modules))
    }

    @Test
    fun `moduleForPath a path under no bound module's root is null, not a guess`() {
        val modules = listOf(ModuleRoot("gson", "gson"))
        assertNull(moduleForPath("extras/src/main/java/Foo.java", modules))
    }

    @Test
    fun `kts include calls become module roots`() {
        val modules = discoverModuleRootsFromSettingsGradle("rootProject.name = \"demo\"\ninclude(\":core\", \":extras\")\n")
        assertEquals(listOf(ModuleRoot("core", "core"), ModuleRoot("extras", "extras")), modules)
    }

    @Test
    fun `Groovy include without parentheses parses the same way`() {
        val modules = discoverModuleRootsFromSettingsGradle("include ':gson', ':gson:extras'\n")
        assertEquals(listOf(ModuleRoot("gson", "gson"), ModuleRoot("extras", "gson/extras")), modules)
    }

    @Test
    fun `an include-prefixed wrapper function is matched too`() {
        val text = listOf(
            "fun includeProject(name: String, mavenized: Boolean = false) {",
            "\tinclude(name)",
            "}",
            "includeProject(\"junit-jupiter\", mavenized = true)",
            "includeProject(\"junit-platform-commons\")",
        ).joinToString("\n")
        assertEquals(listOf("junit-jupiter", "junit-platform-commons"), discoverModuleRootsFromSettingsGradle(text).map { it.root })
    }

    @Test
    fun `includeBuild is never a subproject`() {
        assertEquals(emptyList<ModuleRoot>(), discoverModuleRootsFromSettingsGradle("includeBuild(\"gradle/plugins\")\n"))
    }

    @Test
    fun `includeFlat is skipped rather than reported at a wrong root`() {
        assertEquals(emptyList<ModuleRoot>(), discoverModuleRootsFromSettingsGradle("includeFlat(\"sibling\")\n"))
    }

    @Test
    fun `a wrapper that merely starts with an excluded word is still matched`() {
        assertEquals(listOf("core"), discoverModuleRootsFromSettingsGradle("includeFlattenedModules(\":core\")\n").map { it.root })
    }

    @Test
    fun `the same project declared twice is one module`() {
        assertEquals(listOf("core"), discoverModuleRootsFromSettingsGradle("include(\":core\")\ninclude(\":core\")\n").map { it.root })
    }

    @Test
    fun `a path escaping the repo root is not followed`() {
        assertEquals(emptyList<ModuleRoot>(), discoverModuleRootsFromSettingsGradle("include(\":..:outside\")\n"))
    }

    @Test
    fun `CRLF line endings parse identically`() {
        assertEquals(listOf("core", "extras"), discoverModuleRootsFromSettingsGradle("include(\":core\")\r\ninclude(\":extras\")\r\n").map { it.root })
    }

    @Test
    fun `a settings file declaring nothing yields no modules`() {
        assertEquals(emptyList<ModuleRoot>(), discoverModuleRootsFromSettingsGradle("rootProject.name = \"demo\"\n"))
    }

    @Test
    fun `the root project is added only when the caller says so`() {
        val modules = discoverModuleRootsFromSettingsGradle("include(\":core\")\n", includeRootProject = true)
        assertEquals(listOf(ModuleRoot("root", "."), ModuleRoot("core", "core")), modules)
    }

    @Test
    fun `a subproject literally named root collides safely instead of shadowing the root project`() {
        val modules = discoverModuleRootsFromSettingsGradle("include(\":root\")\n", includeRootProject = true)
        assertEquals(listOf(ModuleRoot("root", "."), ModuleRoot("root-2", "root")), modules)
    }

    @Test
    fun `only real call sites with literal strings contribute`() {
        val text = listOf("fun includeProject(name: String) { include(name) }", "include(\":a\", \":b:c\")").joinToString("\n")
        assertEquals(listOf(":a", ":b:c"), parseSettingsGradleProjectPaths(text))
    }

    @Test
    fun `repository content filters are not projects`() {
        val text = listOf(
            "pluginManagement {",
            "  repositories {",
            "    google {",
            "      content {",
            "        includeGroupByRegex(\"com\\\\.android.*\")",
            "        includeModule(\"com.example\", \"lib\")",
            "        includeVersionByRegex(\"com.example\", \"lib\", \"1\\\\..*\")",
            "      }",
            "    }",
            "  }",
            "}",
            "include(\":app\")",
        ).joinToString("\n")
        assertEquals(listOf(ModuleRoot("app", "app")), discoverModuleRootsFromSettingsGradle(text))
    }

    @Test
    fun `a value that cannot name a directory is skipped, not offered as a module`() {
        val modules = discoverModuleRootsFromSettingsGradle("include(\"glob*pattern\")\ninclude(\":real\")\n")
        assertEquals(listOf("real"), modules.map { it.root })
    }
}
