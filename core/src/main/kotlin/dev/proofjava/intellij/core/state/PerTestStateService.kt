package dev.proofjava.intellij.core.state

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import dev.proofjava.intellij.core.model.PerTestState

/** Project-scoped holder for the last Deep Scan's L2 evidence - the [CoverageStateService] sibling for `perTest`, kept separate because the two have independent lifecycles (mirrors `model/store.ts`'s own separate `setPerTestState`). */
@Service(Service.Level.PROJECT)
class PerTestStateService : Disposable {
    @Volatile
    var state: PerTestState? = null
        private set

    private val listeners = mutableListOf<() -> Unit>()

    fun publish(next: PerTestState) {
        state = next
        listeners.forEach { it() }
    }

    /** [listenerDisposable] controls unsubscription - independent of this service's own lifetime, which is the project's (same pattern as [CoverageStateService.addListener]). */
    fun addListener(listenerDisposable: Disposable, listener: () -> Unit) {
        listeners += listener
        Disposer.register(listenerDisposable) { listeners -= listener }
    }

    override fun dispose() = Unit

    companion object {
        fun getInstance(project: Project): PerTestStateService = project.getService(PerTestStateService::class.java)
    }
}
