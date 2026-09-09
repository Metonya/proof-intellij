package dev.proofjava.intellij.engine.java.skill

import java.io.File

enum class SkillScope { WORKSPACE, USER }

private const val SKILL_DIR_NAME = "proof-java"

data class SkillTarget(
    val id: String,
    val label: String,
    val description: String,
    val scopes: List<SkillScope>,
    val resolveDir: (SkillScope, String) -> String,
)

/**
 * Port of `proof-vscode`'s `ui/skillInstaller.ts` `SKILL_TARGETS` - four
 * targets, each with the scope(s) that tool actually supports, verified
 * against each tool's own current docs/convention (not guessed), same as
 * the TS source:
 *   - Claude Code: `.claude/skills/` (workspace, committed) or
 *     `~/.claude/skills/` (user, every project on this machine).
 *   - Windsurf: `.windsurf/skills/` - workspace only, no documented
 *     user-level equivalent found.
 *   - Antigravity: `~/.gemini/antigravity-cli/skills/` - user only, no
 *     documented workspace-level equivalent found.
 *   - Portable: `.agents/skills/` - the shared convention Cursor, OpenAI
 *     Codex CLI, Gemini CLI, and GitHub Copilot all read directly, so one
 *     install covers all four without duplicating files per tool.
 */
val SKILL_TARGETS: List<SkillTarget> = listOf(
    SkillTarget(
        id = "claude",
        label = "Claude Code",
        description = ".claude/skills/proof-java (workspace) or ~/.claude/skills/proof-java (user)",
        scopes = listOf(SkillScope.WORKSPACE, SkillScope.USER),
        resolveDir = { scope, workspaceRoot ->
            if (scope == SkillScope.WORKSPACE) {
                File(File(File(workspaceRoot, ".claude"), "skills"), SKILL_DIR_NAME).path
            } else {
                File(File(File(userHome(), ".claude"), "skills"), SKILL_DIR_NAME).path
            }
        },
    ),
    SkillTarget(
        id = "windsurf",
        label = "Windsurf",
        description = ".windsurf/skills/$SKILL_DIR_NAME (workspace)",
        scopes = listOf(SkillScope.WORKSPACE),
        resolveDir = { _, workspaceRoot -> File(File(File(workspaceRoot, ".windsurf"), "skills"), SKILL_DIR_NAME).path },
    ),
    SkillTarget(
        id = "antigravity",
        label = "Antigravity",
        description = "~/.gemini/antigravity-cli/skills/$SKILL_DIR_NAME (user)",
        scopes = listOf(SkillScope.USER),
        resolveDir = { _, _ -> File(File(File(File(userHome(), ".gemini"), "antigravity-cli"), "skills"), SKILL_DIR_NAME).path },
    ),
    SkillTarget(
        id = "portable",
        label = "Portable (.agents/skills)",
        description = ".agents/skills/$SKILL_DIR_NAME (workspace) - read directly by Cursor, Codex CLI, Gemini CLI, and GitHub Copilot",
        scopes = listOf(SkillScope.WORKSPACE),
        resolveDir = { _, workspaceRoot -> File(File(File(workspaceRoot, ".agents"), "skills"), SKILL_DIR_NAME).path },
    ),
)

internal fun userHome(): String = System.getProperty("user.home")
