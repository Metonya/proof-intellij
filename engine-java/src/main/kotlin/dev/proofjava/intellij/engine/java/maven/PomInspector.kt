package dev.proofjava.intellij.engine.java.maven

/**
 * Port of `proof-vscode/src/cli/pomInspector.ts`: answers two yes/no
 * questions about a pom.xml's raw text, needed before this engine can
 * offer to run tests on the user's behalf. Targeted regex/line scanning,
 * not a real XML parser - three narrow questions do not justify a new
 * dependency, and a false negative here only means an extra warning shown
 * or an extra goal injected, never a silent wrong answer.
 */

data class LiteralArgLine(val line: Int, val text: String)

data class PomFacts(
    /** Does this pom declare `jacoco-maven-plugin` as a `<plugin>`? If so, its own build already knows how to produce a report - don't inject the CLI goals on top of it. */
    val hasJacocoPlugin: Boolean,
    /** A literal (non-`@{argLine}`/`${argLine}`) `<argLine>` value, if this pom has one - the exact shape that clobbers a command-line-injected JaCoCo agent property. `null` when no `<argLine>` exists, or the one that does already forwards the user property correctly. */
    val literalArgLine: LiteralArgLine?,
)

private val JACOCO_PLUGIN_PATTERN = Regex("""<artifactId>\s*jacoco-maven-plugin\s*</artifactId>""")
private val ARG_LINE_PATTERN = Regex("""<argLine>([^<]*)</argLine>""")

fun inspectPom(pomXml: String): PomFacts = PomFacts(
    hasJacocoPlugin = JACOCO_PLUGIN_PATTERN.containsMatchIn(pomXml),
    literalArgLine = findLiteralArgLine(pomXml),
)

private fun findLiteralArgLine(pomXml: String): LiteralArgLine? {
    val lines = pomXml.split(Regex("""\r?\n"""))
    for (i in lines.indices) {
        val match = ARG_LINE_PATTERN.find(lines[i]) ?: continue
        val value = match.groupValues[1]
        if (!value.contains("@{argLine}") && !value.contains("\${argLine}")) {
            return LiteralArgLine(i + 1, lines[i].trim())
        }
    }
    return null
}
