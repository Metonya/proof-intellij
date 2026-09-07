package dev.proofjava.intellij.engine.java.linetests

import dev.proofjava.intellij.core.verdict.PerTestBlock
import dev.proofjava.intellij.core.verdict.PerTestEntry
import dev.proofjava.intellij.core.verdict.PerTestLine
import dev.proofjava.intellij.core.verdict.PerTestModuleEvidence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Kotlin port of `proof-vscode/src/test/unit/model/lineIndex.test.ts`. */
class LineIndexTest {

    private fun perTestLine(line: Int, vararg tests: String) = PerTestLine(line, tests.toList())

    private val block = PerTestBlock(
        engine = "pitest",
        engineVersion = "1.15.8",
        modules = listOf(
            PerTestModuleEvidence(
                id = "root",
                entries = listOf(
                    PerTestEntry("dev.proofjava.playground.Calculator", "add", listOf(perTestLine(7, "CalcTest#addsTwoNumbers()"))),
                    PerTestEntry("dev.proofjava.playground.Calculator\$Inner", "helper", listOf(perTestLine(40, "CalcTest#innerHelperTest()"))),
                ),
                ambient = listOf(
                    PerTestEntry("dev.proofjava.playground.Calculator", "<clinit>", listOf(perTestLine(3, "CalcTest#addsTwoNumbers()"))),
                ),
            ),
        ),
    )

    @Test
    fun `a known class returns real entries in linesToTests, static-initializer evidence separately in ambientLinesToTests`() {
        val result = testsForClass(block, "dev.proofjava.playground.Calculator")
        check(result is ClassLookupResult.Found)
        assertEquals(listOf("CalcTest#addsTwoNumbers()"), result.linesToTests[7])
        assertNull(result.linesToTests[3])
        assertEquals(listOf("CalcTest#addsTwoNumbers()"), result.ambientLinesToTests[3])
    }

    @Test
    fun `linesToMethod carries the real production method name per line, ambient stays in its own map`() {
        val result = testsForClass(block, "dev.proofjava.playground.Calculator")
        check(result is ClassLookupResult.Found)
        assertEquals("add", result.linesToMethod[7])
        assertNull(result.linesToMethod[3])
        assertEquals("<clinit>", result.ambientLinesToMethod[3])
    }

    @Test
    fun `a nested class entry is matched under its outer class name`() {
        val result = testsForClass(block, "dev.proofjava.playground.Calculator")
        check(result is ClassLookupResult.Found)
        assertEquals(listOf("CalcTest#innerHelperTest()"), result.linesToTests[40])
    }

    @Test
    fun `an empty modules array is noEvidence, distinct from a missing class`() {
        val empty = PerTestBlock("pitest", "1.15.8", emptyList())
        assertEquals(ClassLookupResult.NoEvidence, testsForClass(empty, "dev.proofjava.playground.Calculator"))
    }

    @Test
    fun `a class with no matching entries is classNotFound`() {
        assertEquals(ClassLookupResult.ClassNotFound, testsForClass(block, "dev.proofjava.playground.NotAChangedClass"))
    }

    private val twoClasses = PerTestBlock(
        engine = "pitest",
        engineVersion = "1.15.8",
        modules = listOf(
            PerTestModuleEvidence(
                id = "root",
                entries = listOf(
                    PerTestEntry("dev.proofjava.playground.Calculator", "add", listOf(perTestLine(7, "CalcTest#addsTwoNumbers()"))),
                    PerTestEntry("dev.proofjava.playground.Multiplier", "times", listOf(perTestLine(12, "MultiplierTest#timesTwo()"))),
                    PerTestEntry("dev.proofjava.playground.CalculatorGoodTest", "addsTwoNumbers", listOf(perTestLine(5, "CalcTest#addsTwoNumbers()"))),
                ),
                ambient = emptyList(),
            ),
        ),
    )

