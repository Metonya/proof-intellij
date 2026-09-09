package dev.proofjava.intellij.engine.java.discovery

import java.io.File

/** Port of `preflight.ts`'s `hasTestSources`/`moduleDir` - whether a module has test sources of its own, the fact that decides (on the Gradle side) whether it even has a `test` task to scope to. */
fun hasTestSources(projectRoot: String, moduleRoot: String): Boolean {
    val dir = moduleDir(projectRoot, moduleRoot)
    return File(dir, "src/test/java").exists() || File(dir, "src/test/kotlin").exists()
}

/** Port of `preflight.ts`'s `describeMissingMainSource` - a real, checkable fact (not a relevance guess) for a module picker row. `null` means the module has ordinary main sources. Kotlin counts: a Gradle module's production code is often `src/main/kotlin`. */
fun describeMissingMainSource(projectRoot: String, moduleRoot: String): String? {
    val dir = moduleDir(projectRoot, moduleRoot)
    val hasMain = File(dir, "src/main/java").exists() || File(dir, "src/main/kotlin").exists()
    return if (hasMain) null else "no src/main/java"
}

private fun moduleDir(projectRoot: String, moduleRoot: String): String = if (moduleRoot == ".") projectRoot else File(projectRoot, moduleRoot).path

/** Port of `preflight.ts`'s `readGradleSettings` - `settings.gradle.kts` wins over `settings.gradle`, the same priority `GradleProjectScanner.java` applies. `null` means neither exists. */
fun readGradleSettings(projectRoot: String): String? {
    for (name in listOf("settings.gradle.kts", "settings.gradle")) {
        val file = File(projectRoot, name)
        if (file.exists()) {
            return try {
                file.readText()
            } catch (e: Exception) {
                null
            }
        }
    }
    return null
}
