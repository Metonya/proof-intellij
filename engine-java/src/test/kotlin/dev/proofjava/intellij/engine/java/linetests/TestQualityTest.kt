package dev.proofjava.intellij.engine.java.linetests

import dev.proofjava.intellij.core.verdict.Confidence
import dev.proofjava.intellij.core.verdict.Finding
import dev.proofjava.intellij.core.verdict.RuleId
import dev.proofjava.intellij.core.verdict.Severity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Kotlin port of `proof-vscode/src/test/unit/model/testQuality.test.ts`.
 * Real data captured from a live `--per-test-target` run against
 * proof-java-playground's `Calculator.java`, not synthesized.
 */
class TestQualityTest {

    private val squareHasNoAssertion = "[engine:junit-jupiter]/[class:dev.proofjava.playground.CalculatorPseudoTestedTest]/[method:squareHasNoAssertion()]"
    private val subtractHasNoAssertion = "[engine:junit-jupiter]/[class:dev.proofjava.playground.CalculatorNoOracleTest]/[method:subtractHasNoAssertion()]"
    private val divideAndMultiplyWide = "[engine:junit-jupiter]/[class:dev.proofjava.playground.CalculatorSubsumedTest]/[method:divideAndMultiplyWide()]"
    private val multiplyConstantVsConstant = "[engine:junit-jupiter]/[class:dev.proofjava.playground.CalculatorTautologicalOracleTest]/[method:multiplyConstantVsConstant()]"
    private val multiplyLiteralBoolean = "[engine:junit-jupiter]/[class:dev.proofjava.playground.CalculatorTautologicalOracleTest]/[method:multiplyLiteralBoolean()]"
    private val addWorksCorrectly = "[engine:junit-jupiter]/[class:dev.proofjava.playground.CalculatorGoodTest]/[method:addWorksCorrectly()]"
    private val addParamInvocation1 = "[class:dev.proofjava.playground.CalculatorParameterizedTest]/[test-template:addProducesTheSumForEveryPair(int, int, int)]/[test-template-invocation:#1]"
    private val addCheckedViaSoftAssertions = "[engine:junit-jupiter]/[class:dev.proofjava.playground.CalculatorUnresolvedOracleTest]/[method:addCheckedViaLocalSoftAssertions()]"

    private fun finding(rule: RuleId, confidence: Confidence, testMethod: String) = Finding(
        rule = rule, severity = Severity.WARNING, confidence = confidence, module = "root", path = "src/test/java/X.java",
        startLine = 1, endLine = 1, message = "m", suggestedAction = "a", fingerprint = rule.name + testMethod, testMethod = testMethod,
    )

    private val findings = listOf(
        finding(RuleId.NO_RECOGNIZED_ORACLE, Confidence.HIGH, "dev.proofjava.playground.CalculatorPseudoTestedTest#squareHasNoAssertion()"),
        finding(RuleId.NO_RECOGNIZED_ORACLE, Confidence.HIGH, "dev.proofjava.playground.CalculatorNoOracleTest#subtractHasNoAssertion()"),
        finding(RuleId.TAUTOLOGICAL_ORACLE, Confidence.HIGH, "dev.proofjava.playground.CalculatorTautologicalOracleTest#multiplyConstantVsConstant()"),
        finding(RuleId.TAUTOLOGICAL_ORACLE, Confidence.HIGH, "dev.proofjava.playground.CalculatorTautologicalOracleTest#multiplyLiteralBoolean()"),
        finding(RuleId.NO_RECOGNIZED_ORACLE, Confidence.INCONCLUSIVE, "dev.proofjava.playground.CalculatorUnresolvedOracleTest#addCheckedViaLocalSoftAssertions()"),
    )

    @Test
    fun `a test with a real NO_RECOGNIZED_ORACLE finding is noOracle`() {
        val index = indexFindingsByTestMethod(findings)
        assertEquals(TestVerdict.NO_ORACLE, classifyTest(squareHasNoAssertion, index))
    }

    @Test
    fun `the same rule at INCONCLUSIVE confidence is a distinct verdict, not noOracle`() {
        val index = indexFindingsByTestMethod(findings)
        assertEquals(TestVerdict.INCONCLUSIVE, classifyTest(addCheckedViaSoftAssertions, index))
    }

    @Test
    fun `a test with no finding at all is ok`() {
        val index = indexFindingsByTestMethod(findings)
        assertEquals(TestVerdict.OK, classifyTest(divideAndMultiplyWide, index))
        assertEquals(TestVerdict.OK, classifyTest(addWorksCorrectly, index))
    }

    @Test
    fun `line 37 - the single covering test has no oracle - false green`() {
        val index = indexFindingsByTestMethod(findings)
        val quality = lineQuality(listOf(squareHasNoAssertion), index)
        assertTrue(quality.isFalseGreen)
        assertEquals(1, quality.byVerdict[TestVerdict.NO_ORACLE])
    }

    @Test
    fun `line 11 - same shape, different rule - also false green`() {
        val index = indexFindingsByTestMethod(findings)
        assertTrue(lineQuality(listOf(subtractHasNoAssertion), index).isFalseGreen)
    }

    @Test
    fun `line 15 - two of three tests are TAUTOLOGICAL_ORACLE, but the third has a real oracle - not false green`() {
        val index = indexFindingsByTestMethod(findings)
        val quality = lineQuality(listOf(divideAndMultiplyWide, multiplyConstantVsConstant, multiplyLiteralBoolean), index)
        assertFalse(quality.isFalseGreen)
        assertEquals(1, quality.byVerdict[TestVerdict.OK])
        assertEquals(2, quality.byVerdict[TestVerdict.NO_ORACLE])
    }

    /** One covering test is NO_RECOGNIZED_ORACLE but INCONCLUSIVE - every other test on the line also happens to have no finding (ok), so the line is not false green either way, but the inconclusive test must count as its own verdict. */
    @Test
    fun `line 7 - an INCONCLUSIVE test is tracked as its own verdict, not counted as noOracle`() {
        val index = indexFindingsByTestMethod(findings)
        val quality = lineQuality(listOf(addWorksCorrectly, addParamInvocation1, addCheckedViaSoftAssertions), index)
        assertFalse(quality.isFalseGreen)
        assertEquals(1, quality.byVerdict[TestVerdict.INCONCLUSIVE])
        assertEquals(0, quality.byVerdict[TestVerdict.NO_ORACLE])
        assertEquals(2, quality.byVerdict[TestVerdict.OK])
    }

    @Test
    fun `a line covered by zero tests is not false green`() {
        val index = indexFindingsByTestMethod(findings)
        assertFalse(lineQuality(emptyList(), index).isFalseGreen)
    }

    @Test
    fun `a line where every test is inconclusive is not false green`() {
        val index = indexFindingsByTestMethod(findings)
        assertFalse(lineQuality(listOf(addCheckedViaSoftAssertions), index).isFalseGreen)
    }
}