    @Test
    fun `allClasses groups entries by outer class name, one ClassLines per real class`() {
        val classes = allClasses(twoClasses)
        assertEquals(
            listOf("dev.proofjava.playground.Calculator", "dev.proofjava.playground.CalculatorGoodTest", "dev.proofjava.playground.Multiplier"),
            classes.map { it.className },
        )
        val calculator = classes.find { it.className == "dev.proofjava.playground.Calculator" }!!
        assertEquals(listOf("CalcTest#addsTwoNumbers()"), calculator.linesToTests[7])
        assertEquals("add", calculator.linesToMethod[7])
    }

    @Test
    fun `allClasses with a production-class filter, test classes are dropped`() {
        val isProduction: (String) -> Boolean = { it != "dev.proofjava.playground.CalculatorGoodTest" }
        val classes = allClasses(twoClasses, isProduction)
        assertEquals(listOf("dev.proofjava.playground.Calculator", "dev.proofjava.playground.Multiplier"), classes.map { it.className })
    }

    @Test
    fun `allClasses without a filter nothing is dropped`() {
        assertEquals(3, allClasses(twoClasses).size)
    }

    @Test
    fun `allClasses an empty modules array returns an empty list, not an error`() {
        val empty = PerTestBlock("pitest", "1.15.8", emptyList())
        assertEquals(emptyList<ClassLines>(), allClasses(empty))
    }

    @Test
    fun `allClasses a nested class entry is grouped under its outer class name`() {
        val classes = allClasses(block)
        assertEquals(listOf("dev.proofjava.playground.Calculator"), classes.map { it.className })
        assertEquals(listOf("CalcTest#innerHelperTest()"), classes[0].linesToTests[40])
    }

    @Test
    fun `testsForClass merges evidence across several bound modules`() {
        val twoModules = PerTestBlock(
            "pitest", "1.15.8",
            listOf(
                block.modules[0],
                PerTestModuleEvidence("gson", listOf(PerTestEntry("com.example.Other", "run", listOf(perTestLine(12, "OtherTest#runs()")))), emptyList()),
            ),
        )
        val fromFirstModule = testsForClass(twoModules, "dev.proofjava.playground.Calculator")
        assertTrue(fromFirstModule is ClassLookupResult.Found)
        val fromSecondModule = testsForClass(twoModules, "com.example.Other")
        check(fromSecondModule is ClassLookupResult.Found)
        assertEquals(listOf("OtherTest#runs()"), fromSecondModule.linesToTests[12])
    }

    @Test
    fun `testsToLines a test appears once per production line it covers, keyed by Class#method()`() {
        val reverse = testsToLines(block)
        val refs = reverse["CalcTest#addsTwoNumbers()"]
        assertEquals(listOf(TestLineRef("dev.proofjava.playground.Calculator", 7)), refs?.sortedBy { it.line })
    }

    @Test
    fun `testsToLines ambient evidence is excluded`() {
        val reverse = testsToLines(block)
        val refs = reverse["CalcTest#addsTwoNumbers()"]
        assertFalse(refs!!.any { it.line == 3 })
    }

    @Test
    fun `testsToLines a nested-class entry is reported under its outer class name`() {
        val reverse = testsToLines(block)
        assertEquals(listOf(TestLineRef("dev.proofjava.playground.Calculator", 40)), reverse["CalcTest#innerHelperTest()"])
    }

    private val blockWithSelfCoveringTest = PerTestBlock(
        "pitest", "1.15.8",
        listOf(
            PerTestModuleEvidence(
                "root",
                listOf(
                    PerTestEntry("dev.proofjava.playground.Calculator", "add", listOf(perTestLine(7, "CalcTest#addsTwoNumbers()"))),
                    PerTestEntry("dev.proofjava.playground.CalcTest", "addsTwoNumbers", listOf(perTestLine(18, "CalcTest#addsTwoNumbers()"))),
                ),
                emptyList(),
            ),
        ),
    )

    @Test
    fun `testsToLines with a production-class filter, a test does not report its own lines as production lines`() {
        val reverse = testsToLines(blockWithSelfCoveringTest) { it == "dev.proofjava.playground.Calculator" }
        assertEquals(listOf(7), reverse["CalcTest#addsTwoNumbers()"]?.map { it.line })
    }

