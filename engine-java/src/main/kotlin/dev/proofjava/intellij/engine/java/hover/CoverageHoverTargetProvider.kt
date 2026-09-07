package dev.proofjava.intellij.engine.java.hover

import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.psi.PsiFile
import dev.proofjava.intellij.core.state.CoverageStateService
import dev.proofjava.intellij.core.state.PerTestStateService
import dev.proofjava.intellij.core.util.toRepoRelativePath

/**
 * SDK wiring for [computeHover] - the IntelliJ equivalent of
 * `proof-vscode/src/ui/hoverProvider.ts`'s `registerHoverProvider`.
 * Registered as `com.intellij.platform.backend.documentation.targetProvider`
 * (not `psiTargetProvider`/`symbolTargetProvider`): it is handed a raw
 * `(PsiFile, offset)` pair by the platform's unified hover popup and never
 * resolves a PSI element itself, matching the plugin's "no Java PSI/
 * `com.intellij.java` dependency" constraint - every open buffer (Java or
 * otherwise) already carries a base `PsiFile` wrapper over its `Document`,
 * which is all this needs. Gated to `.java` files by extension here, since
 * this extension point carries no language filter of its own (unlike
 * `vscode.languages.registerHoverProvider({ language: 'java' }, ...)`).
 */
class CoverageHoverTargetProvider : DocumentationTargetProvider {

    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        val virtualFile = file.virtualFile ?: return emptyList()
        if (virtualFile.extension != "java") {
            return emptyList()
        }
        val document = file.viewProvider.document ?: return emptyList()
        if (offset < 0 || offset > document.textLength) {
            return emptyList()
        }

        val project = file.project
        val coverageState = CoverageStateService.getInstance(project).state
        val perTest = PerTestStateService.getInstance(project).state?.perTest
        val fileText = document.charsSequence.toString()
        val lineNumber = document.getLineNumber(offset) + 1
        val repoRelativePath = coverageState?.let { toRepoRelativePath(it.projectRoot, virtualFile.path) }
        val wordAtCursor = wordAtOffset(document.charsSequence, offset)

        val content = computeHover(
            coverageState = coverageState,
            perTest = perTest,
            fileText = fileText,
            fileBaseNameWithoutExtension = virtualFile.nameWithoutExtension,
            repoRelativePath = repoRelativePath,
            lineNumber = lineNumber,
            wordAtCursor = wordAtCursor,
        ) ?: return emptyList()

        return listOf(CoverageHoverTarget(project, content))
    }
}

/** Java-identifier word boundaries around [offset] - the platform equivalent of `document.getWordRangeAtPosition`, which has no non-PSI counterpart in the IntelliJ Platform SDK. */
private fun wordAtOffset(text: CharSequence, offset: Int): String? {
    if (text.isEmpty()) return null
    var start = offset
    while (start > 0 && isJavaIdentifierChar(text[start - 1])) start--
    var end = offset
    while (end < text.length && isJavaIdentifierChar(text[end])) end++
    return if (start < end) text.subSequence(start, end).toString() else null
}

private fun isJavaIdentifierChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_' || c == '$'
