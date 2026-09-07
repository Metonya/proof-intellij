package dev.proofjava.intellij.core.cli

/**
 * Pure port of `proof-vscode/src/cli/argsBuilder.ts`: builds the `analyze`
 * argv from already-resolved inputs, never touches the filesystem or any
 * IntelliJ Platform SDK class - unit-testable with plain JUnit, same "no
 * `vscode` import" discipline as the TS source, translated to "no
 * `com.intellij.*` import".
 *
 * Two report-binding shapes (mirrors the TS side's own Faz 29/Faz 30
 * generalization):
 *   - [AnalyzeArgsInput.reportPath] (+ optional [AnalyzeArgsInput.module]) -
 *     the single-module shorthand. Omitting `module` reproduces the exact
 *     byte-identical bare `--report <path>` invocation.
 *   - [AnalyzeArgsInput.modules] - a real multi-module run: repeats
 *     `--module <id>=<root>` and `--report <id>=<path>` once per bound
 *     module. Takes priority over `reportPath`/`module` when both are given.
 */

sealed interface DiffMode {
    data object NoVcs : DiffMode
    data object Uncommitted : DiffMode
    data class Base(val ref: String) : DiffMode
}

data class ModuleReportBinding(val id: String, val root: String, val reportPath: String)

data class ClasspathBinding(val moduleId: String, val path: String)

data class TargetBinding(val moduleId: String, val fqcn: String)

/** Shared shape between `perTest` and `mutation` evidence collection. */
data class EvidenceInput(
    val classpaths: List<ClasspathBinding>,
    val targets: List<TargetBinding> = emptyList(),
    val timeoutSeconds: Int? = null,
)

data class AnalyzeArgsInput(
    val repo: String,
    val diffMode: DiffMode,
    /** Single-module shorthand - see file doc comment. Ignored when [modules] is given. */
    val reportPath: String? = null,
    /** Single-module shorthand's optional explicit module binding. Ignored when [modules] is given. */
    val module: Pair<String, String>? = null,
    /** Real multi-module binding, repeated `--module`/`--report`. */
    val modules: List<ModuleReportBinding> = emptyList(),
    val outPath: String,
    val fileCoverage: Boolean = false,
    /** Sonar-style `sonar.coverage.exclusions` globs (D-05) - passed through verbatim, never merged with a repo's own `proof.config.json`. */
    val coverageExclusions: List<String> = emptyList(),
    /**
     * L2 per-test evidence. [EvidenceInput.targets] names explicit FQCNs via
     * `--per-test-target`, the CLI's diff-free entry point - when given, it
     * lifts the CLI's own `--no-vcs` rejection, so this builder does not
     * need to know or enforce the diff-mode rule itself.
     */
    val perTest: EvidenceInput? = null,
    /** L3 mutation evidence - same shape and rule as [perTest]. */
    val mutation: EvidenceInput? = null,
)

fun buildAnalyzeArgs(input: AnalyzeArgsInput): List<String> {
    val args = mutableListOf("analyze", "--repo", input.repo)
    appendDiffMode(args, input.diffMode)
    appendReportBinding(args, input)

    if (input.fileCoverage) {
        args += "--file-coverage"
    }
    if (input.coverageExclusions.isNotEmpty()) {
        args += "--coverage-exclusions"
        args += input.coverageExclusions.joinToString(",")
    }
    input.perTest?.let {
        appendEvidenceFlags(args, "--per-test-report", "--per-test-classpath", "--per-test-target", it)
        it.timeoutSeconds?.let { seconds -> args += listOf("--per-test-timeout", seconds.toString()) }
    }
    input.mutation?.let {
        appendEvidenceFlags(args, "--mutation-report", "--mutation-classpath", "--mutation-target", it)
        it.timeoutSeconds?.let { seconds -> args += listOf("--mutation-timeout", seconds.toString()) }
    }
    args += listOf("--out", input.outPath)

    return args
}

private fun appendDiffMode(args: MutableList<String>, diffMode: DiffMode) {
    when (diffMode) {
        is DiffMode.NoVcs -> args += "--no-vcs"
        is DiffMode.Uncommitted -> args += "--uncommitted"
        is DiffMode.Base -> args += listOf("--base", diffMode.ref)
    }
}

private fun appendReportBinding(args: MutableList<String>, input: AnalyzeArgsInput) {
    if (input.modules.isNotEmpty()) {
        for (m in input.modules) {
            args += listOf("--module", "${m.id}=${m.root}")
        }
        for (m in input.modules) {
            args += listOf("--report", "${m.id}=${m.reportPath}")
        }
    } else if (input.module != null) {
        val (id, root) = input.module
        args += listOf("--module", "$id=$root", "--report", "$id=${input.reportPath}")
    } else if (input.reportPath != null) {
        args += listOf("--report", input.reportPath)
    }
}

/** One report-enabling flag, one `--*-classpath <id>=<path>` per bound module, one `--*-target <id>=<fqcn>` per explicit target. */
private fun appendEvidenceFlags(args: MutableList<String>, reportFlag: String, classpathFlag: String, targetFlag: String, evidence: EvidenceInput) {
    args += reportFlag
    for (cp in evidence.classpaths) {
        args += listOf(classpathFlag, "${cp.moduleId}=${cp.path}")
    }
    for (target in evidence.targets) {
        args += listOf(targetFlag, "${target.moduleId}=${target.fqcn}")
    }
}
