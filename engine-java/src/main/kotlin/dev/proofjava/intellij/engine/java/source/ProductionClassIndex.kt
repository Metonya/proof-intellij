package dev.proofjava.intellij.engine.java.source

import dev.proofjava.intellij.core.model.CoverageState
import dev.proofjava.intellij.core.verdict.FileCoverageBlock

/**
 * Port of `proof-vscode/src/model/productionClassIndex.ts`. A multi-module
 * run can (rarely) declare the same FQCN in two modules; [byClassName]
 * never guesses which one is meant - a colliding name is removed from it
 * and recorded in [ambiguous] instead (hard rule 3a: last-writer-wins
 * would silently point navigation at the wrong file).
 */
data class ProductionClassIndex(val byClassName: Map<String, String>, val ambiguous: Set<String>)

/** `className -> repo-relative path`, built straight from `fileCoverage.files[]` (already the complete, filtered listing of production files this run knows about) - no filesystem probe needed. */
fun buildProductionClassIndex(fileCoverage: FileCoverageBlock, sourceRoots: List<String>): ProductionClassIndex {
    val byClassName = mutableMapOf<String, String>()
    val ambiguous = mutableSetOf<String>()
    for (file in fileCoverage.files) {
        val className = classNameFromPath(file.path, sourceRoots) ?: continue
        if (ambiguous.contains(className)) continue
        if (byClassName.containsKey(className)) {
            byClassName.remove(className)
            ambiguous += className
            continue
        }
        byClassName[className] = file.path
    }
    return ProductionClassIndex(byClassName, ambiguous)
}

/** The run's own declared source roots. Falls back to Maven's convention only when the declaration is genuinely empty (an old restored verdict) - not a guess, the CLI's own default. */
fun productionSourceRoots(modules: List<SourceModuleRoots>): List<String> {
    val declared = modules.flatMap { it.sourceRoots }
    return declared.ifEmpty { listOf("src/main/java") }
}

/** [productionSourceRoots]'s test-root counterpart. */
fun testSourceRoots(modules: List<SourceModuleRoots>): List<String> {
    val declared = modules.flatMap { it.testRoots }
    return declared.ifEmpty { listOf("src/test/java") }
}

/**
 * Which classes are production, from `fileCoverage.files[]` (this run's own
 * authoritative list) - `null` (no filter, nothing excluded) when there is
 * no `fileCoverage` block to build one from, matching `lineTestsView.ts`'s
 * own "missing information must not silently delete evidence" rule.
 * Shared by every `engine-java` caller that needs to tell production
 * classes from test classes in `perTest.entries` (the Line→Tests tool
 * window and the hover provider both need this exact join), so it exists
 * once, not once per caller.
 */
fun productionClassFilter(coverageState: CoverageState?): ((String) -> Boolean)? {
    val fileCoverage = coverageState?.fileCoverage ?: return null
    val index = buildProductionClassIndex(fileCoverage, productionSourceRoots(sourceModuleRoots(coverageState)))
    return { outerClassName -> index.byClassName.containsKey(outerClassName) }
}
