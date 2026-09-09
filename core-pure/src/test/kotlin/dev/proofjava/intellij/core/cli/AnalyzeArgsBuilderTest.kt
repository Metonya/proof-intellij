package dev.proofjava.intellij.core.cli

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Kotlin port of `proof-vscode/src/test/unit/cli/argsBuilder.test.ts`, test-by-test. */
class AnalyzeArgsBuilderTest {

    @Test
    fun `no-vcs mode`() {
        val args = buildAnalyzeArgs(AnalyzeArgsInput(repo = "/repo", diffMode = DiffMode.NoVcs, reportPath = "jacoco.xml", outPath = "/tmp/out.json"))
        assertEquals(listOf("analyze", "--repo", "/repo", "--no-vcs", "--report", "jacoco.xml", "--out", "/tmp/out.json"), args)
    }

    @Test
    fun `uncommitted mode`() {
        val args = buildAnalyzeArgs(AnalyzeArgsInput(repo = "/repo", diffMode = DiffMode.Uncommitted, reportPath = "jacoco.xml", outPath = "/tmp/out.json"))
        assertTrue(args.contains("--uncommitted"))
        assertFalse(args.contains("--no-vcs"))
    }

    @Test
    fun `base-ref mode includes the ref right after --base`() {
        val args = buildAnalyzeArgs(AnalyzeArgsInput(repo = "/repo", diffMode = DiffMode.Base("main"), reportPath = "jacoco.xml", outPath = "/tmp/out.json"))
        val baseIndex = args.indexOf("--base")
        assertTrue(baseIndex >= 0)
        assertEquals("main", args[baseIndex + 1])
    }

    @Test
    fun `fileCoverage flag is only appended when true`() {
        val withFlag = buildAnalyzeArgs(AnalyzeArgsInput(repo = "/repo", diffMode = DiffMode.NoVcs, reportPath = "jacoco.xml", outPath = "/tmp/out.json", fileCoverage = true))
        val withoutFlag = buildAnalyzeArgs(AnalyzeArgsInput(repo = "/repo", diffMode = DiffMode.NoVcs, reportPath = "jacoco.xml", outPath = "/tmp/out.json", fileCoverage = false))
        assertTrue(withFlag.contains("--file-coverage"))
        assertFalse(withoutFlag.contains("--file-coverage"))
    }

    @Test
    fun `coverageExclusions is joined with commas into one --coverage-exclusions value`() {
        val args = buildAnalyzeArgs(
            AnalyzeArgsInput(
                repo = "/repo", diffMode = DiffMode.NoVcs, reportPath = "jacoco.xml", outPath = "/tmp/out.json",
                coverageExclusions = listOf("**/generated/**", "src/main/java/**/*Dto.java"),
            ),
        )
        val flagIndex = args.indexOf("--coverage-exclusions")
        assertTrue(flagIndex >= 0)
        assertEquals("**/generated/**,src/main/java/**/*Dto.java", args[flagIndex + 1])
    }

    @Test
    fun `an empty or absent coverageExclusions never appends the flag`() {
        val absent = buildAnalyzeArgs(AnalyzeArgsInput(repo = "/repo", diffMode = DiffMode.NoVcs, reportPath = "jacoco.xml", outPath = "/tmp/out.json"))
        val empty = buildAnalyzeArgs(AnalyzeArgsInput(repo = "/repo", diffMode = DiffMode.NoVcs, reportPath = "jacoco.xml", outPath = "/tmp/out.json", coverageExclusions = emptyList()))
        assertFalse(absent.contains("--coverage-exclusions"))
        assertFalse(empty.contains("--coverage-exclusions"))
    }

    @Test
    fun `perTest appends --per-test-report and one --per-test-classpath id=path per entry`() {
        val args = buildAnalyzeArgs(
            AnalyzeArgsInput(
                repo = "/repo", diffMode = DiffMode.Uncommitted, reportPath = "jacoco.xml", outPath = "/tmp/out.json",
                perTest = EvidenceInput(classpaths = listOf(ClasspathBinding("root", "mutation-classpath.txt"))),
            ),
        )
        assertTrue(args.contains("--per-test-report"))
        val flagIndex = args.indexOf("--per-test-classpath")
        assertTrue(flagIndex >= 0)
        assertEquals("root=mutation-classpath.txt", args[flagIndex + 1])
    }

    /** A multi-module run needs one --per-test-classpath per module - the CLI validates each id independently, there is no repo-wide classpath. */
    @Test
    fun `perTest with several classpaths emits one --per-test-classpath per module`() {
        val args = buildAnalyzeArgs(
            AnalyzeArgsInput(
                repo = "/repo", diffMode = DiffMode.Uncommitted, reportPath = "jacoco.xml", outPath = "/tmp/out.json",
                perTest = EvidenceInput(classpaths = listOf(ClasspathBinding("gson", "gson/cp.txt"), ClasspathBinding("extras", "extras/cp.txt"))),
            ),
        )
        val flags = args.withIndex().filter { it.value == "--per-test-classpath" }.map { args[it.index + 1] }
        assertEquals(listOf("gson=gson/cp.txt", "extras=extras/cp.txt"), flags)
    }

