package dev.proofjava.intellij.core.model

import dev.proofjava.intellij.core.verdict.ChangedFile
import dev.proofjava.intellij.core.verdict.FileCoverageBlock
import dev.proofjava.intellij.core.verdict.Finding
import dev.proofjava.intellij.core.verdict.MetricSet
import dev.proofjava.intellij.core.verdict.ModuleInput
import dev.proofjava.intellij.core.verdict.NewCodeCoverage
import dev.proofjava.intellij.core.verdict.Reason
import dev.proofjava.intellij.core.verdict.VerdictDocument

/**
 * Port of `proof-vscode/src/model/store.ts`'s `CoverageState` shape - the
 * last analyze run's data, kept so the UI can redecorate editors and feed
 * the Coverage tree without re-running `analyze`. Unlike the TS source
 * (one module-level singleton, since a VS Code extension host is one
 * workspace), the mutable holder here is a project-level service
 * (`dev.proofjava.intellij.core.state.CoverageStateService`) - IntelliJ can
 * have multiple projects open in one process.
 */
data class CoverageState(
    val projectRoot: String,
    val fileCoverage: FileCoverageBlock?,
    val overall: MetricSet,
    val newCode: NewCodeCoverage,
    val changedFiles: List<ChangedFile>,
    val findings: List<Finding>,
    val warnings: List<Reason>,
    val modules: List<ModuleInput>,
)

fun coverageStateFrom(projectRoot: String, document: VerdictDocument): CoverageState = CoverageState(
    projectRoot = projectRoot,
    fileCoverage = document.fileCoverage,
    overall = document.coverage.overall,
    newCode = document.coverage.newCode,
    changedFiles = document.changedFiles,
    findings = document.findings,
    warnings = document.warnings,
    modules = document.inputs.modules,
)
