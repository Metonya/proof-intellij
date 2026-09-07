package dev.proofjava.intellij.core.model

import dev.proofjava.intellij.core.verdict.LineTuple
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Kotlin port of `proof-vscode/src/test/unit/verdict/coverageMapping.test.ts`
 * and `classifyLine.test.ts` - this classification is the only thing the
 * gutter paints from, so this test locks the contract the same way the TS
 * one does.
 */
class LineClassificationTest {

    private fun tuple(line: Int, mi: Int, ci: Int, mb: Int, cb: Int) = LineTuple(line, mi, ci, mb, cb)

    @Test
    fun `a fully covered line has no partial flag`() {
        val line = mapLines(listOf(tuple(14, 0, 3, 0, 0)))[0]
        assertTrue(line.executed)
        assertFalse(line.partial)
    }

    @Test
    fun `an uncovered line is not executed and not partial`() {
        val line = mapLines(listOf(tuple(14, 2, 0, 0, 0)))[0]
        assertFalse(line.executed)
        assertFalse(line.partial)
    }

    @Test
    fun `a real missed branch marks an executed line partial`() {
        val line = mapLines(listOf(tuple(14, 0, 2, 1, 1)))[0]
        assertTrue(line.executed)
        assertTrue(line.partial)
    }

    @Test
    fun `a partial instruction count with no branch data still marks the line partial`() {
        val line = mapLines(listOf(tuple(14, 2, 3, 0, 0)))[0]
        assertTrue(line.executed)
        assertTrue(line.partial)
    }

    @Test
    fun `fully covered instructions and branches classify as not partial`() {
        val line = mapLines(listOf(tuple(14, 0, 5, 0, 3)))[0]
        assertTrue(line.executed)
        assertFalse(line.partial)
    }

    @Test
    fun `a fully covered line with no branch data classifies as covered`() {
        assertEquals(LineState.COVERED, classifyLine(mapLines(listOf(tuple(14, 0, 3, 0, 0)))[0]))
    }

    @Test
    fun `an unexecuted line classifies as uncovered regardless of branch data`() {
        assertEquals(LineState.UNCOVERED, classifyLine(mapLines(listOf(tuple(14, 2, 0, 1, 0)))[0]))
    }

    @Test
    fun `an executed line with a real missed branch classifies as partial`() {
        assertEquals(LineState.PARTIAL, classifyLine(mapLines(listOf(tuple(14, 0, 2, 1, 1)))[0]))
    }

    @Test
    fun `an executed line with only covered real branches classifies as covered`() {
        assertEquals(LineState.COVERED, classifyLine(mapLines(listOf(tuple(14, 0, 2, 0, 2)))[0]))
    }

    @Test
    fun `a partial-instruction line with no real branches classifies as partial`() {
        assertEquals(LineState.PARTIAL, classifyLine(mapLines(listOf(tuple(14, 2, 3, 0, 0)))[0]))
    }
}
