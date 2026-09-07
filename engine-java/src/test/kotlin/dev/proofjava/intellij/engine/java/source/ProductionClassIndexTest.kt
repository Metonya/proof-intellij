package dev.proofjava.intellij.engine.java.source

import dev.proofjava.intellij.core.verdict.FileCoverageBlock
import dev.proofjava.intellij.core.verdict.FileCoverageEntry
import dev.proofjava.intellij.core.verdict.Metric
import dev.proofjava.intellij.core.verdict.MetricSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Kotlin port of `proof-vscode/src/test/unit/model/productionClassIndex.test.ts`. */
class ProductionClassIndexTest {

    private val metric = Metric("a", 1, "b", 1, 100.0)
    private val metricSet = MetricSet(metric, metric, metric)

    private fun entry(module: String, path: String) = FileCoverageEntry(module, path, metricSet, emptyList())

    @Test
    fun `maps a normal file to its FQCN`() {
        val fileCoverage = FileCoverageBlock(files = listOf(entry("root", "src/main/java/dev/proofjava/playground/Calculator.java")), excluded = emptyList())
        val index = buildProductionClassIndex(fileCoverage, listOf("src/main/java"))
        assertEquals("src/main/java/dev/proofjava/playground/Calculator.java", index.byClassName["dev.proofjava.playground.Calculator"])
        assertEquals(0, index.ambiguous.size)
    }

    /** A multi-module run can (rarely) declare the same FQCN in two modules. Last-writer-wins would silently point navigation at whichever module happened to be listed last - a colliding name is instead removed from byClassName and recorded as ambiguous. */
    @Test
    fun `a duplicate FQCN across two modules is dropped from byClassName and recorded as ambiguous`() {
        val fileCoverage = FileCoverageBlock(
            files = listOf(entry("moduleA", "moduleA/src/main/java/com/example/Shared.java"), entry("moduleB", "moduleB/src/main/java/com/example/Shared.java")),
            excluded = emptyList(),
        )
        val index = buildProductionClassIndex(fileCoverage, listOf("moduleA/src/main/java", "moduleB/src/main/java"))
        assertFalse(index.byClassName.containsKey("com.example.Shared"))
        assertTrue(index.ambiguous.contains("com.example.Shared"))
    }

    @Test
    fun `a third file with the same colliding name does not resurrect the entry`() {
        val fileCoverage = FileCoverageBlock(
            files = listOf(
                entry("a", "a/src/main/java/com/example/Shared.java"),
                entry("b", "b/src/main/java/com/example/Shared.java"),
                entry("c", "c/src/main/java/com/example/Shared.java"),
            ),
            excluded = emptyList(),
        )
        val index = buildProductionClassIndex(fileCoverage, listOf("a/src/main/java", "b/src/main/java", "c/src/main/java"))
        assertFalse(index.byClassName.containsKey("com.example.Shared"))
        assertEquals(1, index.ambiguous.size)
    }

    @Test
    fun `distinct FQCNs across modules never collide`() {
        val fileCoverage = FileCoverageBlock(
            files = listOf(entry("gson", "gson/src/main/java/com/google/gson/Gson.java"), entry("extras", "extras/src/main/java/com/google/gson/extras/Extra.java")),
            excluded = emptyList(),
        )
        val index = buildProductionClassIndex(fileCoverage, listOf("gson/src/main/java", "extras/src/main/java"))
        assertEquals("gson/src/main/java/com/google/gson/Gson.java", index.byClassName["com.google.gson.Gson"])
        assertEquals("extras/src/main/java/com/google/gson/extras/Extra.java", index.byClassName["com.google.gson.extras.Extra"])
        assertEquals(0, index.ambiguous.size)
    }

    @Test
    fun `productionSourceRoots flattens every module's declared roots`() {
        val roots = productionSourceRoots(listOf(SourceModuleRoots(sourceRoots = listOf("gson/src/main/java")), SourceModuleRoots(sourceRoots = listOf("extras/src/main/java"))))
        assertEquals(listOf("gson/src/main/java", "extras/src/main/java"), roots)
    }

    @Test
    fun `productionSourceRoots falls back to the Maven default when no module declares any`() {
        assertEquals(listOf("src/main/java"), productionSourceRoots(emptyList()))
    }

    @Test
    fun `testSourceRoots same shape as productionSourceRoots, for test roots`() {
        assertEquals(listOf("gson/src/test/java"), testSourceRoots(listOf(SourceModuleRoots(testRoots = listOf("gson/src/test/java")))))
        assertEquals(listOf("src/test/java"), testSourceRoots(emptyList()))
    }
}
