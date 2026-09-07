package dev.proofjava.intellij.engine.java.locator

import java.io.File

/**
 * Port of `proof-vscode/src/cli/jarLocator.ts`'s search order:
 * [configuredPath] first (accepted now so this function's signature does
 * not need to change once a settings service exists to supply one - `null`
 * skips straight to the defaults), then `<project>/proof-java-cli/target/proof-java.jar`
 * (proof-java's own dev-repo layout, so this same search works when
 * developing proof-java itself), then `<project>/.proof-java/proof-java.jar`,
 * then `~/.proof-java/proof-java.jar` (shared across every project on this
 * machine - the "User" scope "Download proof-java.jar" will offer once that
 * command exists here). The jar itself is never bundled into the plugin.
 */
fun locateJar(projectRoot: String, configuredPath: String? = null): String? {
    if (!configuredPath.isNullOrBlank()) {
        val configured = File(configuredPath)
        val resolved = if (configured.isAbsolute) configured else File(projectRoot, configuredPath)
        return if (resolved.exists()) resolved.path else null
    }
    return defaultCandidates(projectRoot).firstOrNull { File(it).exists() }
}

/** Exported so a future "Download proof-java.jar" command can offer these exact two locations - whatever it writes to, [locateJar] above must find with zero extra config. */
fun workspaceJarPath(projectRoot: String): String = File(File(projectRoot, ".proof-java"), "proof-java.jar").path

fun userJarPath(): String = File(File(userHome(), ".proof-java"), "proof-java.jar").path

private fun userHome(): String = System.getProperty("user.home")

private fun defaultCandidates(projectRoot: String): List<String> = listOf(
    File(File(File(projectRoot, "proof-java-cli"), "target"), "proof-java.jar").path,
    workspaceJarPath(projectRoot),
    userJarPath(),
)
