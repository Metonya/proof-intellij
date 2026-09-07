package dev.proofjava.intellij.core.state

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import dev.proofjava.intellij.core.model.CoverageState

/**
 * The one place the last Quick Scan's data lives for a project - IntelliJ
 * equivalent of `proof-vscode/src/model/store.ts`'s module-level singleton,
 * but scoped per-[Project] (a light `@Service`) since IntelliJ can hold
 * several projects open in one process, unlike a VS Code extension host's
 * single workspace. UI surfaces (gutter, Coverage tool window) read this
 * to redraw without re-running `analyze`.
 *
 * Implements [Disposable] so it can serve as the parent disposable for
 * project-scoped listeners (the gutter's `EditorFactoryListener`) - a light
 * service implementing `Disposable` is disposed automatically when its
 * project closes, so anything registered against it is cleaned up the same
 * way `context.subscriptions` cleans up on extension deactivation in
 * proof-vscode.
 */
@Service(Service.Level.PROJECT)
class CoverageStateService : Disposable {
    @Volatile
    var state: CoverageState? = null
        private set

    /** A fresh scan always shows - the gutter-visibility toggle is a per-run choice, not sticky across runs (mirrors `setCoverageState` in the TS source). */
    @Volatile
    var gutterVisible: Boolean = true
        private set

    private val listeners = mutableListOf<() -> Unit>()

    fun publish(next: CoverageState) {
        state = next
        gutterVisible = true
        listeners.forEach { it() }
    }

    fun setGutterVisible(visible: Boolean) {
        gutterVisible = visible
        listeners.forEach { it() }
    }

    /** [listenerDisposable] controls unsubscription (e.g. the tool window's own panel) - independent of this service's own lifetime, which is the project's. */
    fun addListener(listenerDisposable: Disposable, listener: () -> Unit) {
        listeners += listener
        Disposer.register(listenerDisposable) { listeners -= listener }
    }

    /** Nothing to release directly - this service exists as a disposal anchor for listeners registered against it (see class doc). */
    override fun dispose() = Unit

    companion object {
        fun getInstance(project: Project): CoverageStateService = project.getService(CoverageStateService::class.java)
    }
}
