package dev.proofjava.intellij.engine.java.gradle

/**
 * Port of `proof-vscode/src/cli/gradleTestCommand.ts`: turns an optional
 * module scope into the Gradle task paths "Run Tests" should ask for.
 *
 * Two things differ from Maven and are easy to get wrong:
 * - **Every task path is project-qualified, the root one included.** An
 *   unqualified `test` matches that task in the current project *and
 *   every* subproject, so `:test` (root only) and `test` (everything) are
 *   genuinely different commands.
 * - **There is no `-am` analogue, and none is needed.** Gradle already
 *   builds whatever the requested tasks depend on, project dependencies
 *   included; Maven's `-pl ... -am` exists because its reactor does not.
 */

data class GradleTestArgsInput(
    /** Repo-relative module roots (`.`, `core`, `modules/service-a`), or empty for an unscoped run. Empty keeps the historical `["test", "jacocoTestReport"]` argv exactly. */
    val moduleRoots: List<String> = emptyList(),
    /**
     * The task that writes the JaCoCo report, when it is not Gradle's own
     * default. `jacocoTestReport` is only the default *of the `jacoco`
     * plugin*, and plenty of real builds have no task by that name (a
     * plain JVM module that never applies `jacoco` has none at all; an
     * Android library has variant-named ones instead). Asking for a task
     * that does not exist fails the whole build at selection time, before
     * anything runs.
     */
    val coverageTask: String? = null,
)

private const val TEST_TASK = "test"
const val DEFAULT_COVERAGE_TASK = "jacocoTestReport"

/**
 * `runner`'s Gradle wrapper is spawned with `shell: true` (Windows `.bat`
 * requirement) and Gradle takes one task path per argument, so a module
 * root is shell text. `include("my module")` is legal Gradle, so this is a
 * real input, not a hypothetical: rather than quote per-platform, an
 * unrepresentable root drops scoping entirely (see [buildGradleTestArgs]) -
 * a slower correct run beats a mangled one.
 */
private val SHELL_SAFE_MODULE_ROOT = Regex("""^[A-Za-z0-9_.\-/]+$""")

fun isShellSafeModuleRoot(moduleRoot: String): Boolean = SHELL_SAFE_MODULE_ROOT.matches(moduleRoot)

/** `.` -> `:test`; `core` -> `:core:test`; `modules/service-a` -> `:modules:service-a:test`. */
private fun taskPathFor(moduleRoot: String, taskName: String): String =
    if (moduleRoot == "." || moduleRoot.isEmpty()) ":$taskName" else ":${moduleRoot.replace("/", ":")}:$taskName"

/** Scoping is dropped (an unscoped, whole-build run) when any root is not shell-safe - callers that can show UI should say so with [unsafeModuleRoots] first. */
fun buildGradleTestArgs(input: GradleTestArgsInput): List<String> {
    val coverageTask = input.coverageTask?.trim()?.ifEmpty { null } ?: DEFAULT_COVERAGE_TASK
    val roots = input.moduleRoots
    if (roots.isEmpty() || roots.any { !isShellSafeModuleRoot(it) }) {
        return listOf(TEST_TASK, coverageTask)
    }
    return roots.flatMap { listOf(taskPathFor(it, TEST_TASK), taskPathFor(it, coverageTask)) }
}

/** The roots [buildGradleTestArgs] would refuse to scope to - so a caller can name them in a warning instead of silently running the whole build. */
fun unsafeModuleRoots(moduleRoots: List<String>): List<String> = moduleRoots.filter { !isShellSafeModuleRoot(it) }