    @Test
    fun `testsToLines without a filter nothing is dropped`() {
        val reverse = testsToLines(blockWithSelfCoveringTest)
        assertEquals(listOf(7, 18), reverse["CalcTest#addsTwoNumbers()"]?.map { it.line })
    }

    @Test
    fun `testsToLines an empty modules array returns an empty map, not an error`() {
        val empty = PerTestBlock("pitest", "1.15.8", emptyList())
        assertEquals(0, testsToLines(empty).size)
    }

    @Test
    fun `groupConsecutiveLines consecutive lines with the exact same test set merge into one range`() {
        val linesToTests = linkedMapOf(
            9 to listOf("NotifyingCalculatorMockitoTest#addAndNotifySendsTheComputedResult()"),
            10 to listOf("NotifyingCalculatorMockitoTest#addAndNotifySendsTheComputedResult()"),
            11 to listOf("NotifyingCalculatorMockitoTest#addAndNotifySendsTheComputedResult()"),
        )
        val groups = groupConsecutiveLines(linesToTests)
        assertEquals(listOf(LineGroup(9, 11, listOf("NotifyingCalculatorMockitoTest#addAndNotifySendsTheComputedResult()"), null)), groups)
    }

    @Test
    fun `groupConsecutiveLines a differently-tested line in the middle splits the range`() {
        val linesToTests = linkedMapOf(9 to listOf("TestA#a()"), 10 to listOf("TestB#b()"), 11 to listOf("TestA#a()"))
        val groups = groupConsecutiveLines(linesToTests)
        assertEquals(
            listOf(LineGroup(9, 9, listOf("TestA#a()"), null), LineGroup(10, 10, listOf("TestB#b()"), null), LineGroup(11, 11, listOf("TestA#a()"), null)),
            groups,
        )
    }

    @Test
    fun `groupConsecutiveLines a non-consecutive line number never merges even with the same test set`() {
        val linesToTests = linkedMapOf(9 to listOf("TestA#a()"), 15 to listOf("TestA#a()"))
        val groups = groupConsecutiveLines(linesToTests)
        assertEquals(listOf(LineGroup(9, 9, listOf("TestA#a()"), null), LineGroup(15, 15, listOf("TestA#a()"), null)), groups)
    }

    @Test
    fun `groupConsecutiveLines the same test set in a different order still merges`() {
        val linesToTests = linkedMapOf(9 to listOf("TestA#a()", "TestB#b()"), 10 to listOf("TestB#b()", "TestA#a()"))
        val groups = groupConsecutiveLines(linesToTests)
        assertEquals(1, groups.size)
        assertEquals(9, groups[0].startLine)
        assertEquals(10, groups[0].endLine)
    }

    @Test
    fun `groupConsecutiveLines a lone line is its own group with startLine equal to endLine`() {
        val groups = groupConsecutiveLines(linkedMapOf(7 to listOf("CalcTest#addsTwoNumbers()")))
        assertEquals(listOf(LineGroup(7, 7, listOf("CalcTest#addsTwoNumbers()"), null)), groups)
    }

    @Test
    fun `groupConsecutiveLines linesToMethod labels a range with its production method, real init shape`() {
        val linesToTests = linkedMapOf(4 to (0 until 14).map { "Test$it#t()" })
        val linesToMethod = linkedMapOf(4 to "<init>")
        val groups = groupConsecutiveLines(linesToTests, linesToMethod)
        assertEquals(1, groups.size)
        assertEquals("<init>", groups[0].methodName)
    }

    @Test
    fun `groupConsecutiveLines adjacent lines from two different methods never merge even with identical test sets`() {
        val linesToTests = linkedMapOf(10 to listOf("TestA#a()"), 11 to listOf("TestA#a()"))
        val linesToMethod = linkedMapOf(10 to "foo", 11 to "bar")
        val groups = groupConsecutiveLines(linesToTests, linesToMethod)
        assertEquals(listOf(LineGroup(10, 10, listOf("TestA#a()"), "foo"), LineGroup(11, 11, listOf("TestA#a()"), "bar")), groups)
    }
}
