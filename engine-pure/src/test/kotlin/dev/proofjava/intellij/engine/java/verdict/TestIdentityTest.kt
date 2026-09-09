package dev.proofjava.intellij.engine.java.verdict

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Kotlin port of `proof-vscode/src/test/unit/verdict/testIdentity.test.ts`. */
class TestIdentityTest {

    @Test
    fun `a JUnit5 UniqueId string is parsed into class and method, display uses the short class name`() {
        val raw = "[engine:junit-jupiter]/[class:dev.proofjava.playground.CalcTest]/[method:addsTwoNumbers()]"
        val result = parseTestIdentity(raw)
        assertEquals("dev.proofjava.playground.CalcTest", result.className)
        assertEquals("CalcTest", result.simpleClassName)
        assertEquals("addsTwoNumbers", result.methodName)
        assertNull(result.invocation)
        assertEquals("CalcTest#addsTwoNumbers()", result.display)
    }

    /**
     * The real regression this exists for - a real `@ParameterizedTest`
     * invocation id (captured from a live --per-test-target run). The
     * original parser only looked for `[method:]`, so this fell through to
     * the `#`-splitting fallback and found the `#1` inside
     * `[test-template-invocation:#1]`, mis-parsing the whole UniqueId.
     */
    @Test
    fun `a JUnit5 test-template-invocation (parameterized test) id is parsed, not mis-split on its own hash`() {
        val raw = "dev.proofjava.playground.CalculatorParameterizedTest.[engine:junit-jupiter]/" +
            "[class:dev.proofjava.playground.CalculatorParameterizedTest]/" +
            "[test-template:addProducesTheSumForEveryPair(int, int, int)]/[test-template-invocation:#1]"
        val result = parseTestIdentity(raw)
        assertEquals("dev.proofjava.playground.CalculatorParameterizedTest", result.className)
        assertEquals("CalculatorParameterizedTest", result.simpleClassName)
        assertEquals("addProducesTheSumForEveryPair", result.methodName)
        assertEquals("1", result.invocation)
        assertEquals("CalculatorParameterizedTest#addProducesTheSumForEveryPair() #1", result.display)
    }

    @Test
    fun `a test-template id with no invocation segment still parses the method, invocation stays null`() {
        val raw = "[class:dev.proofjava.playground.CalculatorParameterizedTest]/[test-template:addProducesTheSumForEveryPair(int, int, int)]"
        val result = parseTestIdentity(raw)
        assertEquals("addProducesTheSumForEveryPair", result.methodName)
        assertNull(result.invocation)
        assertEquals("CalculatorParameterizedTest#addProducesTheSumForEveryPair()", result.display)
    }

    @Test
    fun `a Class#method() shape is parsed directly, display uses the short class name`() {
        val result = parseTestIdentity("dev.proofjava.playground.CalcTest#addsTwoNumbers()")
        assertEquals("dev.proofjava.playground.CalcTest", result.className)
        assertEquals("CalcTest", result.simpleClassName)
        assertEquals("addsTwoNumbers", result.methodName)
        assertEquals("CalcTest#addsTwoNumbers()", result.display)
    }

    @Test
    fun `a Class#method() shape with no package keeps the bare class name`() {
        val result = parseTestIdentity("CalcTest#addsTwoNumbers()")
        assertEquals("CalcTest", result.className)
        assertEquals("CalcTest", result.simpleClassName)
    }

    @Test
    fun `an unrecognized shape is shown verbatim rather than guessed at`() {
        val raw = "SomeWeirdEngine::totallyUnknownFormat"
        val result = parseTestIdentity(raw)
        assertNull(result.className)
        assertNull(result.methodName)
        assertNull(result.simpleClassName)
        assertNull(result.invocation)
        assertEquals(raw, result.display)
    }

    @Test
    fun `a class UniqueId with neither method nor test-template falls back to the raw id`() {
        val raw = "[engine:junit-jupiter]/[class:dev.proofjava.playground.CalcTest]"
        val result = parseTestIdentity(raw)
        assertNull(result.className)
        assertEquals(raw, result.display)
    }
}
