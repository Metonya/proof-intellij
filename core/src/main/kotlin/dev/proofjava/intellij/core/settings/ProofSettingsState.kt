package dev.proofjava.intellij.core.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import dev.proofjava.intellij.core.cli.DiffMode

/**
 * Project-scoped, persisted equivalent of `proof-vscode`'s workspace
 * settings (`proof.jarPath`/`proof.diffMode`/`proof.baseRef`) - the first
 * real settings this plugin has (`QuickScanAction`/`DeepScanAction`
 * hardcoded [DiffMode.Uncommitted] until now, `JavaEngine.locateCli`
 * only ever tried the default jar search paths). Real user feedback
 * (2026-09-07): none of this was reachable from IntelliJ's own Settings
 * dialog at all.
 */
@Service(Service.Level.PROJECT)
@State(name = "ProofSettings", storages = [Storage("proof.xml")])
class ProofSettingsState : PersistentStateComponent<ProofSettingsState.State> {
    private var myState = State()

    /** `null`/blank means "use the engine's own default search paths" - never a guessed path. */
    var jarPath: String?
        get() = myState.jarPath?.takeIf { it.isNotBlank() }
        set(value) {
            myState.jarPath = value
        }

    /**
     * `null`/blank means "auto-detect" - `engine-java`'s own
     * `locateJavaExecutable` then prefers the IntelliJ Project SDK's own
     * `java` binary over a bare `"java"` inherited from whatever
     * environment launched the IDE process. Set this only when the
     * project's own SDK does not satisfy `--per-test-report`'s JDK <= 22
     * ceiling either (real user scenario, 2026-09-07: PIT's embedded
     * engine rejected a run launched under JDK 25).
     */
    var javaExecutable: String?
        get() = myState.javaExecutable?.takeIf { it.isNotBlank() }
        set(value) {
            myState.javaExecutable = value
        }

    var diffMode: String
        get() = myState.diffMode
        set(value) {
            myState.diffMode = value
        }

    /** Only meaningful when [diffMode] is [DIFF_MODE_BASE]. */
    var baseRef: String?
        get() = myState.baseRef?.takeIf { it.isNotBlank() }
        set(value) {
            myState.baseRef = value
        }

    fun toDiffMode(): DiffMode = when (diffMode) {
        DIFF_MODE_NO_VCS -> DiffMode.NoVcs
        DIFF_MODE_BASE -> DiffMode.Base(baseRef ?: "main")
        else -> DiffMode.Uncommitted
    }

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    class State {
        var jarPath: String? = null
        var javaExecutable: String? = null
        var diffMode: String = DIFF_MODE_UNCOMMITTED
        var baseRef: String? = null
    }

    companion object {
        const val DIFF_MODE_NO_VCS: String = "no-vcs"
        const val DIFF_MODE_UNCOMMITTED: String = "uncommitted"
        const val DIFF_MODE_BASE: String = "base"

        fun getInstance(project: Project): ProofSettingsState = project.getService(ProofSettingsState::class.java)
    }
}
