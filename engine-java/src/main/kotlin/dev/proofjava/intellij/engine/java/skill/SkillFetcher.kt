package dev.proofjava.intellij.engine.java.skill

import com.google.gson.JsonObject
import com.intellij.openapi.progress.ProgressIndicator
import dev.proofjava.intellij.engine.java.net.arrayField
import dev.proofjava.intellij.engine.java.net.githubGetBytes
import dev.proofjava.intellij.engine.java.net.githubGetText
import dev.proofjava.intellij.engine.java.net.parseJsonObject
import dev.proofjava.intellij.engine.java.net.stringField

private const val SKILL_REPO = "Metonya/proof-java"
private const val SKILL_BRANCH = "main"
private const val SKILL_ROOT = "skills/proof-java"

/** Relative to `skills/proof-java/` itself - e.g. `SKILL.md`, `reference/rules.md`. */
data class SkillFile(val relativePath: String, val content: ByteArray)

/**
 * Port of `proof-vscode`'s `cli/skillFetcher.ts` - fetches the *current*
 * `skills/proof-java/` tree from proof-java's own GitHub repo at install
 * time (never bundled/cached locally), so a user fixing a `SKILL.md` typo
 * upstream doesn't require every IntelliJ user to update this plugin too -
 * same reasoning the TS source gives. The Git Trees API (one call,
 * `recursive=1`) lists every path in the repo at [SKILL_BRANCH] in one
 * shot - cheaper and more robust to the skill's own file layout changing
 * than walking the Contents API directory by directory.
 */
fun fetchSkillFiles(
    indicator: ProgressIndicator? = null,
    fetchText: (String) -> String = { githubGetText(it, indicator) },
    fetchBytes: (String) -> ByteArray = { githubGetBytes(it, indicator) },
): List<SkillFile> {
    val treeUrl = "https://api.github.com/repos/$SKILL_REPO/git/trees/$SKILL_BRANCH?recursive=1"
    val tree = parseJsonObject(fetchText(treeUrl))
    val entries = tree.arrayField("tree")
        ?: throw IllegalStateException("unexpected response shape from the GitHub API (no \"tree\" array)")

    val paths = entries.mapNotNull { it as? JsonObject }
        .filter { it.stringField("type") == "blob" && (it.stringField("path") ?: "").startsWith("$SKILL_ROOT/") }
        .mapNotNull { it.stringField("path") }
    check(paths.isNotEmpty()) { "no files found under \"$SKILL_ROOT\" in $SKILL_REPO@$SKILL_BRANCH - the skill may have moved" }

    return paths.map { fullPath ->
        val relativePath = fullPath.removePrefix("$SKILL_ROOT/")
        val content = fetchBytes("https://raw.githubusercontent.com/$SKILL_REPO/$SKILL_BRANCH/$fullPath")
        SkillFile(relativePath, content)
    }
}
