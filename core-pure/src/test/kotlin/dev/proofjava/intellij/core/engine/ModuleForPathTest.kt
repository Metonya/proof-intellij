package dev.proofjava.intellij.core.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Kotlin port of `proof-vscode/src/test/unit/cli/reportDiscovery.test.ts`'s `moduleForPath` cases, exercised through [bindTargetsToModules] since `moduleForPath` itself is private here (only `bindTargetsToModules` needs it). */
class ModuleForPathTest {

    private fun target(path: String) = ClassTarget(path, "com.example.Foo")

    @Test
    fun `a file under a module root resolves to that module`() {
        val modules = listOf(ModuleBinding("gson", "gson"), ModuleBinding("extras", "extras"))
        val bound = bindTargetsToModules(
            listOf(target("gson/src/test/java/com/google/gson/GsonTest.java"), target("extras/src/test/java/com/google/gson/extras/ExtraTest.java")),
            modules,
        )
        assertEquals(listOf("gson", "extras"), bound.map { it.moduleId })
    }

    @Test
    fun `a nested module's own root outranks its parent's (longest prefix wins)`() {
        val modules = listOf(ModuleBinding("root", "."), ModuleBinding("nested", "gson/nested"))
        val bound = bindTargetsToModules(
            listOf(target("gson/nested/src/main/java/Foo.java"), target("gson/src/main/java/Bar.java")),
            modules,
        )
        assertEquals(listOf("nested", "root"), bound.map { it.moduleId })
    }

    @Test
    fun `a path under no bound module's root is dropped, not a guess`() {
        val modules = listOf(ModuleBinding("gson", "gson"))
        val bound = bindTargetsToModules(listOf(target("extras/src/main/java/Foo.java")), modules)
        assertTrue(bound.isEmpty())
    }
}
