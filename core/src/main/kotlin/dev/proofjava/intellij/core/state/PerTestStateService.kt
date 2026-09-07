package dev.proofjava.intellij.core.state

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import dev.proofjava.intellij.core.model.PerTestState

/** Project-scoped holder for the last Deep Scan's L2 evidence - the [dev.proofjava.intellij.core.state.CoverageStateService] sibling for `perTest`, kept separate because the two have independent lifecycles (mirrors `model/store.ts`'s own separate `setPerTestState`). */
@Service(Service.Level.PROJECT)
class PerTestStateService {
    @Volatile
    var state: PerTestState? = null
        private set

    fun publish(next: PerTestState) {
        state = next
    }

    companion object {
        fun getInstance(project: Project): PerTestStateService = project.getService(PerTestStateService::class.java)
    }
}