    @Test
    fun `an absent perTest never appends either flag`() {
        val args = buildAnalyzeArgs(AnalyzeArgsInput(repo = "/repo", diffMode = DiffMode.Uncommitted, reportPath = "jacoco.xml", outPath = "/tmp/out.json"))
        assertFalse(args.contains("--per-test-report"))
        assertFalse(args.contains("--per-test-classpath"))
    }

    @Test
    fun `perTest targets append one --per-test-target per FQCN, even under no-vcs`() {
        val args = buildAnalyzeArgs(
            AnalyzeArgsInput(
                repo = "/repo", diffMode = DiffMode.NoVcs, reportPath = "jacoco.xml", outPath = "/tmp/out.json",
                perTest = EvidenceInput(
                    classpaths = listOf(ClasspathBinding("root", "proof-classpath.txt")),
                    targets = listOf(TargetBinding("root", "dev.example.Calculator")),
                ),
            ),
        )
        val flagIndex = args.indexOf("--per-test-target")
        assertTrue(flagIndex >= 0)
        assertEquals("root=dev.example.Calculator", args[flagIndex + 1])
        assertTrue(args.contains("--no-vcs"), "the builder itself does not reject no-vcs + a target - the CLI decides that")
    }

    @Test
    fun `perTest without targets never appends --per-test-target`() {
        val args = buildAnalyzeArgs(
            AnalyzeArgsInput(
                repo = "/repo", diffMode = DiffMode.Uncommitted, reportPath = "jacoco.xml", outPath = "/tmp/out.json",
                perTest = EvidenceInput(classpaths = listOf(ClasspathBinding("root", "proof-classpath.txt"))),
            ),
        )
        assertFalse(args.contains("--per-test-target"))
    }

    @Test
    fun `perTest timeoutSeconds appends --per-test-timeout, mirroring mutation's own timeout flag`() {
        val args = buildAnalyzeArgs(
            AnalyzeArgsInput(
                repo = "/repo", diffMode = DiffMode.Uncommitted, reportPath = "jacoco.xml", outPath = "/tmp/out.json",
                perTest = EvidenceInput(classpaths = listOf(ClasspathBinding("root", "proof-classpath.txt")), timeoutSeconds = 180),
            ),
        )
        assertEquals("180", args[args.indexOf("--per-test-timeout") + 1])
    }

    @Test
    fun `perTest without an explicit timeoutSeconds never appends --per-test-timeout`() {
        val args = buildAnalyzeArgs(
            AnalyzeArgsInput(
                repo = "/repo", diffMode = DiffMode.Uncommitted, reportPath = "jacoco.xml", outPath = "/tmp/out.json",
                perTest = EvidenceInput(classpaths = listOf(ClasspathBinding("root", "proof-classpath.txt"))),
            ),
        )
        assertFalse(args.contains("--per-test-timeout"))
    }

    @Test
    fun `--out is always the last two args`() {
        val args = buildAnalyzeArgs(AnalyzeArgsInput(repo = "/repo", diffMode = DiffMode.NoVcs, reportPath = "jacoco.xml", outPath = "/tmp/out.json"))
        assertEquals("--out", args[args.size - 2])
        assertEquals("/tmp/out.json", args[args.size - 1])
    }

    /** `--mutation-classpath` uses the same file shape as `--per-test-classpath`, but the CLI treats them as separate opt-ins - never merged here either. */
    @Test
    fun `mutation appends --mutation-report, its own classpath flag and the timeout`() {
        val args = buildAnalyzeArgs(
            AnalyzeArgsInput(
                repo = "/repo", diffMode = DiffMode.Uncommitted, reportPath = "jacoco.xml", outPath = "/tmp/out.json",
                mutation = EvidenceInput(classpaths = listOf(ClasspathBinding("root", "target/proof-classpath.txt")), timeoutSeconds = 300),
            ),
        )
        assertTrue(args.contains("--mutation-report"))
        assertEquals("root=target/proof-classpath.txt", args[args.indexOf("--mutation-classpath") + 1])
        assertEquals("300", args[args.indexOf("--mutation-timeout") + 1])
        assertFalse(args.contains("--per-test-report"), "mutation must not silently drag L2 along - separate opt-ins")
    }

