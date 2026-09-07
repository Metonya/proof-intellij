package dev.proofjava.intellij.core.verdict

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * [writePerTestSnapshotJson]/[parsePerTestSnapshot] round-trip - the
 * `.proof/pertest-current.json` shape a Deep Scan writes and a project
 * reopen (`core.startup.RestoreLastScanActivity`) reads back.
 */
class PerTestSnapshotTest {

    private val snapshot = PerTestSnapshot(
        perTest = PerTestBlock(
            engine = "pitest",
            engineVersion = "1.15.8",
            modules = listOf(
                PerTestModuleEvidence(
                    id = "root",
                    entries = listOf(PerTestEntry("Calculator", "add", listOf(PerTestLine(7, listOf("CalcTest#addsTwoNumbers()"))))),
                    ambient = emptyList(),
                ),
            ),
        ),
        warnings = listOf(Reason("PER_TEST_TRUNCATED", "evidence was cut short", path = null, module = "root", count = 3)),
        targets = listOf("dev.example.Calculator"),
        ranAtMs = 1_757_000_000_000L,
    )

    @Test
    fun `round-trips through JSON unchanged`() {
        val json = writePerTestSnapshotJson(snapshot)
        assertEquals(snapshot, parsePerTestSnapshot(json))
    }

    @Test
    fun `an empty targets list (diff-derived) round-trips as empty, not null`() {
        val diffDerived = snapshot.copy(targets = emptyList())
        assertEquals(diffDerived, parsePerTestSnapshot(writePerTestSnapshotJson(diffDerived)))
    }

    @Test
    fun `malformed JSON returns null, not a crash`() {
        assertNull(parsePerTestSnapshot("{not json"))
    }

    @Test
    fun `well-formed JSON missing a required field returns null`() {
        assertNull(parsePerTestSnapshot("""{"warnings":[],"targets":[],"ranAtMs":1}"""))
    }
}
