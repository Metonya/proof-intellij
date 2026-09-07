package dev.proofjava.intellij.core.verdict

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** [writeMutationSnapshotJson]/[parseMutationSnapshot] round-trip - the `.proof/mutation-current.json` shape a mutation run writes and a project reopen reads back. Mirrors [PerTestSnapshotTest]. */
class MutationSnapshotTest {

    private val snapshot = MutationSnapshot(
        mutation = MutationBlock(
            engine = "pitest",
            engineVersion = "1.15.8",
            modules = listOf(
                MutationModuleEvidence(
                    id = "root",
                    methods = listOf(
                        MutatedMethod("Calculator", "square", "(I)I", 37, 37, listOf(Mutant("PrimitiveReturnsMutator", 37, "SURVIVED", emptyList()))),
                    ),
                ),
            ),
        ),
        warnings = listOf(Reason("MUTATION_TIMEOUT", "idle timeout reached", path = null, module = "root", count = null)),
        targets = listOf("dev.example.Calculator"),
        ranAtMs = 1_757_000_000_000L,
    )

    @Test
    fun `round-trips through JSON unchanged`() {
        val json = writeMutationSnapshotJson(snapshot)
        assertEquals(snapshot, parseMutationSnapshot(json))
    }

    @Test
    fun `an empty targets list (diff-derived) round-trips as empty, not null`() {
        val diffDerived = snapshot.copy(targets = emptyList())
        assertEquals(diffDerived, parseMutationSnapshot(writeMutationSnapshotJson(diffDerived)))
    }

    @Test
    fun `malformed JSON returns null, not a crash`() {
        assertNull(parseMutationSnapshot("{not json"))
    }

    @Test
    fun `well-formed JSON missing a required field returns null`() {
        assertNull(parseMutationSnapshot("""{"warnings":[],"targets":[],"ranAtMs":1}"""))
    }
}
