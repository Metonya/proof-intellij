package dev.proofjava.intellij.engine.java.buildtool

import dev.proofjava.intellij.core.engine.ProjectKind
import java.io.File

enum class JavaProjectKind : ProjectKind { MAVEN, GRADLE }

/**
 * Port of `proof-vscode/src/ui/preflight.ts`'s `detectRunTestsBuildTool` -
 * same priority the CLI's own `DoctorCommand.discoverModules` uses: a root
 * `pom.xml` means Maven; otherwise a committed Gradle wrapper means Gradle;
 * neither means there is nothing this engine can run.
 */
fun detectProjectKind(projectRoot: String): JavaProjectKind? {
    if (File(projectRoot, "pom.xml").exists()) {
        return JavaProjectKind.MAVEN
    }
    if (resolveGradleWrapper(projectRoot) != null) {
        return JavaProjectKind.GRADLE
    }
    return null
}

/**
 * Port of `proof-vscode/src/ui/gradleTestTask.ts`'s `resolveGradleWrapper` -
 * only ever the project's own committed wrapper script, never a bare
 * `gradle` resolved from PATH (the wrapper pins the exact Gradle version
 * the project was actually built with).
 */
fun resolveGradleWrapper(projectRoot: String): String? {
    val scriptName = if (isWindows()) "gradlew.bat" else "gradlew"
    val script = File(projectRoot, scriptName)
    return if (script.exists()) script.path else null
}

private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win")
