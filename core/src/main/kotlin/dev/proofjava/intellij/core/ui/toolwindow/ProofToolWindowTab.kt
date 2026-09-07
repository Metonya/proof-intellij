package dev.proofjava.intellij.core.ui.toolwindow

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import javax.swing.JComponent

/**
 * A pluggable tab the "Proof" tool window shows, contributed by an engine
 * module. `core` builds its own Coverage tab directly, but must never
 * construct an engine-specific one (`engine-java`'s Line → Tests view)
 * itself - the whole point of the core/engine-java boundary this plan is
 * built around. This extension point is the seam a tab like that
 * registers through instead, mirroring `core.engine.Engine`'s own
 * "extension point in `core`, implementation in `engine-java`, wired only
 * through `plugin.xml`" pattern.
 *
 * Not speculative ahead-of-need infrastructure: it replaces
 * `engine-java`'s own top-level "Proof: Line to Tests" tool window, which
 * real user feedback (a live `runIde` session, 2026-09-07) found
 * confusing as a second separate window rather than a tab of "Proof".
 */
interface ProofToolWindowTab {
    /** Shown as the tab's own title - keep it short, this is a tab label, not a sentence. */
    val title: String

    /** [project]-scoped: called once per tool window creation, same as [dev.proofjava.intellij.core.ui.toolwindow.CoverageToolWindowPanel]'s own construction. Implement [com.intellij.openapi.Disposable] on the returned component if it holds a listener subscription that needs unsubscribing - the caller checks for it and wires it to the tab's own `Content` lifecycle. */
    fun createComponent(project: Project): JComponent

    companion object {
        val EP_NAME: ExtensionPointName<ProofToolWindowTab> = ExtensionPointName.create("dev.proofjava.intellij.toolWindowTab")
    }
}
