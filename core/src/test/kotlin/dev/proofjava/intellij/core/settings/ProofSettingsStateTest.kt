package dev.proofjava.intellij.core.settings

import dev.proofjava.intellij.core.cli.DiffMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProofSettingsStateTest {

    @Test
    fun `defaults to uncommitted`() {
        assertEquals(DiffMode.Uncommitted, ProofSettingsState().toDiffMode())
    }

    @Test
    fun `no-vcs maps to DiffMode_NoVcs`() {
        val settings = ProofSettingsState().apply { diffMode = ProofSettingsState.DIFF_MODE_NO_VCS }
        assertEquals(DiffMode.NoVcs, settings.toDiffMode())
    }

    @Test
    fun `base with a ref maps to DiffMode_Base of that ref`() {
        val settings = ProofSettingsState().apply {
            diffMode = ProofSettingsState.DIFF_MODE_BASE
            baseRef = "origin/main"
        }
        assertEquals(DiffMode.Base("origin/main"), settings.toDiffMode())
    }

    @Test
    fun `base with a blank ref falls back to main rather than an empty ref`() {
        val settings = ProofSettingsState().apply { diffMode = ProofSettingsState.DIFF_MODE_BASE }
        assertEquals(DiffMode.Base("main"), settings.toDiffMode())
    }

    @Test
    fun `blank jarPath means auto-detect, not a literal blank path`() {
        val settings = ProofSettingsState().apply { jarPath = "   " }
        assertEquals(null, settings.jarPath)
    }
}
