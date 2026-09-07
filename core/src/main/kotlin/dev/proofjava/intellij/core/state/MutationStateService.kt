package dev.proofjava.intellij.core.state

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import dev.proofjava.intellij.core.model.MutationState

/** Project-scoped holder for the last mutation run's L3 evidence - the [PerTestStateService]/[CoverageStateService] sibling for `mutation`, kept separate because all three have independent lifecycles (mirrors `model/store.ts`'s own separate `setMutationState`). */
@Service(Service.Level.PROJECT)
class MutationStateService : Disposable {
    @Volatile
    var state: MutationState? = null
        private set

    private val listeners = mutableListOf<() -> Unit>()

    fun publish(next: MutationState) {
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
        fun getInstance(project: Project): MutationStateService = project.getService(MutationStateService::class.java)
    }
}
