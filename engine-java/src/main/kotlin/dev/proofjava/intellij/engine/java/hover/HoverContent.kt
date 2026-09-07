package dev.proofjava.intellij.engine.java.hover

import dev.proofjava.intellij.core.model.CoverageState
import dev.proofjava.intellij.core.verdict.PerTestBlock
import dev.proofjava.intellij.engine.java.linetests.ClassLookupResult
import dev.proofjava.intellij.engine.java.linetests.LineQuality
import dev.proofjava.intellij.engine.java.linetests.TestQualityRef
import dev.proofjava.intellij.engine.java.linetests.TestVerdict
import dev.proofjava.intellij.engine.java.linetests.indexFindingsByTestMethod
import dev.proofjava.intellij.engine.java.linetests.lineQuality
import dev.proofjava.intellij.engine.java.linetests.locateTestFile
import dev.proofjava.intellij.engine.java.linetests.testsForClass
import dev.proofjava.intellij.engine.java.linetests.testsToLines
import dev.proofjava.intellij.engine.java.source.SourceKind
import dev.proofjava.intellij.engine.java.source.SourceModuleRoots
import dev.proofjava.intellij.engine.java.source.buildProductionClassIndex
import dev.proofjava.intellij.engine.java.source.classifySourcePath
import dev.proofjava.intellij.engine.java.source.detectClassName
import dev.proofjava.intellij.engine.java.source.productionClassFilter
import dev.proofjava.intellij.engine.java.source.productionSourceRoots
import dev.proofjava.intellij.engine.java.source.sourceModuleRoots
import dev.proofjava.intellij.engine.java.source.testSourceRoots
import dev.proofjava.intellij.engine.java.source.toAbsolutePath
import dev.proofjava.intellij.engine.java.verdict.parseTestIdentity
import java.net.URLEncoder

/**
 * Port of `proof-vscode/src/ui/hoverProvider.ts`'s actual answer-computation
 * logic (the "is this line really tested?"/"what does this test run?"
 * join, Faz 15b/21) - deliberately kept free of any IntelliJ Platform SDK
 * import so it is unit-testable the same way as the rest of `engine-java`'s
 * model layer. The SDK-facing `DocumentationTargetProvider`/`-Target`/
 * `-LinkHandler` trio in this same package is thin wiring around
 * [computeHover]; nothing else in this file talks to the platform.
 *
 * HTML, not `vscode.MarkdownString` - IntelliJ's `DocumentationResult`
 * takes HTML, so the presentation is ported to HTML output rather than the
 * TS source's Markdown, using its own `coverage-nav://` link scheme
 * (resolved by `CoverageHoverLinkHandler`) in place of VS Code's
 * `command:vscode.open` URIs.
 */
data class HoverContent(val title: String, val html: String)

/**
 * @param repoRelativePath the hovered file's own path relative to [coverageState]'s `projectRoot` - `null` when the file is outside the project (or [coverageState] itself is `null`); treated as "no declared root matches", same as the TS source's `toRepoRelativePath(...) ?? ''` fallback.
 * @param lineNumber 1-based line under the cursor.
 * @param wordAtCursor the identifier under the cursor, if any - only consulted in the reverse (test-method) direction, mirroring `document.getWordRangeAtPosition`.
 */
fun computeHover(
    coverageState: CoverageState?,
    perTest: PerTestBlock?,
    fileText: String,
    fileBaseNameWithoutExtension: String,
    repoRelativePath: String?,
    lineNumber: Int,
    wordAtCursor: String?,
): HoverContent? {
    if (coverageState == null || perTest == null) {
        return null
    }

    val className = detectClassName(fileText, fileBaseNameWithoutExtension)
    val findingsByTestMethod = indexFindingsByTestMethod(coverageState.findings)
    val moduleRoots = sourceModuleRoots(coverageState)

    // Faz 21: direction comes from the CLI's own root declaration, not from
    // which map happens to have an entry - see `LineIndex.testsToLines`'s
    // own doc comment for why (PIT's L2 collector writes test classes into
    // `entries` too, so the naive check picks the wrong direction).
    val sourceKind = classifySourcePath(repoRelativePath ?: "", moduleRoots)
    if (sourceKind == SourceKind.TEST) {
        return wordAtCursor?.let { testMethodHover(coverageState, perTest, moduleRoots, className, it) }
    }

    val productionLookup = testsForClass(perTest, className)
    if (productionLookup is ClassLookupResult.Found) {
        val tests = productionLookup.linesToTests[lineNumber]
        if (!tests.isNullOrEmpty()) {
            val quality = lineQuality(tests, findingsByTestMethod)
            return productionHover(coverageState, moduleRoots, lineNumber, quality)
        }
        return null // a known class, but this specific line has no per-test evidence - no hover, not a guess
    }

    return wordAtCursor?.let { testMethodHover(coverageState, perTest, moduleRoots, className, it) }
}

