package dev.proofjava.intellij.core.model

import dev.proofjava.intellij.core.verdict.Reason

/**
 * Port of `proof-vscode/src/model/warningCatalog.ts`. Gives each `warnings[]`
 * code a plain title + "what it means" + "what to do" - the raw message is
 * never dropped (it carries real numbers the CLI counted, e.g. how many
 * lines), it stays available via [Reason.message]. An unrecognized code
 * falls back to itself as the title (hard rule 3a: never pretend to
 * recognize something we don't).
 */
data class WarningInfo(val code: String, val title: String, val explanation: String?, val action: String?)

private data class CatalogEntry(val title: String, val explanation: String, val action: String)

private val CATALOG: Map<String, CatalogEntry> = mapOf(
    "CHANGED_LINES_ABSENT_FROM_REPORT" to CatalogEntry(
        title = "Some changed lines are missing from the coverage report",
        explanation = "Some of the lines you changed never appear in the JaCoCo report at all, so they contributed to neither the numerator nor the denominator of the \"new code\" percentage. " +
            "There are two possible causes and proof-java can't tell them apart: (1) those lines aren't executable code to begin with - a brace, a method signature, a blank line; JaCoCo never lists these, and that's completely normal. " +
            "(2) The report is older than this change - i.e. you haven't run the tests since your last edit.",
        action = "If the number is lower than expected, rerun the tests to refresh the report first. If it still shows up after that, the remaining lines are likely just braces/signatures.",
    ),
    "CHANGED_FILES_EXCLUDED" to CatalogEntry(
        title = "Some changed files were excluded from coverage",
        explanation = "Some of the files you changed matched a coverage-exclusions pattern (or a test folder), so they never entered the new-code calculation at all.",
        action = "If that's intentional, there's nothing to do. Otherwise, review your coverage-exclusions setting.",
    ),
    "MODULE_WITHOUT_REPORT" to CatalogEntry(
        title = "A module has no coverage report",
        explanation = "A defined module has no JaCoCo report bound to it, so that module was excluded from the analyzed set entirely - its coverage isn't 0%, it's simply unknown.",
        action = "Run that module's tests with JaCoCo and make sure the report path setting points at the right file.",
    ),
    "PER_TEST_NO_CHANGED_TARGETS" to CatalogEntry(
        title = "No target class for per-test evidence",
        explanation = "Deep Scan can only target production classes that changed in the diff; nothing was collected because no class changed in this run. This isn't an error.",
        action = "Make a real change to a file and scan again, or target a single class directly.",
    ),
    "PER_TEST_CLASSPATH_MISSING" to CatalogEntry(
        title = "No classpath file bound for per-test evidence",
        explanation = "Deep Scan reruns your tests under PIT, which needs a file listing the full test classpath line by line; no such file is bound to this module.",
        action = "Generate the classpath list and make sure the classpath path setting points at it.",
    ),
    "PER_TEST_TRUNCATED" to CatalogEntry(
        title = "Per-test evidence was truncated",
        explanation = "Part of the collected evidence was dropped. What's shown may be incomplete - a line not appearing here does NOT mean \"no test covers it\".",
        action = "If you need complete evidence, rerun the scan with a narrower scope (a single class).",
    ),
    "PER_TEST_EMPTY_EVIDENCE" to CatalogEntry(
        title = "Per-test evidence ran but came back empty",
        explanation = "The engine ran but couldn't resolve any test-to-line records. Evidence was requested but is missing - this means \"we couldn't tell\", not \"no test covers these lines\".",
        action = "Confirm the tests actually ran and that the classpath list includes the compiled classes.",
    ),
    "PER_TEST_TARGET_UNRESOLVED" to CatalogEntry(
        title = "Target class not found",
        explanation = "The class name given for per-test evidence couldn't be resolved to a source file under any declared source root, so it was skipped.",
        action = "Make sure the class name is fully qualified (package included) and the file lives under a declared source root.",
    ),
    "PER_TEST_TARGET_NOT_BOUND" to CatalogEntry(
        title = "No target class given for a module",
        explanation = "A defined module had no target class bound to it in this run, so it was excluded from per-test evidence entirely. Normal when targeting a single module in a multi-module project.",
        action = "If that's intentional, there's nothing to do.",
    ),
    "PER_TEST_COLLECTION_FAILED" to CatalogEntry(
        title = "Per-test evidence collection failed",
        explanation = "The engine reported an error for this module; its per-test evidence was skipped. Coverage numbers are unaffected, only \"which test covers which line\" is missing.",
        action = "Check the proof-java log for detail.",
    ),
)

fun warningInfo(reason: Reason): WarningInfo {
    val known = CATALOG[reason.code]
    return WarningInfo(code = reason.code, title = known?.title ?: reason.code, explanation = known?.explanation, action = known?.action)
}
