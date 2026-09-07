package dev.proofjava.intellij.engine.java.maven

/**
 * Port of `proof-vscode/src/cli/mavenTestCommand.ts`: builds the Maven argv
 * for "Run Tests". Default is still the whole reactor - no `-pl`/`-am`,
 * matching what a user would type by hand. [MavenTestArgsInput.moduleRoots]
 * scopes it to just the module(s) already known to be needed, once a scan
 * has bound them - a whole-reactor run can pull in sibling modules
 * (native-image, ProGuard-obfuscated test classes, JPMS) that have nothing
 * to do with the module actually being analyzed. The very first run (no
 * scan yet, no known modules) still has to be whole-reactor - there is
 * nothing to scope to yet, and guessing one would be a guess.
 */

enum class MavenTestPhase { TEST, VERIFY }

data class MavenTestArgsInput(
    val phase: MavenTestPhase,
    /** Whether to inject `org.jacoco:jacoco-maven-plugin:<version>:prepare-agent`/`:report` around the phase - false when some pom in the reactor already configures the plugin itself. */
    val injectJacocoGoals: Boolean,
    val jacocoPluginVersion: String,
    /** Repo-relative module roots to scope the build to via `-pl <roots> -am`. Empty means the whole reactor. */
    val moduleRoots: List<String> = emptyList(),
)

fun buildMavenTestArgs(input: MavenTestArgsInput): List<String> {
    val args = mutableListOf("-B")
    if (input.moduleRoots.isNotEmpty()) {
        args += listOf("-pl", input.moduleRoots.joinToString(","), "-am")
    }
    if (input.injectJacocoGoals) {
        args += "org.jacoco:jacoco-maven-plugin:${input.jacocoPluginVersion}:prepare-agent"
    }
    args += if (input.phase == MavenTestPhase.TEST) "test" else "verify"
    if (input.injectJacocoGoals) {
        args += "org.jacoco:jacoco-maven-plugin:${input.jacocoPluginVersion}:report"
    }
    return args
}
