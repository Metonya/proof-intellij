package dev.proofjava.intellij.engine.java

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import dev.proofjava.intellij.core.engine.CliLocation
import dev.proofjava.intellij.core.engine.ClassTarget
import dev.proofjava.intellij.core.engine.Engine
import dev.proofjava.intellij.core.engine.EvidenceInputsResult
import dev.proofjava.intellij.core.engine.EvidenceKind
import dev.proofjava.intellij.core.engine.ModuleBinding
import dev.proofjava.intellij.core.engine.ModuleReportBinding
import dev.proofjava.intellij.core.engine.ProjectKind
import dev.proofjava.intellij.core.engine.ReportBindingResult
import dev.proofjava.intellij.core.engine.TestRunResult
import dev.proofjava.intellij.core.model.CoverageState
import dev.proofjava.intellij.core.settings.ProofSettingsState
import dev.proofjava.intellij.engine.java.buildtool.JavaProjectKind
import dev.proofjava.intellij.engine.java.buildtool.detectProjectKind
import dev.proofjava.intellij.engine.java.classpath.resolveEvidenceInputs
import dev.proofjava.intellij.engine.java.discovery.bindModules
import dev.proofjava.intellij.engine.java.discovery.discoverModuleRootsFromPoms
import dev.proofjava.intellij.engine.java.discovery.discoverModuleRootsFromSettingsGradle
import dev.proofjava.intellij.engine.java.discovery.findPomFiles
import dev.proofjava.intellij.engine.java.discovery.findReportFiles
import dev.proofjava.intellij.engine.java.discovery.hasTestSources
import dev.proofjava.intellij.engine.java.discovery.isProjectRoot
import dev.proofjava.intellij.engine.java.discovery.readGradleSettings
import dev.proofjava.intellij.engine.java.discovery.PROJECT_ROOT_MARKER_FILES
import dev.proofjava.intellij.engine.java.gradle.interpretGradleFailure
import dev.proofjava.intellij.engine.java.gradle.runGradleTests
import dev.proofjava.intellij.engine.java.locator.locateJar
import dev.proofjava.intellij.engine.java.locator.locateJavaExecutable
import dev.proofjava.intellij.engine.java.maven.MavenTestPhase
import dev.proofjava.intellij.engine.java.maven.interpretMavenFailure
import dev.proofjava.intellij.engine.java.maven.runMavenTests
import dev.proofjava.intellij.engine.java.source.classNameFromPath
import dev.proofjava.intellij.engine.java.source.detectClassName
import dev.proofjava.intellij.engine.java.source.productionSourceRoots
import dev.proofjava.intellij.engine.java.source.sourceModuleRoots
import java.io.File

/**
 * The `Engine` implementation for proof-java (Maven/Gradle/JVM projects).
 * Registered via the `dev.proofjava.intellij.engine` extension point in
 * `plugin.xml` - `core` never references this class directly, only the
 * `Engine` interface it implements.
 */
class JavaEngine : Engine {
    override val id: String = "java"
    override val displayName: String = "Java (proof-java)"

    override fun locateCli(project: Project): CliLocation? {
        val root = project.basePath ?: return null
        val settings = ProofSettingsState.getInstance(project)
        val jarPath = locateJar(root, settings.jarPath) ?: return null
        val executable = locateJavaExecutable(project, settings.javaExecutable)
        return CliLocation(executable = executable, jarPath = jarPath)
    }

    override fun detectProjectKind(project: Project): ProjectKind? {
        val root = project.basePath ?: return null
        return detectProjectKind(root)
    }