private fun productionHover(
    coverageState: CoverageState,
    moduleRoots: List<SourceModuleRoots>,
    lineNumber: Int,
    quality: LineQuality,
): HoverContent {
    val testRoots = testSourceRoots(moduleRoots)
    // Resolve each test's own file only once per class (most lines share a
    // handful of test classes), same as the TS source's own dedup via `Set`.
    val uniqueClassNames = quality.tests.mapNotNull { parseTestIdentity(it.rawTestId).className }.distinct()
    val pathByClassName = uniqueClassNames.associateWith { className ->
        val findingWithPath = quality.tests
            .firstOrNull { it.finding != null && parseTestIdentity(it.rawTestId).className == className }
            ?.finding?.path
        locateTestFile(coverageState.projectRoot, testRoots, className, findingWithPath)
    }

    val html = StringBuilder()
    if (quality.isFalseGreen) {
        html.append("<p><b>&#9888; Proof: none of the tests covering this line has an oracle</b> - it's covered (green), but not actually verified.</p><hr/>")
    }
    html.append("<p><b>Proof — line $lineNumber</b></p>")
    val weakCount = (quality.byVerdict[TestVerdict.NO_ORACLE] ?: 0) + (quality.byVerdict[TestVerdict.WEAK] ?: 0)
    val summary = "${quality.tests.size} test(s) cover this" + if (weakCount > 0) " · $weakCount with no/weak oracle" else ""
    html.append("<p>${escapeHtml(summary)}</p>")
    for (test in quality.tests) {
        html.append("<p>${verdictIcon(test.verdict)} ${testLine(coverageState.projectRoot, test, pathByClassName)}</p>")
    }
    return HoverContent("Proof — line $lineNumber", html.toString())
}

private fun testLine(projectRoot: String, test: TestQualityRef, pathByClassName: Map<String, String?>): String {
    val identity = parseTestIdentity(test.rawTestId)
    val ruleTag = test.finding?.let { " <code>${escapeHtml(it.rule.name)}</code>" } ?: ""
    val path = identity.className?.let { pathByClassName[it] }
    if (path == null) {
        return "${escapeHtml(identity.display)}$ruleTag"
    }
    val startLine = test.finding?.startLine ?: 1
    val link = navLink(toAbsolutePath(projectRoot, path), startLine, identity.display)
    return "$link$ruleTag"
}

private fun testMethodHover(
    coverageState: CoverageState,
    perTest: PerTestBlock,
    moduleRoots: List<SourceModuleRoots>,
    className: String,
    methodName: String,
): HoverContent? {
    val key = "$className#$methodName()"
    // Faz 21: test classes' own lines are excluded here too - `entries`
    // holds them, and unfiltered a test would list its own body as
    // "production lines it runs" (same filter `LineTestsTree` uses).
    val reverse = testsToLines(perTest, productionClassFilter(coverageState))[key]
    if (reverse.isNullOrEmpty()) {
        return null // not a known test method - no hover, not a guess
    }

    val productionClassIndex = coverageState.fileCoverage?.let {
        buildProductionClassIndex(it, productionSourceRoots(moduleRoots)).byClassName
    }
    val byClass = linkedMapOf<String, MutableList<Int>>()
    for (ref in reverse) {
        byClass.getOrPut(ref.outerClassName) { mutableListOf() }.add(ref.line)
    }

    val html = StringBuilder()
    html.append("<p><b>Proof — $methodName()</b></p>")
    html.append("<p>This test executes the following production lines (only classes targeted in this run):</p>")
    for ((outerClassName, lines) in byClass) {
        val path = productionClassIndex?.get(outerClassName)
        val label = "${shortName(outerClassName)}.java"
        val sortedLines = lines.sorted()
        val lineLinks = sortedLines.joinToString(", ") { line ->
            if (path != null) navLink(toAbsolutePath(coverageState.projectRoot, path), line, line.toString()) else line.toString()
        }
        html.append("<p>${escapeHtml(label)}: $lineLinks</p>")
    }
    return HoverContent("Proof — $methodName()", html.toString())
}

private fun verdictIcon(verdict: TestVerdict): String = when (verdict) {
    TestVerdict.OK -> "✓"
    TestVerdict.INCONCLUSIVE -> "?"
    else -> "⚠"
}

private fun shortName(fqcn: String): String {
    val dot = fqcn.lastIndexOf('.')
    return if (dot < 0) fqcn else fqcn.substring(dot + 1)
}

/** `coverage-nav://` link, resolved by `CoverageHoverLinkHandler` - the IntelliJ equivalent of the TS source's `command:vscode.open?...` URI. [absolutePath] is URL-encoded since it can contain characters a URI query cannot hold verbatim (Windows drive-letter colons, spaces, backslashes). */
internal fun navLink(absolutePath: String, line: Int, label: String): String {
    val encodedPath = URLEncoder.encode(absolutePath, "UTF-8")
    val href = "coverage-nav://open?file=$encodedPath&line=$line"
    return "<a href=\"$href\">${escapeHtml(label)}</a>"
}

private fun escapeHtml(text: String): String = text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
