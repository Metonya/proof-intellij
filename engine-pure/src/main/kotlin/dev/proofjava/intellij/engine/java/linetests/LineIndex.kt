package dev.proofjava.intellij.engine.java.linetests

import dev.proofjava.intellij.core.verdict.PerTestBlock
import dev.proofjava.intellij.core.verdict.PerTestEntry
import dev.proofjava.intellij.engine.java.verdict.parseTestIdentity

/**
 * Port of `proof-vscode/src/model/lineIndex.ts`: "which tests cover this
 * line" for one class, from `perTest.entries[].lines[].tests[]`.
 *
 * `entries` and `ambient` are kept in two separate maps rather than
 * flattened together - the CLI's own distinction (D-50) between "a test
 * directly executed this line" (`entries`) and "this line only ran as a
 * side effect of a static initializer/`<clinit>`, indirectly reachable
 * from a test but not really exercised by it" (`ambient`) must not be
 * destroyed, so a caller can label ambient evidence instead of quietly
 * mixing it into the same list.
 */

sealed interface ClassLookupResult {
    /** `perTest.modules` is empty - no L2 evidence was collected at all this run. Distinct from [ClassNotFound] (evidence exists, just not for this class) per hard rule 3a. */
    data object NoEvidence : ClassLookupResult
    data object ClassNotFound : ClassLookupResult
    data class Found(
        val linesToTests: Map<Int, List<String>>,
        val ambientLinesToTests: Map<Int, List<String>>,
        /** A line's own production method - from `perTest.entries[].methodName`, never guessed. A line (rarely) claimed by more than one distinct method is ambiguous and left out of the map entirely (hard rule 3a). */
        val linesToMethod: Map<Int, String>,
        val ambientLinesToMethod: Map<Int, String>,
    ) : ClassLookupResult
}

/** Merges evidence across every bound module - `perTest.modules` may hold several (a multi-module run), and a class lives in exactly one of them, so merging is safe. */
fun testsForClass(perTest: PerTestBlock, className: String): ClassLookupResult {
    if (perTest.modules.isEmpty()) {
        return ClassLookupResult.NoEvidence
    }

    val outerClassName = stripNestedSuffix(className)
    val allEntries = perTest.modules.flatMap { it.entries }
    val allAmbient = perTest.modules.flatMap { it.ambient }
    val (linesToTests, linesToMethod) = collectLines(allEntries, outerClassName)
    val (ambientLinesToTests, ambientLinesToMethod) = collectLines(allAmbient, outerClassName)

    if (linesToTests.isEmpty() && ambientLinesToTests.isEmpty()) {
        return ClassLookupResult.ClassNotFound
    }
    return ClassLookupResult.Found(linesToTests, ambientLinesToTests, linesToMethod, ambientLinesToMethod)
}

/** One class's line-level evidence, [ClassLookupResult.Found]'s shape without the ambient half - [allClasses]'s per-class entry. */
data class ClassLines(val className: String, val linesToTests: Map<Int, List<String>>, val linesToMethod: Map<Int, String>)

/**
 * Mirrors `model/mutationModel.ts`'s `classesOf`: "Line → Tests" can show
 * something even with no Java file open at all (like the Mutation view
 * always does) - every class in `perTest.entries`' own line -> test map,
 * independent of any active file. [isProductionClass] unfiltered means
 * nothing is excluded (test classes' own lines stay visible too).
 */
fun allClasses(perTest: PerTestBlock, isProductionClass: ((String) -> Boolean)? = null): List<ClassLines> {
    val entriesByClass = linkedMapOf<String, MutableList<PerTestEntry>>()
    for (entry in perTest.modules.flatMap { it.entries }) {
        val outerClassName = stripNestedSuffix(entry.className)
        if (isProductionClass != null && !isProductionClass(outerClassName)) continue
        entriesByClass.getOrPut(outerClassName) { mutableListOf() }.add(entry)
    }
    return entriesByClass.entries
        .map { (className, entries) ->
            val (linesToTests, linesToMethod) = collectLines(entries, className)
            ClassLines(className, linesToTests, linesToMethod)
        }
        .sortedBy { it.className }
}