    /**
     * Overrides [Engine]'s single-module-only default with the real M5
     * multi-module glob discovery, port of `preflight.ts`'s
     * `resolveReportBinding`: past the single-module fast path, the
     * project root must itself look like a real project (a marker file
     * present) before any discovered report is trusted as belonging to it -
     * a folder of several unrelated repos side by side is not offered as
     * if picking among them were a real choice. Deliberately simplified
     * from the TS source: that distinction's own explanatory message
     * (`describeSiblingProjects`) is not surfaced here - callers just see
     * [ReportBindingResult.NotFound] either way, a real disclosed gap.
     */
    override fun resolveReportBinding(project: Project, configuredReportPath: String): ReportBindingResult {
        val root = project.basePath ?: return ReportBindingResult.NotFound
        if (File(root, configuredReportPath).exists()) {
            return ReportBindingResult.SingleModule(configuredReportPath)
        }
        val presentMarkers = PROJECT_ROOT_MARKER_FILES.filter { File(root, it).exists() }
        if (!isProjectRoot(presentMarkers)) {
            return ReportBindingResult.NotFound
        }
        val reportPaths = findReportFiles(root, KNOWN_REPORT_SUFFIXES_FOR_GLOB)
        if (reportPaths.isEmpty()) {
            return ReportBindingResult.NotFound
        }
        val bound = bindModules(reportPaths)
        return ReportBindingResult.MultiModule(bound.map { ModuleReportBinding(it.id, it.root, it.reportPath) })
    }

    override fun discoverModules(project: Project): List<ModuleBinding> {
        val root = project.basePath ?: return emptyList()
        val kind = detectProjectKind(root)
        val discovered = if (kind == JavaProjectKind.GRADLE) {
            val settingsText = readGradleSettings(root) ?: return emptyList()
            discoverModuleRootsFromSettingsGradle(settingsText, includeRootProject = hasTestSources(root, "."))
        } else {
            discoverModuleRootsFromPoms(findPomFiles(root))
        }
        return discovered.map { ModuleBinding(it.id, it.root) }
    }

    override fun runTests(project: Project, moduleRoots: List<String>, indicator: ProgressIndicator): TestRunResult {
        val root = project.basePath ?: return TestRunResult.NotRun
        return when (detectProjectKind(root)) {
            JavaProjectKind.MAVEN -> runMavenTests(project, root, moduleRoots, MavenTestPhase.TEST, jacocoPluginVersion = DEFAULT_JACOCO_PLUGIN_VERSION, indicator = indicator)
            JavaProjectKind.GRADLE -> runGradleTests(root, moduleRoots, coverageTask = null, indicator = indicator)
            null -> TestRunResult.NotRun
        }
    }

    override fun interpretTestFailure(rawOutput: String): String? =
        interpretMavenFailure(rawOutput)?.detail ?: interpretGradleFailure(rawOutput)?.detail

    override fun resolveEvidenceInputs(project: Project, modules: List<ModuleBinding>, kind: EvidenceKind, indicator: ProgressIndicator): EvidenceInputsResult {
        val root = project.basePath ?: return EvidenceInputsResult.Unavailable
        val cli = locateCli(project) ?: return EvidenceInputsResult.Unavailable
        return resolveEvidenceInputs(project, root, cli, modules, kind, indicator)
    }

    /** Port of `ui/commands.ts`'s `allProductionTargets`: every `fileCoverage.files[]` entry that names a real FQCN under a declared source root - the "whole module, no diff" Deep Scan entry point's target list. */
    override fun productionClassTargets(state: CoverageState): List<ClassTarget> {
        val fileCoverage = state.fileCoverage ?: return emptyList()
        val sourceRoots = productionSourceRoots(sourceModuleRoots(state))
        return fileCoverage.files.mapNotNull { file ->
            classNameFromPath(file.path, sourceRoots)?.let { fqcn -> ClassTarget(file.path, fqcn) }
        }
    }

    override fun ownsFile(fileName: String): Boolean = fileName.endsWith(".java")

    override fun classNameFor(fileText: String, fileBaseNameWithoutExtension: String): String =
        detectClassName(fileText, fileBaseNameWithoutExtension)

    companion object {
        /** Matches `preflight.ts`'s own default - no settings service exists yet to make this configurable (same gap `QuickScanAction`'s `DEFAULT_REPORT_PATH` already discloses). */
        private const val DEFAULT_JACOCO_PLUGIN_VERSION = "0.8.13"
        private val KNOWN_REPORT_SUFFIXES_FOR_GLOB = listOf("/target/site/jacoco/jacoco.xml", "/build/reports/jacoco/test/jacocoTestReport.xml")
    }
}
