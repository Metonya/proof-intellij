package dev.proofjava.intellij.core.cli

/**
 * Port of `proof-vscode/src/cli/progressParser.ts`. The CLI prints every
 * progress line to **stderr**, prefixed `proof-java: `, flushed
 * immediately (D-64: stdout carries the text report and the JSON must stay
 * byte-deterministic). Heartbeat is 30 seconds. Real formats
 * (`MutationCollector`/`MutationRunner`/`PerTestCollector`/`PerTestRunner`,
 * verified 2026-08-28 on the proof-vscode side):
 *
 * ```
 * proof-java: mutation: module 'root' - 3 target class(es), budget 300s
 * proof-java: mutation: module 'root' - 2/3 class(es), 6m12s elapsed
 * proof-java: mutation: module 'root' - done, 5 method(s) with mutants
 * proof-java: mutation: module 'root' - FAILED, budget of 300s exhausted after 2/3 class(es) completed
 * proof-java: per-test: module 'root' - 4 target class(es)
 * proof-java: per-test: module 'root' - collecting coverage, 48s elapsed
 * ```
 *
 * Pure - no `com.intellij.*` import, same contract as [AnalyzeArgsBuilder].
 *
 * Returns `null` for an unrecognized line and does **not** swallow it: the
 * caller must write it to the log/output verbatim (hard rule 3a). These
 * formats are the CLI's internal detail, not its contract - if they change,
 * this parser silently stops showing a percentage rather than showing a
 * wrong one.
 */

enum class ProgressEventKind { MUTATION, PER_TEST }

sealed interface ProgressEvent {
    val kind: ProgressEventKind
    val moduleId: String

    /** Run started; target class count known, none finished yet. */
    data class Start(override val kind: ProgressEventKind, override val moduleId: String, val total: Int, val budgetSeconds: Int? = null) : ProgressEvent

    /** 30-second heartbeat. `done`/`total` only present for mutation; L2 does not publish a class counter. */
    data class Heartbeat(override val kind: ProgressEventKind, override val moduleId: String, val done: Int? = null, val total: Int? = null, val elapsed: String) : ProgressEvent

    data class Done(override val kind: ProgressEventKind, override val moduleId: String, val message: String) : ProgressEvent

    data class Failed(override val kind: ProgressEventKind, override val moduleId: String, val message: String) : ProgressEvent
}

private const val PREFIX = "proof-java: "
private val HEAD = Regex("""^(mutation|per-test): module '([^']*)' - (.*)$""")
private val START = Regex("""^(\d+) target class\(es\)(?:, budget (\d+)s)?$""")
private val HEARTBEAT_WITH_COUNT = Regex("""^(\d+)/(\d+) class\(es\), (.+) elapsed$""")
private val HEARTBEAT_PLAIN = Regex("""^collecting coverage, (.+) elapsed$""")

fun parseProgressLine(line: String): ProgressEvent? {
    val trimmed = (if (line.startsWith(PREFIX)) line.substring(PREFIX.length) else line).trim()
    val head = HEAD.find(trimmed) ?: return null
    val kind = if (head.groupValues[1] == "mutation") ProgressEventKind.MUTATION else ProgressEventKind.PER_TEST
    val moduleId = head.groupValues[2]
    val rest = head.groupValues[3]

    START.find(rest)?.let {
        val total = it.groupValues[1].toInt()
        val budget = it.groupValues[2].takeIf { b -> b.isNotEmpty() }?.toInt()
        return ProgressEvent.Start(kind, moduleId, total, budget)
    }

    HEARTBEAT_WITH_COUNT.find(rest)?.let {
        return ProgressEvent.Heartbeat(kind, moduleId, done = it.groupValues[1].toInt(), total = it.groupValues[2].toInt(), elapsed = it.groupValues[3])
    }

    HEARTBEAT_PLAIN.find(rest)?.let {
        return ProgressEvent.Heartbeat(kind, moduleId, elapsed = it.groupValues[1])
    }

    if (rest.startsWith("done,") || rest == "done") {
        return ProgressEvent.Done(kind, moduleId, rest)
    }
    // "FAILED, ..." and "no mutable target found by the engine" both mean the
    // run ended, but not successfully.
    if (rest.startsWith("FAILED") || rest.startsWith("no mutable target")) {
        return ProgressEvent.Failed(kind, moduleId, rest)
    }
    return null
}

/**
 * The single line to show the user for one progress event. Percent only
 * advances every 30s, so elapsed time is **always** included - otherwise
 * the UI looks frozen (a mutation run can take 70-90 minutes per module).
 * [showModule] (true in a multi-module run) prefixes the module id - noise
 * in a single-module run, so opt-in.
 */
fun progressMessage(event: ProgressEvent, showModule: Boolean = false): String {
    val prefix = if (showModule) "${event.moduleId} · " else ""
    return when (event) {
        is ProgressEvent.Start -> prefix + if (event.budgetSeconds == null) {
            "${event.total} class(es) to scan"
        } else {
            "${event.total} class(es) to scan · budget ${event.budgetSeconds}s"
        }
        is ProgressEvent.Heartbeat -> prefix + if (event.done != null && event.total != null) {
            "${event.done}/${event.total} class(es) · ${event.elapsed}"
        } else {
            "collecting evidence · ${event.elapsed}"
        }
        is ProgressEvent.Done -> "${prefix}done"
        is ProgressEvent.Failed -> prefix + event.message
    }
}

/**
 * A progress-bar increment, or `null` for "don't publish one" (an unbounded
 * L2 heartbeat has no total, and a fabricated progress bar is worse than no
 * progress bar at all).
 *
 * [previousDone] must be tracked **per module** by the caller - the CLI
 * scans modules in sequence, and a module reaching `3/3` before the next
 * module starts its own `1/5` would otherwise read as a negative delta
 * against one shared counter and make the bar look stuck (a real
 * regression on the proof-vscode side; this signature exists specifically
 * so a caller cannot share one counter across modules).
 * [moduleCount] scales this module's share so an N-module run's total
 * never exceeds 100%.
 */
data class ProgressIncrement(val increment: Double, val done: Int)

fun incrementFor(event: ProgressEvent, previousDone: Int, moduleCount: Int = 1): ProgressIncrement? {
    if (event !is ProgressEvent.Heartbeat || event.done == null || event.total == null || event.total == 0) {
        return null
    }
    val increment = ((event.done - previousDone).toDouble() / event.total) * (100.0 / moduleCount)
    return if (increment > 0) ProgressIncrement(increment, event.done) else null
}

private val DOCTOR_FIX_LINE = Regex("""^proof-java: doctor: fixing classpath for '([^']*)'\.\.\.$""")

/**
 * `doctor --fix`'s own progress line (`DoctorCommand.applyFixes`). The CLI
 * does not print a total module count (only one line per module that
 * actually needed fixing), so "which module number" is not derived here -
 * the caller already knows `modules.size` and computes its own share,
 * rather than this parser inventing a number the CLI never gave.
 */
data class DoctorFixProgress(val moduleId: String)

fun parseDoctorProgressLine(line: String): DoctorFixProgress? {
    val match = DOCTOR_FIX_LINE.find(line.trim()) ?: return null
    return DoctorFixProgress(match.groupValues[1])
}
