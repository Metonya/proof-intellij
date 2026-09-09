package dev.proofjava.intellij.engine.java.gradle

/**
 * Port of `proof-vscode/src/cli/gradleErrorInterpreter.ts`: recognizes a
 * small, closed set of Gradle failure shapes from raw subprocess output
 * and turns them into an honest, actionable sentence - never a guess. The
 * Gradle sibling of [dev.proofjava.intellij.engine.java.maven.interpretMavenFailure];
 * an unrecognized failure returns `null`, hard rule 3a.
 */

enum class GradleFailureKind { TASK_NOT_FOUND_IN_PROJECT, ANDROID_SDK_MISSING }

data class GradleFailureInterpretation(val kind: GradleFailureKind, val detail: String)

/**
 * Captured verbatim from Gradle 9.7.1 on the real junit-framework repo
 * (`./gradlew :junit-bom:test`):
 * ```
 * Selection failed
 *   Cannot locate tasks that match ':junit-bom:test' as task 'test' not found in project ':junit-bom'.
 * ```
 * Matched case-insensitively on the inner clause alone, because that
 * clause is also how the message reads when it starts a sentence - the
 * surrounding "Selection failed / Cannot locate tasks" wrapper is the part
 * that varies.
 */
private val TASK_NOT_FOUND_PATTERN = Regex("""task '([^']+)' not found in project '([^']+)'""", RegexOption.IGNORE_CASE)

fun interpretGradleFailure(output: String): GradleFailureInterpretation? =
    interpretTaskNotFoundInProject(output) ?: interpretAndroidSdkMissing(output)

/**
 * The failure mode module scoping introduces. An unscoped `gradlew test
 * jacocoTestReport` silently skips every project without those tasks; the
 * moment a run is scoped, a project missing either one fails the whole
 * build at task-selection time, before anything executes.
 *
 * Two causes, different advice: no `test` task means a `java-platform` BOM
 * or a docs-only module simply has no tests. No coverage task means
 * `jacocoTestReport` (the `jacoco` plugin's default task name, not a
 * universal one) isn't there - a plain JVM module that never applies
 * `jacoco` has none at all, and an Android library has variant-named ones
 * instead (`createDemoDebugUnitTestCoverageReport` and siblings).
 */
private fun interpretTaskNotFoundInProject(output: String): GradleFailureInterpretation? {
    val match = TASK_NOT_FOUND_PATTERN.find(output) ?: return null
    val (taskName, projectPath) = match.destructured
    val moduleName = projectPath.removePrefix(":").ifEmpty { "the root project" }
    if (taskName == "test") {
        return GradleFailureInterpretation(
            GradleFailureKind.TASK_NOT_FOUND_IN_PROJECT,
            "`$moduleName` has no tests to run - a BOM or docs-only module has no `test` task at all. Uncheck it in the module picker on the next run.",
        )
    }
    return GradleFailureInterpretation(
        GradleFailureKind.TASK_NOT_FOUND_IN_PROJECT,
        "`$moduleName` has no `$taskName` task. That name is the default of Gradle's `jacoco` plugin: an Android module never has it (AGP names coverage " +
            "tasks per variant, e.g. `createDemoDebugUnitTestCoverageReport`), and a plain JVM module only has it when it applies that plugin. " +
            "Point the configured Gradle coverage task at the task this build really has, or uncheck the module.",
    )
}

/**
 * Captured verbatim from a real run of
 * `./gradlew :core:data:test :core:data:createDemoDebugUnitTestCoverageReport`
 * on Now in Android, on a machine whose Android SDK had only
 * `platform-tools` installed. Worth its own shape because the raw output
 * buries it under hundreds of AGP configuration warnings, and because
 * nothing about it is this engine's doing - the build simply cannot run
 * here at all.
 */
private val ANDROID_SDK_MISSING_PATTERN = Regex("""SDK location not found""", RegexOption.IGNORE_CASE)

private fun interpretAndroidSdkMissing(output: String): GradleFailureInterpretation? {
    if (!ANDROID_SDK_MISSING_PATTERN.containsMatchIn(output)) return null
    return GradleFailureInterpretation(
        GradleFailureKind.ANDROID_SDK_MISSING,
        "this Android build needs an Android SDK, and Gradle could not find one. Set `ANDROID_HOME`, or put `sdk.dir` into the `local.properties` file at " +
            "the repo root. Nothing about this is specific to Proof - the build cannot run at all without an SDK.",
    )
}
