package dev.proofjava.intellij.core.actions

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import dev.proofjava.intellij.core.engine.ClassTarget
import dev.proofjava.intellij.core.engine.Engine
import dev.proofjava.intellij.core.util.toRepoRelativePath

/**
 * The active editor's own class - port of `commands.ts`'s
 * `runPerTestForFile`/`runMutationForFile` shared "which file, which
 * class" preamble (both call `vscode.window.activeTextEditor` +
 * `detectClassName` the same way). Shared by [runDeepScanForFile] and
 * [runMutationForFile] (M7 part 4) - the single-file Deep Scan/Mutation
 * entry points, the TS source's own primary/cheapest recommended
 * commands, deferred until now for lack of exactly this.
 *
 * Must be called on the EDT (the active editor is UI state) - callers
 * queue their `Task.Backgroundable` only after this resolves, same
 * reasoning [RunTestsAction]'s own module-picker dialog already runs
 * before queuing its background task.
 */
internal fun activeFileClassTarget(project: Project, engine: Engine): ClassTarget? {
    val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null
    val virtualFile = FileDocumentManager.getInstance().getFile(editor.document) ?: return null
    if (!engine.ownsFile(virtualFile.name)) return null
    val repo = project.basePath ?: return null
    val repoRelativePath = toRepoRelativePath(repo, virtualFile.path) ?: return null
    val className = engine.classNameFor(editor.document.text, virtualFile.nameWithoutExtension)
    return ClassTarget(repoRelativePath, className)
}
