package dev.proofjava.intellij.engine.java.skill

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * [SKILL_TARGETS]'s own `resolveDir` closures - the destination each target
 * writes to, mirroring `skillInstaller.test.ts`'s own coverage of
 * `resolveDir` (the part with no `vscode` dependency).
 */
class SkillTargetsTest {

    private fun target(id: String) = SKILL_TARGETS.first { it.id == id }

    @Test
    fun `claude code workspace scope resolves under the workspace root`() {
        val dir = target("claude").resolveDir(SkillScope.WORKSPACE, "/repo")
        assertEquals(File(File(File("/repo", ".claude"), "skills"), "proof-java").path, dir)
    }

    @Test
    fun `claude code user scope resolves under the user home, ignoring workspace root`() {
        val dir = target("claude").resolveDir(SkillScope.USER, "/repo")
        assertTrue(dir.endsWith(File(File(File("", ".claude"), "skills"), "proof-java").path.removePrefix(File.separator)))
        assertTrue(dir.startsWith(userHome()))
    }

    @Test
    fun `windsurf only supports workspace scope`() {
        assertEquals(listOf(SkillScope.WORKSPACE), target("windsurf").scopes)
        val dir = target("windsurf").resolveDir(SkillScope.WORKSPACE, "/repo")
        assertEquals(File(File(File("/repo", ".windsurf"), "skills"), "proof-java").path, dir)
    }

    @Test
    fun `antigravity only supports user scope, under gemini antigravity-cli`() {
        assertEquals(listOf(SkillScope.USER), target("antigravity").scopes)
        val dir = target("antigravity").resolveDir(SkillScope.USER, "/repo")
        assertTrue(dir.startsWith(userHome()))
        assertTrue(dir.endsWith(File(File(File(File("", ".gemini"), "antigravity-cli"), "skills"), "proof-java").path.removePrefix(File.separator)))
    }

    @Test
    fun `portable target resolves to dot-agents-skills under the workspace root`() {
        assertEquals(listOf(SkillScope.WORKSPACE), target("portable").scopes)
        val dir = target("portable").resolveDir(SkillScope.WORKSPACE, "/repo")
        assertEquals(File(File(File("/repo", ".agents"), "skills"), "proof-java").path, dir)
    }

    @Test
    fun `all four targets are present with unique ids`() {
        assertEquals(listOf("claude", "windsurf", "antigravity", "portable"), SKILL_TARGETS.map { it.id })
    }
}
