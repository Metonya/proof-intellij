package dev.proofjava.intellij.core.startup

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import dev.proofjava.intellij.core.state.CoverageStateService
import dev.proofjava.intellij.core.ui.gutter.registerGutterReapplyListener

/**
 * Registers the gutter's "repaint a newly-opened editor" listener once per
 * project (`postStartupActivity` extension point) - the modern (2023.1+)
 * replacement for the deprecated `StartupActivity`. The listener's
 * lifetime is tied to the project's own [CoverageStateService], so it is
 * cleaned up automatically when the project closes.
 */
class GutterReapplyActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        registerGutterReapplyListener(project, CoverageStateService.getInstance(project))
    }
}