    @Test
    fun `mutation with several classpaths emits one --mutation-classpath per module`() {
        val args = buildAnalyzeArgs(
            AnalyzeArgsInput(
                repo = "/repo", diffMode = DiffMode.Uncommitted, reportPath = "jacoco.xml", outPath = "/tmp/out.json",
                mutation = EvidenceInput(classpaths = listOf(ClasspathBinding("gson", "gson/cp.txt"), ClasspathBinding("extras", "extras/cp.txt"))),
            ),
        )
        val flags = args.withIndex().filter { it.value == "--mutation-classpath" }.map { args[it.index + 1] }
        assertEquals(listOf("gson=gson/cp.txt", "extras=extras/cp.txt"), flags)
    }

    @Test
    fun `mutation targets are passed one --mutation-target per class, each with its own module id`() {
        val args = buildAnalyzeArgs(
            AnalyzeArgsInput(
                repo = "/repo", diffMode = DiffMode.NoVcs, reportPath = "jacoco.xml", outPath = "/tmp/out.json",
                mutation = EvidenceInput(
                    classpaths = listOf(ClasspathBinding("root", "cp.txt")),
                    targets = listOf(TargetBinding("root", "dev.example.Calculator"), TargetBinding("root", "dev.example.Other")),
                ),
            ),
        )
        val targets = args.withIndex().filter { it.value == "--mutation-target" }.map { args[it.index + 1] }
        assertEquals(listOf("root=dev.example.Calculator", "root=dev.example.Other"), targets)
        assertTrue(args.contains("--no-vcs"), "the builder does not enforce the diff rule - a target lifts it CLI-side")
    }

    @Test
    fun `mutation without a timeout omits the flag rather than inventing the default`() {
        val args = buildAnalyzeArgs(
            AnalyzeArgsInput(
                repo = "/repo", diffMode = DiffMode.Uncommitted, reportPath = "jacoco.xml", outPath = "/tmp/out.json",
                mutation = EvidenceInput(classpaths = listOf(ClasspathBinding("root", "cp.txt"))),
            ),
        )
        assertFalse(args.contains("--mutation-timeout"))
    }

    @Test
    fun `no mutation input means no mutation flags at all`() {
        val args = buildAnalyzeArgs(AnalyzeArgsInput(repo = "/repo", diffMode = DiffMode.Uncommitted, reportPath = "jacoco.xml", outPath = "/tmp/out.json"))
        assertFalse(args.any { it.startsWith("--mutation") })
    }

    @Test
    fun `without a module, --report stays the bare single-module shorthand`() {
        val args = buildAnalyzeArgs(AnalyzeArgsInput(repo = "/repo", diffMode = DiffMode.NoVcs, reportPath = "jacoco.xml", outPath = "/tmp/out.json"))
        assertTrue(args.contains("--report"))
        assertEquals("jacoco.xml", args[args.indexOf("--report") + 1])
        assertFalse(args.contains("--module"))
    }

    @Test
    fun `a module binding adds --module and switches --report to the id=path form`() {
        val args = buildAnalyzeArgs(
            AnalyzeArgsInput(
                repo = "/repo", diffMode = DiffMode.NoVcs, reportPath = "gson/target/site/jacoco/jacoco.xml", outPath = "/tmp/out.json",
                module = "root" to "gson",
            ),
        )
        val moduleIndex = args.indexOf("--module")
        assertTrue(moduleIndex >= 0)
        assertEquals("root=gson", args[moduleIndex + 1])
        assertEquals("root=gson/target/site/jacoco/jacoco.xml", args[args.indexOf("--report") + 1])
    }

    /** `modules` is the real multi-module binding - repeats --module/--report once per entry, and takes priority over reportPath/module when both happen to be set. */
    @Test
    fun `modules repeats --module and --report once per entry, all --module flags before any --report`() {
        val args = buildAnalyzeArgs(
            AnalyzeArgsInput(
                repo = "/repo", diffMode = DiffMode.NoVcs, outPath = "/tmp/out.json",
                modules = listOf(
                    ModuleReportBinding("gson", "gson", "gson/target/site/jacoco/jacoco.xml"),
                    ModuleReportBinding("extras", "extras", "extras/target/site/jacoco/jacoco.xml"),
                ),
            ),
        )
        assertEquals(
            listOf("gson=gson", "extras=extras", "gson=gson/target/site/jacoco/jacoco.xml", "extras=extras/target/site/jacoco/jacoco.xml"),
            args.filter { it.contains("=") },
        )
    }

    @Test
    fun `an empty modules falls back to reportPath and module rather than emitting nothing`() {
        val args = buildAnalyzeArgs(AnalyzeArgsInput(repo = "/repo", diffMode = DiffMode.NoVcs, reportPath = "jacoco.xml", outPath = "/tmp/out.json", modules = emptyList()))
        assertEquals("jacoco.xml", args[args.indexOf("--report") + 1])
        assertFalse(args.contains("--module"))
    }
}