private fun collectLines(entries: List<PerTestEntry>, outerClassName: String): Pair<Map<Int, List<String>>, Map<Int, String>> {
    val linesToTests = linkedMapOf<Int, MutableList<String>>()
    val methodNamesPerLine = linkedMapOf<Int, MutableSet<String>>()
    for (entry in entries) {
        if (stripNestedSuffix(entry.className) != outerClassName) continue
        for (line in entry.lines) {
            linesToTests.getOrPut(line.line) { mutableListOf() }.addAll(line.tests)
            methodNamesPerLine.getOrPut(line.line) { mutableSetOf() }.add(entry.methodName)
        }
    }
    // Only labeled when a single method claims this line - if two methods
    // share a line (not observed, theoretically possible), guessing which
    // one is worse than not labeling at all.
    val linesToMethod = mutableMapOf<Int, String>()
    for ((line, methodNames) in methodNamesPerLine) {
        if (methodNames.size == 1) {
            linesToMethod[line] = methodNames.first()
        }
    }
    return linesToTests to linesToMethod
}

/** Strips a nested-class suffix (`Outer$Inner` -> `Outer`) - same convention proof-java-cli's PseudoTestedMethodRule uses on the production side. */
private fun stripNestedSuffix(className: String): String {
    val dollar = className.indexOf('$')
    return if (dollar < 0) className else className.substring(0, dollar)
}

/** One production line a test (`className#methodName`) is on record as covering. */
data class TestLineRef(
    /** Outer class name (nested-suffix already stripped) - the unit `source.PathIndex`'s FQCN-to-path resolution expects. */
    val outerClassName: String,
    val line: Int,
)

/**
 * The reverse of [testsForClass]: "this test covers these production
 * lines". Only `entries` (real direct evidence) - `ambient` (`<clinit>`-
 * sourced, D-50) is deliberately excluded, since "this test covers this
 * line" claims the test's own logic triggered it; presenting a line only
 * indirectly run via a static initializer with the same claim would be
 * misleading.
 */
fun testsToLines(perTest: PerTestBlock, isProductionClass: ((String) -> Boolean)? = null): Map<String, List<TestLineRef>> {
    val result = linkedMapOf<String, MutableList<TestLineRef>>()
    for (module in perTest.modules) {
        for (entry in module.entries) {
            val outerClassName = stripNestedSuffix(entry.className)
            // PIT's L2 collector writes test classes into `entries` too - if
            // unfiltered, a test would list its own body's lines as
            // "production lines it runs". No filter (no fileCoverage means
            // we cannot know which class is production) lets everything
            // through: filtering with incomplete data destroys evidence.
            if (isProductionClass != null && !isProductionClass(outerClassName)) continue
            for (line in entry.lines) {
                for (rawTestId in line.tests) {
                    addTestLineRef(result, rawTestId, outerClassName, line.line)
                }
            }
        }
    }
    return result
}

/** One or more consecutive production lines covered by the exact same set of tests. */
data class LineGroup(val startLine: Int, val endLine: Int, val tests: List<String>, val methodName: String?)

/**
 * Merges adjacent lines with identical test sets (and, when [linesToMethod]
 * is given, identical owning method too - two different methods' lines
 * never merge even if their test sets happen to match) into one range,
 * mirroring `ui/treeViews/coverageView.ts`'s own `uncoveredNewRanges`
 * pattern - without this, a constructor body's 6 consecutive lines covered
 * by the same test would be 6 separate near-duplicate rows.
 */
fun groupConsecutiveLines(linesToTests: Map<Int, List<String>>, linesToMethod: Map<Int, String>? = null): List<LineGroup> {
    val sortedLines = linesToTests.keys.sorted()
    val groups = mutableListOf<LineGroup>()
    for (line in sortedLines) {
        val tests = linesToTests.getValue(line)
        val methodName = linesToMethod?.get(line)
        val last = groups.lastOrNull()
        if (last != null && last.endLine == line - 1 && sameTestSet(last.tests, tests) && last.methodName == methodName) {
            groups[groups.size - 1] = last.copy(endLine = line)
        } else {
            groups += LineGroup(line, line, tests, methodName)
        }
    }
    return groups
}

/** Order-independent set equality over raw test ids - two lines "have the same tests" regardless of the order `perTest` happened to list them in. */
private fun sameTestSet(a: List<String>, b: List<String>): Boolean {
    if (a.size != b.size) return false
    val sortedA = a.sorted()
    val sortedB = b.sorted()
    return sortedA == sortedB
}

private fun addTestLineRef(result: MutableMap<String, MutableList<TestLineRef>>, rawTestId: String, outerClassName: String, line: Int) {
    val identity = parseTestIdentity(rawTestId)
    val className = identity.className ?: return
    val methodName = identity.methodName ?: return
    val key = "$className#$methodName()"
    result.getOrPut(key) { mutableListOf() }.add(TestLineRef(outerClassName, line))
}
