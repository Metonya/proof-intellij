package dev.proofjava.intellij.core.cli

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.util.Key
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

/**
 * Port of `proof-vscode/src/cli/runner.ts`: spawns `<executable> [-jar
 * <jarPath>] <args>` and line-buffers stdout/stderr separately - stdout
 * carries the CLI's own text report, stderr carries transient progress
 * (D-64's contract on the CLI side; see [parseProgressLine]).
 *
 * The one class in `core.cli` that legitimately imports `com.intellij.*`
 * (per the plan's SDK-mapping table) - process spawning is inherently a
 * platform concern, unlike [buildAnalyzeArgs]/[parseProgressLine]/the
 * `core.verdict` package, which stay pure and unit-testable without an IDE.
 */

data class RunOptions(
    val executable: String,
    /** `null` runs [executable] with [args] directly, no `-jar` wrapper - lets this same runner invoke a non-jar executable (e.g. a build tool) too. */
    val jarPath: String? = null,
    val args: List<String>,
    val workDirectory: String? = null,
    /** Empty inherits the caller's own environment untouched (`GeneralCommandLine`'s default). */
    val environment: Map<String, String> = emptyMap(),
    val onStdoutLine: ((String) -> Unit)? = null,
    val onStderrLine: ((String) -> Unit)? = null,
)

data class RunResult(
    val exitCode: Int?,
    val stdout: String,
    val stderr: String,
)

class RunHandle internal constructor(
    val result: CompletableFuture<RunResult>,
    private val cancelAction: () -> Unit,
) {
    fun cancel() = cancelAction()
}

fun run(options: RunOptions): RunHandle {
    val future = CompletableFuture<RunResult>()

    val commandLine = GeneralCommandLine()
        .withExePath(options.executable)
        .withParameters(buildList {
            options.jarPath?.let { addAll(listOf("-jar", it)) }
            addAll(options.args)
        })
    options.workDirectory?.let { commandLine.withWorkDirectory(File(it)) }
    if (options.environment.isNotEmpty()) {
        commandLine.withEnvironment(options.environment)
    }

    val handler = try {
        OSProcessHandler(commandLine)
    } catch (e: ExecutionException) {
        // Mirrors the TS source's `child.on('error', reject)`: a spawn
        // failure (executable not found, permission denied) surfaces
        // through the result future, never a synchronous throw from `run`.
        future.completeExceptionally(e)
        return RunHandle(future) { /* never started - nothing to cancel */ }
    }

    val stdoutAll = StringBuilder()
    val stderrAll = StringBuilder()
    val stdoutBuffer = LineBuffer { line -> stdoutAll.append(line).append('\n'); options.onStdoutLine?.invoke(line) }
    val stderrBuffer = LineBuffer { line -> stderrAll.append(line).append('\n'); options.onStderrLine?.invoke(line) }

    handler.addProcessListener(object : ProcessListener {
        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
            when {
                outputType === ProcessOutputTypes.STDOUT -> stdoutBuffer.push(event.text)
                outputType === ProcessOutputTypes.STDERR -> stderrBuffer.push(event.text)
            }
        }

        override fun processTerminated(event: ProcessEvent) {
            stdoutBuffer.flush()
            stderrBuffer.flush()
            future.complete(RunResult(event.exitCode, stdoutAll.toString(), stderrAll.toString()))
        }
    })
    handler.startNotify()

    return RunHandle(future) { killTree(handler.process) }
}

/**
 * A bare `Process.destroy()` only reaches the direct child - PIT spawns its
 * own child "minion" JVMs, and killing only the parent leaves those running
 * (a real bug on the proof-vscode side: Faz 20, minion JVMs burning CPU for
 * minutes and locking the classpath for the next run). Uses the JDK's own
 * cross-platform [ProcessHandle.descendants] rather than replicating the TS
 * source's platform-specific `SIGTERM`-on-POSIX/`taskkill /T /F`-on-Windows
 * split - what actually matters (killing the whole tree) is verified to
 * work the same way on every platform through this standard API, instead
 * of trusting unverified platform-specific behavior.
 */
private fun killTree(process: Process) {
    val handle = process.toHandle()
    handle.descendants().forEach { it.destroyForcibly() }
    handle.destroyForcibly()
}

/** Buffers partial chunks until a full line is available - `onTextAvailable` gives no guarantee a chunk boundary lands on a newline. Direct port of `lineBuffer` in the TS source. */
private class LineBuffer(private val onLine: (String) -> Unit) {
    private val buffer = StringBuilder()

    fun push(chunk: String) {
        buffer.append(chunk)
        var newlineIndex = buffer.indexOf("\n")
        while (newlineIndex >= 0) {
            onLine(stripTrailingCr(buffer.substring(0, newlineIndex)))
            buffer.delete(0, newlineIndex + 1)
            newlineIndex = buffer.indexOf("\n")
        }
    }

    fun flush() {
        if (buffer.isNotEmpty()) {
            onLine(stripTrailingCr(buffer.toString()))
            buffer.clear()
        }
    }

    private fun stripTrailingCr(line: String): String = line.removeSuffix("\r")
}
