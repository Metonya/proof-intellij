package dev.proofjava.intellij.engine.java.hover

import dev.proofjava.intellij.core.model.CoverageState
import dev.proofjava.intellij.core.verdict.Confidence
import dev.proofjava.intellij.core.verdict.Finding
import dev.proofjava.intellij.core.verdict.FileCoverageBlock
import dev.proofjava.intellij.core.verdict.FileCoverageEntry
import dev.proofjava.intellij.core.verdict.Metric
import dev.proofjava.intellij.core.verdict.MetricSet
import dev.proofjava.intellij.core.verdict.ModuleInput
import dev.proofjava.intellij.core.verdict.NewCodeCoverage
import dev.proofjava.intellij.core.verdict.PerTestBlock
import dev.proofjava.intellij.core.verdict.PerTestEntry
import dev.proofjava.intellij.core.verdict.PerTestLine
import dev.proofjava.intellij.core.verdict.PerTestModuleEvidence
import dev.proofjava.intellij.core.verdict.RuleId
import dev.proofjava.intellij.core.verdict.Severity
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Fixture data straight from `proof-vscode/src/test/integration/hoverProvider.test.ts`
 * (real shape from a live `--per-test-target` run, Faz 15/21 sessions) -
 * same 3 scenarios that test covers, adapted to call [computeHover]
 * directly instead of going through a real editor/VS Code command, since
 * this file's whole point is that [computeHover] itself needs no SDK to
 * test. None of the 3 scenarios below actually reach a real filesystem
 * check: the covering test's own [Finding] always carries its `path`
 * already, so `locateTestFile` returns it without ever probing disk (see
 * `TestFileLocator.kt`'s own short-circuit) - no `@TempDir` needed here.
 */
class HoverContentTest {

    private val metric = Metric("num", 1, "den", 2, 100.0)
    private val metricSet = MetricSet(metric, metric, metric)

    private val perTest = PerTestBlock(
        engine = "pitest",
        engineVersion = "1.15.8",
        modules = listOf(
            PerTestModuleEvidence(
                id = "root",
                entries = listOf(
                    PerTestEntry(
                        className = "dev.proofjava.playground.Calculator",
                        methodName = "square",
                        lines = listOf(PerTestLine(37, listOf("[class:dev.proofjava.playground.CalculatorPseudoTestedTest]/[method:squareHasNoAssertion()]"))),
                    ),
                ),
                ambient = emptyList(),
            ),
        ),
    )

    private val findings = listOf(
        Finding(
            rule = RuleId.NO_RECOGNIZED_ORACLE, severity = Severity.WARNING, confidence = Confidence.HIGH,
            module = "root", path = "src/test/java/dev/proofjava/playground/CalculatorPseudoTestedTest.java",
            startLine = 16, endLine = 16, message = "no oracle", suggestedAction = "add one", fingerprint = "f1",
            testMethod = "dev.proofjava.playground.CalculatorPseudoTestedTest#squareHasNoAssertion()",
        ),
    )

    private val modules = listOf(ModuleInput(id = "root", root = ".", sourceRoots = listOf("src/main/java"), testRoots = listOf("src/test/java")))

    private fun state(fileCoverage: FileCoverageBlock? = null) = CoverageState(
        projectRoot = "/repo",
        fileCoverage = fileCoverage,
        overall = metricSet,
        newCode = NewCodeCoverage.Status("unavailable_no_vcs"),
        changedFiles = emptyList(),
        findings = findings,
        warnings = emptyList(),
        modules = modules,
    )

    /** A real file's worth of padding so line 37 is a plausible position - mirrors the TS test's own `openPaddedJavaFile`. */
    private val paddedCalculatorSource = "package dev.proofjava.playground;\n\npublic class Calculator {\n" + "    // padding\n".repeat(40) + "}\n"

    @Test
    fun `a false-green production line hover names the missing oracle`() {
        val content = computeHover(
            coverageState = state(),
            perTest = perTest,
            fileText = paddedCalculatorSource,
            fileBaseNameWithoutExtension = "Calculator",
            repoRelativePath = "src/main/java/dev/proofjava/playground/Calculator.java",
            lineNumber = 37,
            wordAtCursor = null,
        )
        assertTrue(content != null, "expected a hover on the false-green line")
        assertTrue(content!!.html.contains("oracle", ignoreCase = true))
        assertTrue(content.html.contains("squareHasNoAssertion"))
    }

    @Test
    fun `a line with no per-test evidence at all produces no hover`() {
        val content = computeHover(
            coverageState = state(),
            perTest = perTest,
            fileText = paddedCalculatorSource,
            fileBaseNameWithoutExtension = "Calculator",
            repoRelativePath = "src/main/java/dev/proofjava/playground/Calculator.java",
            lineNumber = 10,
            wordAtCursor = null,
        )
        assertNull(content)
    }

    @Test
    fun `a test file under testRoots gets the reverse hover, not the production one`() {
        val realPerTest = PerTestBlock(
            engine = "pitest",
            engineVersion = "1.15.8",
            modules = listOf(
                PerTestModuleEvidence(
                    id = "root",
                    entries = listOf(
                        PerTestEntry(
                            className = "dev.proofjava.playground.Calculator",
                            methodName = "square",
                            lines = listOf(PerTestLine(37, listOf("dev.proofjava.playground.CalculatorPseudoTestedTest.[engine:junit-jupiter]/[class:dev.proofjava.playground.CalculatorPseudoTestedTest]/[method:squareHasNoAssertion()]"))),
                        ),
                        // Real data: the test class covers its own lines too - must be filtered by the production-class check, not shown as a "production line".
                        PerTestEntry(
                            className = "dev.proofjava.playground.CalculatorPseudoTestedTest",
                            methodName = "squareHasNoAssertion",
                            lines = listOf(PerTestLine(4, listOf("dev.proofjava.playground.CalculatorPseudoTestedTest.[engine:junit-jupiter]/[class:dev.proofjava.playground.CalculatorPseudoTestedTest]/[method:squareHasNoAssertion()]"))),
                        ),
                    ),
                    ambient = emptyList(),
                ),
            ),
        )
        val fileCoverage = FileCoverageBlock(
            files = listOf(FileCoverageEntry(module = "root", path = "src/main/java/dev/proofjava/playground/Calculator.java", metrics = metricSet, lines = emptyList())),
            excluded = emptyList(),
        )
        val testFileSource = "package dev.proofjava.playground;\n\npublic class CalculatorPseudoTestedTest {\n    void squareHasNoAssertion() {}\n}\n"

        val content = computeHover(
            coverageState = state(fileCoverage),
            perTest = realPerTest,
            fileText = testFileSource,
            fileBaseNameWithoutExtension = "CalculatorPseudoTestedTest",
            repoRelativePath = "src/test/java/dev/proofjava/playground/CalculatorPseudoTestedTest.java",
            lineNumber = 4,
            wordAtCursor = "squareHasNoAssertion",
        )
        assertTrue(content != null, "expected the reverse-direction hover on a test method")
        assertTrue(content!!.html.contains("production lines"), "must be the reverse hover, which names the production lines this test runs")
        assertTrue(content.html.contains("Calculator.java"))
    }
}
