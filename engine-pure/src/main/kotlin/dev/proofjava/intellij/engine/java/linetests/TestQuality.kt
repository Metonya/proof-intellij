package dev.proofjava.intellij.engine.java.linetests

import dev.proofjava.intellij.core.verdict.Confidence
import dev.proofjava.intellij.core.verdict.Finding
import dev.proofjava.intellij.core.verdict.RuleId
import dev.proofjava.intellij.engine.java.verdict.parseTestIdentity

/**
 * Port of `proof-vscode/src/model/testQuality.ts` - the join proof-vscode
 * never made until Faz 15a: `findings[].testMethod` (shape
 * `FQCN#method()`) and `perTest`'s test ids (JUnit5 UniqueId or the same
 * `FQCN#method()` shape) refer to the exact same test. Without this join,
 * a line covered only by a test with no assertion looks identical to a
 * line covered by a real test - proof-java's entire reason to exist over
 * plain JaCoCo, invisible without it.
 */
enum class TestVerdict { OK, NO_ORACLE, WEAK, REDUNDANT, INCONCLUSIVE }

/** Keyed by `finding.testMethod` (already `FQCN#method()`) - only findings the CLI itself anchored on a test method can be joined to a test id. */
fun indexFindingsByTestMethod(findings: List<Finding>): Map<String, Finding> {
    val index = mutableMapOf<String, Finding>()
    for (finding in findings) {
        finding.testMethod?.let { index[it] = finding }
    }
    return index
}

/**
 * [rawTestId] is parsed to the same `Class#method()` key `finding.testMethod`
 * already uses, then matched. No match -> [TestVerdict.OK] (hard rule 3a:
 * absence of a finding is not itself evidence of quality, but L0 already
 * looked and found nothing wrong). [TestVerdict.INCONCLUSIVE] is kept
 * distinct from a real [TestVerdict.NO_ORACLE] - a test the scanner could
 * not resolve is not the same claim as a test with no oracle at all.
 */
fun classifyTest(rawTestId: String, findingsByTestMethod: Map<String, Finding>): TestVerdict {
    val identity = parseTestIdentity(rawTestId)
    val className = identity.className ?: return TestVerdict.OK
    val methodName = identity.methodName ?: return TestVerdict.OK
    val finding = findingsByTestMethod["$className#$methodName()"] ?: return TestVerdict.OK
    if (finding.confidence == Confidence.INCONCLUSIVE) {
        return TestVerdict.INCONCLUSIVE
    }
    return when (finding.rule) {
        RuleId.NO_RECOGNIZED_ORACLE, RuleId.TAUTOLOGICAL_ORACLE, RuleId.CATCH_ORACLE_WITHOUT_FAIL -> TestVerdict.NO_ORACLE
        RuleId.NULL_CHECK_ONLY -> TestVerdict.WEAK
        RuleId.SUBSUMED_TEST -> TestVerdict.REDUNDANT
        // Anchored on the production method (mutation evidence), not a single
        // test's oracle quality - has nothing to say about which of the
        // covering tests is weak, so it does not downgrade any of them.
        RuleId.PSEUDO_TESTED_METHOD -> TestVerdict.OK
    }
}

data class TestQualityRef(val rawTestId: String, val verdict: TestVerdict, val finding: Finding?)

data class LineQuality(
    val tests: List<TestQualityRef>,
    val byVerdict: Map<TestVerdict, Int>,
    /**
     * True only when at least one test covers the line AND every one of
     * them is [TestVerdict.NO_ORACLE] - a JaCoCo-green line that no test
     * actually verifies anything on. A single INCONCLUSIVE test blocks
     * this (the scanner could not confirm no-oracle, so neither can this
     * line), same for OK/WEAK/REDUNDANT - any test that did assert
     * something, however imperfectly, is enough to not call the line a
     * false green.
     */
    val isFalseGreen: Boolean,
)

fun lineQuality(rawTestIds: List<String>, findingsByTestMethod: Map<String, Finding>): LineQuality {
    val byVerdict = TestVerdict.entries.associateWith { 0 }.toMutableMap()
    val tests = mutableListOf<TestQualityRef>()
    for (rawTestId in rawTestIds) {
        val identity = parseTestIdentity(rawTestId)
        val finding = if (identity.className != null && identity.methodName != null) {
            findingsByTestMethod["${identity.className}#${identity.methodName}()"]
        } else {
            null
        }
        val verdict = classifyTest(rawTestId, findingsByTestMethod)
        byVerdict[verdict] = (byVerdict[verdict] ?: 0) + 1
        tests += TestQualityRef(rawTestId, verdict, finding)
    }
    val isFalseGreen = tests.isNotEmpty() && byVerdict[TestVerdict.NO_ORACLE] == tests.size
    return LineQuality(tests, byVerdict, isFalseGreen)
}
