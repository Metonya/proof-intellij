package dev.proofjava.intellij.engine.java

import com.intellij.openapi.project.Project
import dev.proofjava.intellij.core.engine.CliLocation
import dev.proofjava.intellij.core.engine.Engine
import dev.proofjava.intellij.core.engine.ProjectKind
import dev.proofjava.intellij.engine.java.buildtool.detectProjectKind
import dev.proofjava.intellij.engine.java.locator.locateJar

/**
 * The `Engine` implementation for proof-java (Maven/Gradle/JVM projects).
 * Registered via the `dev.proofjava.intellij.engine` extension point in
 * `plugin.xml` - `core` never references this class directly, only the
 * `Engine` interface it implements.
 *
 * M2 scope only: CLI location and build-tool detection. `resolveReportBinding`
 * is inherited unchanged from `Engine`'s own single-module-fast-path default
 * - multi-module glob discovery is a later milestone.
 */
class JavaEngine : Engine {
    override val id: String = "java"
    override val displayName: String = "Java (proof-java)"

    override fun locateCli(project: Project): CliLocation? {
        val root = project.basePath ?: return null
        val jarPath = locateJar(root) ?: return null
        return CliLocation(executable = "java", jarPath = jarPath)
    }

    override fun detectProjectKind(project: Project): ProjectKind? {
        val root = project.basePath ?: return null
        return detectProjectKind(root)
    }
}
