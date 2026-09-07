package dev.proofjava.intellij.engine.java.locator

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import java.io.File

/**
 * Which `java` binary launches `proof-java.jar` - port of `proof-vscode`'s
 * own `config.get<string>('javaExecutable') || 'java'`, extended with a
 * middle tier neither that nor a bare PATH lookup can offer: the
 * project's own configured JDK (Project Structure -> SDK). Real user
 * question (2026-09-07), after a live Deep Scan failed with
 * `PER_TEST_JDK_UNSUPPORTED` (the embedded PIT engine needs JDK 22 or
 * lower) even though the project's own configured SDK (17) would have
 * satisfied it - the CLI subprocess was inheriting a bare `"java"` from
 * whatever environment happened to launch the IDE process, with no
 * relation at all to what the project itself builds and tests with.
 *
 * Precedence: [configuredPath] (an explicit Settings override, for a
 * project whose own SDK does not satisfy `--per-test-report`'s ceiling
 * either) -> the IntelliJ Project SDK's own `java` binary, when one is
 * configured and its home actually contains one -> the bare `"java"` a
 * subprocess resolves from its own inherited PATH (this plugin's only
 * behavior until now, kept as the final fallback for a project with no
 * SDK configured at all).
 */
fun locateJavaExecutable(project: Project, configuredPath: String?): String {
    if (!configuredPath.isNullOrBlank()) {
        return configuredPath
    }
    val sdkHome = ProjectRootManager.getInstance(project).projectSdk?.homePath
    return javaBinaryUnderSdkHome(sdkHome) ?: "java"
}

/** Split out from [locateJavaExecutable] so the actual path-building logic is unit-testable without a real [Project]/`Sdk` - only the `ProjectRootManager` lookup above needs one. */
internal fun javaBinaryUnderSdkHome(sdkHome: String?): String? {
    if (sdkHome == null) return null
    val javaBinaryName = if (isWindows()) "java.exe" else "java"
    val candidate = File(File(sdkHome, "bin"), javaBinaryName)
    return if (candidate.isFile) candidate.path else null
}

private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win")
