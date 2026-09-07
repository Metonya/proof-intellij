package dev.proofjava.intellij.engine.java.classpath

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import dev.proofjava.intellij.core.cli.DoctorOptions
import dev.proofjava.intellij.core.cli.runDoctor
import dev.proofjava.intellij.core.engine.CliLocation
import dev.proofjava.intellij.core.engine.EvidenceClasspath
import dev.proofjava.intellij.core.engine.EvidenceInputsResult
import dev.proofjava.intellij.core.engine.EvidenceKind
import dev.proofjava.intellij.core.engine.ModuleBinding
import dev.proofjava.intellij.engine.java.buildtool.JavaProjectKind
import dev.proofjava.intellij.engine.java.buildtool.detectProjectKind
import java.io.File

/**
 * Port of `proof-vscode/src/ui/preflight.ts`'s `resolveEvidenceClasspaths`/
 * `checkClasspaths`/`classpathRelPath`: resolves the classpath list(s) L2/L3
 * need, one per bound module, generating missing ones via a single
 * `doctor --fix` call (fixes every usable module in the reactor in one
 * pass) rather than a hand-rolled per-module `dependency:build-classpath`.
 *
 * Disclosed simplification from the TS source: no automatic
 * `mvn install -DskipTests` retry when `doctor --fix` fails on an
 * unresolved reactor sibling (D-67) - that retry-and-reattempt loop is a
 * real, valuable second layer the TS source has, deferred here rather than
 * rushed; today this just reports the failure (via
 * [dev.proofjava.intellij.engine.java.JavaEngine.interpretTestFailure])
 * and stops, same as any other `doctor --fix` failure.
 *
 * Also simplified: no `proof.perTestClasspathPath`-style single-module
 * escape hatch - no settings service exists yet (same gap disclosed
 * elsewhere in this milestone sequence).
 */

private data class ClasspathCheck(val classpaths: List<EvidenceClasspath>, val missingModuleRoots: List<String>)

/** `GradleClasspathFixer.java`'s own file names (`build/proof-*-classpath.txt`) mirror `ClasspathFixer.java`'s Maven ones (`target/proof-*-classpath.txt`) exactly except for the build-output directory. */
private fun classpathRelPath(root: String, kind: EvidenceKind, buildTool: JavaProjectKind): String {
    val dir = if (buildTool == JavaProjectKind.GRADLE) "build" else "target"
    val file = "$dir/proof-${if (kind == EvidenceKind.PER_TEST) "per-test" else "mutation"}-classpath.txt"
    return if (root == ".") file else "$root/$file"
}

private fun checkClasspaths(projectRoot: String, modules: List<ModuleBinding>, kind: EvidenceKind, buildTool: JavaProjectKind): ClasspathCheck {
    val classpaths = mutableListOf<EvidenceClasspath>()
    val missing = mutableListOf<String>()
    for (m in modules) {
        val relPath = classpathRelPath(m.root, kind, buildTool)
        if (File(projectRoot, relPath).exists()) {
            classpaths += EvidenceClasspath(m.id, relPath)
        } else {
            missing += m.root
        }
    }
    return ClasspathCheck(classpaths, missing)
}

fun resolveEvidenceInputs(
    project: Project,
    projectRoot: String,
    cli: CliLocation,
    modules: List<ModuleBinding>,
    kind: EvidenceKind,
    indicator: ProgressIndicator,
): EvidenceInputsResult {
    val buildTool = detectProjectKind(projectRoot) ?: JavaProjectKind.MAVEN
    var check = checkClasspaths(projectRoot, modules, kind, buildTool)
    if (check.missingModuleRoots.isEmpty()) {
        return EvidenceInputsResult.Resolved(check.classpaths, emptyList())
    }

    if (cli.jarPath == null) return EvidenceInputsResult.Unavailable
    if (!confirmGenerateOnEdt(project, check.missingModuleRoots, buildTool)) {
        return EvidenceInputsResult.Unavailable
    }

    indicator.text = "doctor --fix"
    val handle = runDoctor(cli, projectRoot, DoctorOptions(fix = true))
    while (!handle.result.isDone) {
        if (indicator.isCanceled) {
            handle.cancel()
            return EvidenceInputsResult.Unavailable
        }
        Thread.sleep(100)
    }
    handle.result.get() // exit code intentionally unchecked here - success is judged by what checkClasspaths finds afterward, same as the TS source

    check = checkClasspaths(projectRoot, modules, kind, buildTool)
    if (check.classpaths.isEmpty()) {
        return EvidenceInputsResult.Unavailable
    }
    return EvidenceInputsResult.Resolved(check.classpaths, check.missingModuleRoots)
}

private fun confirmGenerateOnEdt(project: Project, missingRoots: List<String>, buildTool: JavaProjectKind): Boolean {
    var confirmed = false
    ApplicationManager.getApplication().invokeAndWait {
        val toolLabel = if (buildTool == JavaProjectKind.GRADLE) "Gradle" else "Maven"
        confirmed = Messages.showYesNoDialog(
            project,
            "Deep Scan needs a classpath list (missing for ${missingRoots.size} module(s): ${missingRoots.joinToString(", ")}). Generate it with $toolLabel now?",
            "Proof",
            Messages.getQuestionIcon(),
        ) == Messages.YES
    }
    return confirmed
}
