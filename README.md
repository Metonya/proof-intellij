# Proof for IntelliJ IDEA

See which of your passing tests actually prove anything, right in the
editor.

Coverage tells you a line executed. It doesn't tell you that anything
checked what that line did. This plugin runs [proof-java](https://github.com/Metonya/proof-java)
against your project and shows its verdict directly in the editor: which
lines are covered, which of the tests covering them have no real
assertion, and, when you ask for it, which mutations nothing catches.

It's the IntelliJ IDEA counterpart to [proof-vscode](https://github.com/Metonya/proof-vscode)
- same CLI, same JSON contract, same underlying claims - built on the
IntelliJ Platform SDK instead of the VS Code extension API.

## Status

Under active development, not yet published to the JetBrains Marketplace.
Build and run it from source (see below). Only Java projects (Maven or
Gradle) are supported today; the `core` module is kept language-agnostic
on purpose so a future engine (Python, say) could plug in later without
rewriting it - see [Architecture](#architecture).

**Works today:**

- Quick Scan, Run Tests (Maven and Gradle, multi-module), and Deep Scan
  (per-test line evidence)
- Coverage gutter, full-line tint by line status (covered, partially
  covered, uncovered, excluded, or *oracleless* - covered, but by tests
  with no real assertion)
- Coverage tool window and a Line → Tests tool window (for a production
  line: which tests cover it and whether each has a real oracle; for a
  test: which production lines it actually exercises)
- Status bar widget and coverage badges in the project view
- Editor hover with the same two directions as the Line → Tests view,
  right where you're looking - no PSI/Java-plugin dependency, so it stays
  usable even before a second engine ever needs one
- Toggle Coverage View

**Not built yet:**

- Mutation testing (PIT) and its own view
- A dedicated, filterable Test Quality view (the underlying data already
  exists; there's no standalone view for it yet)
- Problems/Diagnostics panel integration
- HTML report export
- An in-IDE "download proof-java.jar" action and a Settings/Configurable
  UI - the jar is currently only found via the same default search paths
  `proof-vscode` uses

## Requirements

- IntelliJ IDEA Community 2026.2 or newer.
- A Maven or Gradle project with a JaCoCo report. Quick Scan finds either
  `target/site/jacoco/jacoco.xml` (Maven) or
  `build/reports/jacoco/test/jacocoTestReport.xml` (Gradle) automatically.
  Run Tests and Deep Scan work for a plain-Java Gradle project too,
  provided it has a committed Gradle Wrapper (`gradlew`/`gradlew.bat`) -
  this plugin only ever runs a project's own pinned wrapper, never a bare
  `gradle` on `PATH`. See [`proof-java`](https://github.com/Metonya/proof-java)'s
  own README for the full build-tool support matrix.
- `proof-java.jar` already present at one of its default search
  locations. See [`proof-java`](https://github.com/Metonya/proof-java)
  for what it does and how to get it.

## Building from source

Requires JDK 25 to compile against the IntelliJ Platform 2026.2.x line.

```
git clone https://github.com/Metonya/proof-intellij.git
cd proof-intellij
./gradlew runIde
```

`runIde` launches a sandboxed IntelliJ IDEA instance with the plugin
installed. `./gradlew buildPlugin` produces an installable `.zip` under
`build/distributions/`.

## Architecture

The project is split into two Gradle modules plus the root assembly
project:

- `core` - language- and build-tool-agnostic: the verdict JSON contract,
  the CLI runner, and every UI surface (gutter, tool windows, status bar,
  hover wiring) that only ever consumes that JSON. `core` never imports
  anything from `engine-java` - enforced at compile time, not just by
  convention.
- `engine-java` - everything specific to Java, Maven, Gradle, and JUnit:
  build-tool detection, module discovery, classpath resolution, and the
  `JavaEngine` implementation of `core`'s `Engine` extension point.
- The root project applies the IntelliJ Platform Gradle plugin and holds
  `plugin.xml`, composing `core` and `engine-java` into one plugin
  artifact.

This mirrors a real split already implicit in
[`proof-vscode`](https://github.com/Metonya/proof-vscode)'s own source
tree, made structural here: a second engine for another language would
add a new module next to `engine-java`, not touch `core`.

## License

[Apache License 2.0](LICENSE)
