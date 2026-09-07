package dev.proofjava.intellij.core.cli

import dev.proofjava.intellij.core.engine.CliLocation

/**
 * Port of `proof-vscode/src/cli/doctorRunner.ts`: spawns `<cli> doctor
 * --repo <root> [--fix] [--write-config]`. Lives in `core.cli`, not an
 * engine module or the `Engine` interface itself - the invocation *shape*
 * is uniform across any future engine (the CLI-side fact that
 * Maven/Gradle-specific work happens behind `doctor --fix` is an
 * implementation detail of proof-java's own binary, not a reason for the
 * IDE-side call to be engine-specific; see the plan's "Komut/argv parity"
 * section). What differs by engine is what *orchestrates* calling this -
 * that belongs in an engine module (a later milestone), not here.
 *
 * Only exit code + stdout/stderr are consumed (`doctor` has no `--json`);
 * the caller decides what any of it means. `doctor`'s own prose always
 * goes to the log verbatim, never parsed for control flow beyond
 * [parseDoctorProgressLine]'s narrow progress-line contract.
 */

data class DoctorOptions(
    val fix: Boolean = false,
    val writeConfig: Boolean = false,
    val environment: Map<String, String> = emptyMap(),
    val onStderrLine: ((String) -> Unit)? = null,
)

data class DoctorResult(val exitCode: Int?, val stdout: String, val stderr: String)

fun runDoctor(cli: CliLocation, repo: String, options: DoctorOptions = DoctorOptions()): RunHandle {
    val args = buildList {
        add("doctor")
        add("--repo")
        add(repo)
        if (options.fix) add("--fix")
        if (options.writeConfig) add("--write-config")
    }
    return run(
        RunOptions(
            executable = cli.executable,
            jarPath = cli.jarPath,
            args = args,
            environment = options.environment,
            onStderrLine = options.onStderrLine,
        ),
    )
}
