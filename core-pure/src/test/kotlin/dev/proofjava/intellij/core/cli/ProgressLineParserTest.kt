package dev.proofjava.intellij.core.cli

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Kotlin port of `proof-vscode/src/test/unit/cli/progressParser.test.ts`.
 * Line formats are real, taken from the CLI's own source
 * (`MutationCollector`/`MutationRunner`/`PerTestCollector`/`PerTestRunner`)
 * and verified against a real playground run (2026-08-28, per the TS
 * source's own note) - not invented for this test.
 */
class ProgressLineParserTest {

    @Test
    fun `a real mutation run's start line - target count and budget`() {
        val event = parseProgressLine("proof-java: mutation: module 'root' - 1 target class(es), budget 300s")
        assertEquals(ProgressEvent.Start(ProgressEventKind.MUTATION, "root", total = 1, budgetSeconds = 300), event)
    }

    @Test
    fun `a real L2 run's start line - no budget`() {
        val event = parseProgressLine("proof-java: per-test: module 'root' - 4 target class(es)")
        assertEquals(ProgressEvent.Start(ProgressEventKind.PER_TEST, "root", total = 4, budgetSeconds = null), event)
    }

    @Test
    fun `heartbeat - class counter and elapsed time`() {
        val event = parseProgressLine("proof-java: mutation: module 'root' - 2/3 class(es), 6m12s elapsed")
        assertEquals(ProgressEvent.Heartbeat(ProgressEventKind.MUTATION, "root", done = 2, total = 3, elapsed = "6m12s"), event)
    }

    @Test
    fun `L2 heartbeat has no counter - done and total stay null, never invented`() {
        val event = parseProgressLine("proof-java: per-test: module 'root' - collecting coverage, 48s elapsed")
        assertEquals(ProgressEvent.Heartbeat(ProgressEventKind.PER_TEST, "root", elapsed = "48s"), event)
    }

    @Test
    fun `a real run's done line`() {
        val event = parseProgressLine("proof-java: mutation: module 'root' - done, 16 method(s) with mutants")
        assertEquals(ProgressEvent.Done(ProgressEventKind.MUTATION, "root", "done, 16 method(s) with mutants"), event)
    }

    @Test
    fun `a budget overrun is a failure, not a done`() {
        val event = parseProgressLine("proof-java: mutation: module 'root' - FAILED, budget of 300s exhausted after 2/3 class(es) completed")
        assertEquals(ProgressEvent.Failed::class, (event!!)::class)
    }

    @Test
    fun `no mutable target is also a failure, not a done`() {
        val event = parseProgressLine("proof-java: mutation: module 'root' - no mutable target found by the engine")
        assertEquals(ProgressEvent.Failed::class, (event!!)::class)
    }

    @Test
    fun `parses without the proof-java prefix too (the runner already strips it)`() {
        val event = parseProgressLine("mutation: module 'root' - 2/3 class(es), 1m elapsed")
        assertEquals(ProgressEvent.Heartbeat::class, (event!!)::class)
    }

    /** Hard rule 3a: an unrecognized line is never swallowed as an invented event - it stays null and the caller writes it verbatim. */
    @Test
    fun `an unrecognized line returns null, not a fabricated event`() {
        assertNull(parseProgressLine("proof-java: analysis complete (no-vcs)"))
        assertNull(parseProgressLine("  jacoco-line        89.5% (17/19)"))
        assertNull(parseProgressLine(""))
        assertNull(parseProgressLine("proof-java: mutation: module 'root' - something we have never seen"))
    }

    @Test
    fun `progressMessage always includes elapsed time - a frozen percentage across a 30s heartbeat should not look stuck`() {
        val heartbeat = parseProgressLine("proof-java: mutation: module 'root' - 2/3 class(es), 6m12s elapsed")!!
        assertEquals("2/3 class(es) · 6m12s", progressMessage(heartbeat))

        val plain = parseProgressLine("proof-java: per-test: module 'root' - collecting coverage, 48s elapsed")!!
        assertEquals("collecting evidence · 48s", progressMessage(plain))
    }

    @Test
    fun `progressMessage mentions the budget at start too`() {
        val start = parseProgressLine("proof-java: mutation: module 'root' - 3 target class(es), budget 300s")!!
        assertEquals("3 class(es) to scan · budget 300s", progressMessage(start))
    }

    @Test
    fun `incrementFor gives a delta, not an absolute percentage`() {
        val at2of4 = parseProgressLine("proof-java: mutation: module 'root' - 2/4 class(es), 1m elapsed")!!
        assertEquals(ProgressIncrement(50.0, 2), incrementFor(at2of4, 0))
        assertEquals(ProgressIncrement(25.0, 2), incrementFor(at2of4, 1))
    }

    /** A fabricated progress bar is worse than no progress bar when the total is unknown. */
    @Test
    fun `incrementFor publishes nothing for a counter-less heartbeat or a backward count`() {
        val plain = parseProgressLine("proof-java: per-test: module 'root' - collecting coverage, 48s elapsed")!!
        assertNull(incrementFor(plain, 0))

        val at2of4 = parseProgressLine("proof-java: mutation: module 'root' - 2/4 class(es), 1m elapsed")!!
        assertNull(incrementFor(at2of4, 2), "the same count coming back around must not jump the bar forward")
        assertNull(incrementFor(at2of4, 3), "a backward-moving count must not produce a negative increment")

        val start = parseProgressLine("proof-java: mutation: module 'root' - 3 target class(es), budget 300s")!!
        assertNull(incrementFor(start, 0))
    }

    /**
     * The real multi-module regression: `gson` reaches 3/3 and then `extras`
     * starts its own 1/5 - a single shared `previousDone` would see `1 < 3`
     * as negative and drop the increment, freezing the bar. The caller must
     * track `previousDone` per moduleId; this function trusts whatever it's
     * given, so the fix is exercised here as two independent counters.
     */
    @Test
    fun `incrementFor - a second module starting its own count is not a regression against the first module's count`() {
        val gsonDone3of3 = parseProgressLine("proof-java: mutation: module 'gson' - 3/3 class(es), 2m elapsed")!!
        assertEquals(ProgressIncrement(50.0, 3), incrementFor(gsonDone3of3, 0, moduleCount = 2))

        val extrasDone1of5 = parseProgressLine("proof-java: mutation: module 'extras' - 1/5 class(es), 10s elapsed")!!
        assertEquals(ProgressIncrement(10.0, 1), incrementFor(extrasDone1of5, 0, moduleCount = 2))
    }

    @Test
    fun `incrementFor - moduleCount scales a module's share so an N-module run cannot exceed 100 total`() {
        val fullModule = parseProgressLine("proof-java: mutation: module 'gson' - 3/3 class(es), 2m elapsed")!!
        val result = incrementFor(fullModule, 0, moduleCount = 3)
        assertEquals(100.0 / 3, result?.increment)
        assertEquals(3, result?.done)
    }

    @Test
    fun `incrementFor - moduleCount defaults to 1 (single-module run)`() {
        val at2of4 = parseProgressLine("proof-java: mutation: module 'root' - 2/4 class(es), 1m elapsed")!!
        assertEquals(ProgressIncrement(50.0, 2), incrementFor(at2of4, 0))
    }

    @Test
    fun `progressMessage - showModule prefixes the module id, off by default`() {
        val heartbeat = parseProgressLine("proof-java: mutation: module 'gson' - 2/3 class(es), 6m12s elapsed")!!
        assertEquals("2/3 class(es) · 6m12s", progressMessage(heartbeat))
        assertEquals("gson · 2/3 class(es) · 6m12s", progressMessage(heartbeat, showModule = true))
        assertEquals("2/3 class(es) · 6m12s", progressMessage(heartbeat, showModule = false))
    }

    @Test
    fun `progressMessage - showModule prefixes start, done and failed too`() {
        val start = parseProgressLine("proof-java: mutation: module 'gson' - 3 target class(es), budget 300s")!!
        assertEquals("gson · 3 class(es) to scan · budget 300s", progressMessage(start, showModule = true))

        val done = parseProgressLine("proof-java: mutation: module 'gson' - done, 5 method(s) with mutants")!!
        assertEquals("gson · done", progressMessage(done, showModule = true))

        val failed = parseProgressLine("proof-java: mutation: module 'gson' - FAILED, budget of 300s exhausted after 2/3 class(es) completed")!!
        assertEquals("gson · FAILED, budget of 300s exhausted after 2/3 class(es) completed", progressMessage(failed, showModule = true))
    }

    /** `DoctorCommand.applyFixes`'s real line: `printErr("proof-java: doctor: fixing classpath for '" + module.id() + "'...")`. */
    @Test
    fun `parseDoctorProgressLine extracts the module id from doctor --fix's own line`() {
        assertEquals(DoctorFixProgress("gson"), parseDoctorProgressLine("proof-java: doctor: fixing classpath for 'gson'..."))
        assertEquals(DoctorFixProgress("root"), parseDoctorProgressLine("proof-java: doctor: fixing classpath for 'root'..."))
    }

    @Test
    fun `parseDoctorProgressLine - an unrecognized line returns null, doctor's other lines are never invented`() {
        assertNull(parseDoctorProgressLine("proof-java: doctor: 'gson' - Maven dependency resolution failed: ..."))
        assertNull(parseDoctorProgressLine("proof-java: doctor: wrote proof.config.json"))
        assertNull(parseDoctorProgressLine("  [ok] gson"))
        assertNull(parseDoctorProgressLine(""))
    }
}
