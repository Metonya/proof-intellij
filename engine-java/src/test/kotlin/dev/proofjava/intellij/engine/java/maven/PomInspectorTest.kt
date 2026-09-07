package dev.proofjava.intellij.engine.java.maven

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Kotlin port of `proof-vscode/src/test/unit/cli/pomInspector.test.ts`. */
class PomInspectorTest {

    @Test
    fun `detects a declared jacoco-maven-plugin`() {
        val pom = "<project><build><plugins><plugin><groupId>org.jacoco</groupId><artifactId>jacoco-maven-plugin</artifactId></plugin></plugins></build></project>"
        assertTrue(inspectPom(pom).hasJacocoPlugin)
    }

    @Test
    fun `a pom with no jacoco plugin at all`() {
        val pom = "<project><build><plugins><plugin><artifactId>maven-surefire-plugin</artifactId></plugin></plugins></build></project>"
        assertFalse(inspectPom(pom).hasJacocoPlugin)
    }

    @Test
    fun `a literal argLine (no @argLine) is reported with its line number`() {
        val pom = listOf(
            "<project>",
            "  <build>",
            "    <plugins>",
            "      <plugin>",
            "        <artifactId>maven-surefire-plugin</artifactId>",
            "        <configuration>",
            "          <argLine>--illegal-access=deny</argLine>",
            "        </configuration>",
            "      </plugin>",
            "    </plugins>",
            "  </build>",
            "</project>",
        ).joinToString("\n")
        val facts = inspectPom(pom)
        assertEquals(7, facts.literalArgLine?.line)
        assertTrue(facts.literalArgLine?.text?.contains("--illegal-access=deny") == true)
    }

    @Test
    fun `an argLine that already forwards the at-form is not reported as a problem`() {
        val pom = "<project><argLine>@{argLine} --illegal-access=deny</argLine></project>"
        assertNull(inspectPom(pom).literalArgLine)
    }

    @Test
    fun `an argLine using the dollar form is also accepted`() {
        val pom = "<project><argLine>\${argLine} -Xmx512m</argLine></project>"
        assertNull(inspectPom(pom).literalArgLine)
    }

    @Test
    fun `no argLine at all is not a problem`() {
        assertNull(inspectPom("<project><build></build></project>").literalArgLine)
    }

    @Test
    fun `the FIRST literal argLine found is reported, not the last`() {
        val pom = listOf(
            "<project>",
            "  <argLine>first --literal</argLine>",
            "  <argLine>@{argLine} second</argLine>",
            "</project>",
        ).joinToString("\n")
        assertEquals(2, inspectPom(pom).literalArgLine?.line)
    }
}
