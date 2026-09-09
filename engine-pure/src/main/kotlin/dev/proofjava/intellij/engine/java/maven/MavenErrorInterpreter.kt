package dev.proofjava.intellij.engine.java.maven

/**
 * Port of `proof-vscode/src/cli/mavenErrorInterpreter.ts`: recognizes a
 * small, closed set of Maven failure shapes from raw subprocess output and
 * turns them into an honest, actionable sentence - never a guess. An
 * unrecognized failure returns `null` and the caller falls back to "see
 * the log for detail" with the raw text already logged (hard rule 3a: an
 * unrecognized cause is never invented).
 *
 * Every shape here is ported from text the TS source's own test fixtures
 * capture verbatim from a real failure, not written from documentation.
 */

enum class MavenFailureKind { UNRESOLVED_REACTOR_SIBLING, NO_PLUGIN_PREFIX, ENFORCER_JDK, UNRESOLVED_JPMS_MODULE }

data class MavenFailureInterpretation(val kind: MavenFailureKind, val detail: String)

private val UNRESOLVED_ARTIFACT_PATTERN = Regex("""Could not find artifact ([\w.-]+:[\w.-]+:jar:[\w.-]+)""")
private val NO_PLUGIN_PREFIX_PATTERN = Regex("""No plugin found for prefix '([^']+)'""")
private val ENFORCER_JDK_PATTERN = Regex("""Detected JDK Version:.*is not in the allowed range[^\n]*""")
private val JPMS_MODULE_NOT_FOUND_PATTERN = Regex("""module-info\.java:\[\d+,\d+]\s*module not found:\s*([\w.]+)""")

fun interpretMavenFailure(output: String): MavenFailureInterpretation? =
    interpretUnresolvedReactorSibling(output)
        ?: interpretNoPluginPrefix(output)
        ?: interpretEnforcerJdk(output)
        ?: interpretUnresolvedJpmsModule(output)

/**
 * `dependency:build-classpath` resolves a reactor sibling module through
 * its **installed** jar in `~/.m2`, never through its freshly-built
 * `target/classes` - a module that depends on a sibling that has never
 * been `mvn install`-ed fails with both of these lines together:
 *
 * ```
 * Could not resolve dependencies for project com.google.code.gson:test-jpms:...
 * Could not find artifact com.google.code.gson:gson:jar:2.14.1-SNAPSHOT
 * ```
 */
private fun interpretUnresolvedReactorSibling(output: String): MavenFailureInterpretation? {
    if (!output.contains("Could not resolve dependencies for project")) return null
    val match = UNRESOLVED_ARTIFACT_PATTERN.find(output) ?: return null
    return MavenFailureInterpretation(
        MavenFailureKind.UNRESOLVED_REACTOR_SIBLING,
        "Maven couldn't find the `${match.groupValues[1]}` module (from the same repo) in the local repository - that module hasn't been installed yet. " +
            "A sibling module's classpath can't be generated without running `mvn install -DskipTests` once first.",
    )
}

/** A pom with no `jacoco-maven-plugin` declared cannot resolve the short `jacoco:report` goal form - this engine always uses the full coordinate, so seeing this means something else on the machine tried the short form. */
private fun interpretNoPluginPrefix(output: String): MavenFailureInterpretation? {
    val match = NO_PLUGIN_PREFIX_PATTERN.find(output) ?: return null
    return MavenFailureInterpretation(
        MavenFailureKind.NO_PLUGIN_PREFIX,
        "This project's pom doesn't declare the JaCoCo plugin, so the short \"${match.groupValues[1]}:...\" form doesn't work. " +
            "This engine's own commands always use the full coordinate (org.jacoco:jacoco-maven-plugin:<version>:...).",
    )
}

/** Quotes Maven's own sentence verbatim rather than paraphrasing a version range this engine does not know. */
private fun interpretEnforcerJdk(output: String): MavenFailureInterpretation? {
    val match = ENFORCER_JDK_PATTERN.find(output) ?: return null
    return MavenFailureInterpretation(
        MavenFailureKind.ENFORCER_JDK,
        "Maven's own message: \"${match.value.trim()}\". Point the configured Java executable / JAVA_HOME at a JDK within the range this project expects.",
    )
}

/**
 * A sibling module's own JPMS `module-info.java` requires a reactor module
 * this engine never asked for and has nothing to do with the module being
 * analyzed. The dependency module descriptor is typically only added to
 * the JAR at the `package` phase - a plain `test`/`verify` build never
 * produces one, so this fails deterministically regardless of
 * install/build order.
 */
private fun interpretUnresolvedJpmsModule(output: String): MavenFailureInterpretation? {
    val match = JPMS_MODULE_NOT_FOUND_PATTERN.find(output) ?: return null
    return MavenFailureInterpretation(
        MavenFailureKind.UNRESOLVED_JPMS_MODULE,
        "A module's `module-info.java` can't find the `${match.groupValues[1]}` module on the module path - module descriptors are usually only added to the JAR " +
            "at the `package` phase, not yet present at `test`/`verify`. Either scope this module out (re-scan and select only the module you need), or run a full `mvn install`/`package`.",
    )
}
