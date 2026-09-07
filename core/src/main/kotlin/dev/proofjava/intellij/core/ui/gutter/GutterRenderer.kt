package dev.proofjava.intellij.core.ui.gutter

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import dev.proofjava.intellij.core.model.LineState
import dev.proofjava.intellij.core.model.classifyLine
import dev.proofjava.intellij.core.model.mapLines
import dev.proofjava.intellij.core.state.CoverageStateService
import dev.proofjava.intellij.core.verdict.FileCoverageBlock
import java.awt.Color
import java.io.File

/**
 * IntelliJ port of `proof-vscode/src/ui/gutterRenderer.ts`.
 *
 * Deliberate visual adaptation, not a literal port: VS Code's
 * `TextEditorDecorationType` with `borderWidth: '0 0 0 3px'` (a thin
 * colored left-edge stripe) has no direct IntelliJ analogue - nothing in
 * the platform paints an arbitrary-width stripe on just one edge of a line
 * without custom gutter-icon painting. A translucent full-line background
 * tint is used instead: the same mechanism IntelliJ's own built-in
 * Coverage feature uses for exactly this purpose, so this reads as native
 * rather than as an imported VS Code idiom. Same intent (glanceable
 * per-line coverage state at a glance), the target platform's own idiom
 * for it - flagged here explicitly rather than silently changed.
 *
 * Deferred from this port: the "stale file" banner
 * (`proof-vscode`'s Faz 14e - marking a file's coverage stale after it's
 * edited post-scan). A real, disclosed gap, not an oversight - re-scanning
 * always shows correct data either way, staleness tracking only adds a
 * proactive warning on top.
 */

private val DEFAULT_PALETTE: Map<LineState, Color> = mapOf(
    LineState.COVERED to Color(0x4A, 0xDE, 0x80),
    LineState.PARTIAL to Color(0xFA, 0xCC, 0x15),
    LineState.UNCOVERED to Color(0xF8, 0x71, 0x71),
)

/** Okabe-Ito palette (Okabe & Ito, "Color Universal Design", 2008) - stays distinguishable under protanopia, deuteranopia and tritanopia simultaneously, unlike a single hand-picked "colorblind-friendly" pair. Same choice as `proof-vscode`'s `proof.colorblindMode`. */
private val COLORBLIND_PALETTE: Map<LineState, Color> = mapOf(
    LineState.COVERED to Color(0x00, 0x72, 0xB2),
    LineState.PARTIAL to Color(0xF0, 0xE4, 0x42),
    LineState.UNCOVERED to Color(0xD5, 0x5E, 0x00),
)

/** Kept low - a tint, not a solid fill, so syntax highlighting stays legible underneath. */
private const val BACKGROUND_ALPHA = 40

private val TRACKED_HIGHLIGHTERS: Key<MutableList<RangeHighlighter>> = Key.create("dev.proofjava.intellij.gutter.highlighters")

fun applyGutterCoverage(project: Project, projectRoot: String, block: FileCoverageBlock, colorblindMode: Boolean = false) {
    val palette = if (colorblindMode) COLORBLIND_PALETTE else DEFAULT_PALETTE
    val statesByAbsolutePath: Map<String, Map<Int, LineState>> = block.files.associate { entry ->
        val absolutePath = File(projectRoot, entry.path).path
        absolutePath to mapLines(entry.lines).associate { it.line to classifyLine(it) }
    }

    for (editor in editorsForProject(project)) {
        clearHighlighters(editor)
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: continue
        val states = statesByAbsolutePath[file.path] ?: continue
        paintEditor(editor, states, palette)
    }
}

fun clearGutterCoverage(project: Project) {
    for (editor in editorsForProject(project)) {
        clearHighlighters(editor)
    }
}

/**
 * Registers the listener that repaints a newly-opened editor from whatever
 * the last scan found - `setDecorations`/`RangeHighlighter`s are per-editor,
 * not global, so a file opened after a scan needs its marks applied by
 * hand (mirrors `extension.ts`'s `onDidChangeVisibleTextEditors` wiring).
 * Call once per project (from a `ProjectActivity`); the listener is
 * disposed automatically when [parentDisposable] (the project's own
 * [CoverageStateService]) is disposed.
 */
fun registerGutterReapplyListener(project: Project, parentDisposable: com.intellij.openapi.Disposable) {
    EditorFactory.getInstance().addEditorFactoryListener(
        object : EditorFactoryListener {
            override fun editorCreated(event: EditorFactoryEvent) {
                val editor = event.editor
                if (editor.project !== project) return
                val service = CoverageStateService.getInstance(project)
                val fileCoverage = service.state?.fileCoverage ?: return
                if (!service.gutterVisible) return
                val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
                val entry = fileCoverage.files.find { File(service.state!!.projectRoot, it.path).path == file.path } ?: return
                val states = mapLines(entry.lines).associate { it.line to classifyLine(it) }
                paintEditor(editor, states, DEFAULT_PALETTE)
            }
        },
        parentDisposable,
    )
}

private fun editorsForProject(project: Project): List<Editor> = EditorFactory.getInstance().allEditors.filter { it.project === project }

private fun paintEditor(editor: Editor, states: Map<Int, LineState>, palette: Map<LineState, Color>) {
    val document = editor.document
    val markupModel = editor.markupModel
    val highlighters = mutableListOf<RangeHighlighter>()
    for ((line, state) in states) {
        val lineIndex = line - 1
        if (lineIndex < 0 || lineIndex >= document.lineCount) continue
        val base = palette.getValue(state)
        val attributes = TextAttributes().apply { backgroundColor = Color(base.red, base.green, base.blue, BACKGROUND_ALPHA) }
        val highlighter = markupModel.addRangeHighlighter(
            document.getLineStartOffset(lineIndex),
            document.getLineEndOffset(lineIndex),
            HighlighterLayer.CARET_ROW - 1,
            attributes,
            HighlighterTargetArea.EXACT_RANGE,
        )
        highlighters += highlighter
    }
    editor.putUserData(TRACKED_HIGHLIGHTERS, highlighters)
}

private fun clearHighlighters(editor: Editor) {
    editor.getUserData(TRACKED_HIGHLIGHTERS)?.forEach { if (it.isValid) editor.markupModel.removeHighlighter(it) }
    editor.putUserData(TRACKED_HIGHLIGHTERS, null)
}
