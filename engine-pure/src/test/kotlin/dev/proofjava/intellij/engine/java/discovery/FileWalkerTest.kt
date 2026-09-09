package dev.proofjava.intellij.engine.java.discovery

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Regression test for a real bug a live `runIde` run against
 * `proof-java-playground` (a Gradle project) caught: [findReportFiles]
 * used to exclude `build/` from its walk, so a Gradle project's own
 * JaCoCo report (`build/reports/jacoco/test/jacocoTestReport.xml`) could
 * never be found, and Quick Scan always failed with "no coverage report
 * found" even after a real test run had produced one. `preflight.ts`'s
 * own `vscode.workspace.findFiles` call excludes only `node_modules` -
 * this file's exclusion set must not diverge from that.
 */
class FileWalkerTest {

    @Test
    fun `finds a Gradle jacocoTestReport inside build - the bug this test guards against`(@TempDir tempDir: File) {
        val report = File(tempDir, "build/reports/jacoco/test/jacocoTestReport.xml")
        report.parentFile.mkdirs()
        report.writeText("<report/>")

        val found = findReportFiles(tempDir.path, listOf("/target/site/jacoco/jacoco.xml", "/build/reports/jacoco/test/jacocoTestReport.xml"))

        assertEquals(listOf("build/reports/jacoco/test/jacocoTestReport.xml"), found)
    }

    @Test
    fun `finds a Maven jacoco report inside target`(@TempDir tempDir: File) {
        val report = File(tempDir, "target/site/jacoco/jacoco.xml")
        report.parentFile.mkdirs()
        report.writeText("<report/>")

        val found = findReportFiles(tempDir.path, listOf("/target/site/jacoco/jacoco.xml", "/build/reports/jacoco/test/jacocoTestReport.xml"))

        assertEquals(listOf("target/site/jacoco/jacoco.xml"), found)
    }

    @Test
    fun `finds a module's pom_xml even though a sibling module has a target directory`(@TempDir tempDir: File) {
        File(tempDir, "pom.xml").writeText("<project/>")
        val siblingPom = File(tempDir, "gson/pom.xml")
        siblingPom.parentFile.mkdirs()
        siblingPom.writeText("<project/>")
        File(tempDir, "gson/target").mkdirs() // a real build-output dir sitting right next to a real module pom.xml

        val found = findPomFiles(tempDir.path)

        assertEquals(setOf("pom.xml", "gson/pom.xml"), found.toSet())
    }

    @Test
    fun `never walks into node_modules`(@TempDir tempDir: File) {
        val decoy = File(tempDir, "node_modules/some-package/build/reports/jacoco/test/jacocoTestReport.xml")
        decoy.parentFile.mkdirs()
        decoy.writeText("<report/>")

        val found = findReportFiles(tempDir.path, listOf("/build/reports/jacoco/test/jacocoTestReport.xml"))

        assertTrue(found.isEmpty())
    }
}
