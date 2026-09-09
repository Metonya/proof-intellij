package dev.proofjava.intellij.engine.java.source

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Kotlin port of `proof-vscode/src/test/unit/model/classNameDetector.test.ts`. */
class ClassNameDetectorTest {

    @Test
    fun `a real package declaration is combined with the file base name`() {
        val source = "package dev.proofjava.playground;\n\npublic class Calculator {\n}\n"
        assertEquals("dev.proofjava.playground.Calculator", detectClassName(source, "Calculator"))
    }

    @Test
    fun `no package declaration falls back to the bare class name (default package)`() {
        assertEquals("Calculator", detectClassName("public class Calculator {}\n", "Calculator"))
    }

    @Test
    fun `leading blank lines and comments before the package declaration do not confuse detection`() {
        val source = "\n\n// a leading comment\npackage dev.proofjava.playground;\npublic class Calc {}\n"
        assertEquals("dev.proofjava.playground.Calc", detectClassName(source, "Calc"))
    }

    /** Same adversarial-input safety property SonarQube flagged on the TS regex (typescript:S5852) - proven here against a genuinely adversarial input, not just asserted from the pattern. */
    @Test
    fun `a large file with many non-matching package-shaped lines is still handled in linear time`() {
        val adversarialLine = "package " + "a.".repeat(2000) + "a \n"
        val source = adversarialLine.repeat(500) + "package dev.proofjava.playground;\n"

        val start = System.currentTimeMillis()
        val result = detectClassName(source, "Calculator")
        val elapsedMs = System.currentTimeMillis() - start

        assertEquals("dev.proofjava.playground.Calculator", result)
        assertTrue(elapsedMs < 1000, "expected linear-time matching, took ${elapsedMs}ms")
    }
}
