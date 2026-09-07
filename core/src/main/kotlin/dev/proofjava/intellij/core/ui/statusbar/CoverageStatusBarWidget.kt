package dev.proofjava.intellij.core.ui.statusbar

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import dev.proofjava.intellij.core.actions.toggleCoverage
import dev.proofjava.intellij.core.state.CoverageStateService
import dev.proofjava.intellij.core.verdict.Metric
import dev.proofjava.intellij.core.verdict.NewCodeCoverage
import com.intellij.util.Consumer
import java.awt.Component
import java.awt.event.MouseEvent

private const val WIDGET_ID = "dev.proofjava.intellij.statusBar"

/** Port of `proof-vscode/src/ui/statusBar.ts`. */
class CoverageStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = WIDGET_ID
    override fun getDisplayName(): String = "Proof Coverage"
    override fun isAvailable(project: Project): Boolean = true
    override fun createWidget(project: Project): StatusBarWidget = CoverageStatusBarWidget(project)
    override fun disposeWidget(widget: StatusBarWidget) = Disposer.dispose(widget)
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

class CoverageStatusBarWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.TextPresentation, Disposable {
    private var statusBar: StatusBar? = null
    private val listenerDisposable = Disposer.newDisposable("proof-java status bar listener")

    override fun ID(): String = WIDGET_ID

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        CoverageStateService.getInstance(project).addListener(listenerDisposable) { statusBar.updateWidget(WIDGET_ID) }
    }

    override fun dispose() {
        Disposer.dispose(listenerDisposable)
        statusBar = null
    }

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getAlignment(): Float = Component.LEFT_ALIGNMENT

    override fun getText(): String {
        val service = CoverageStateService.getInstance(project)
        val state = service.state ?: return "Proof"
        val percent = state.overall.sonarCompatible.percent ?: return "Proof"
        val eye = if (service.gutterVisible) "◉" else "○"
        return "$eye Proof $percent%"
    }

    override fun getTooltipText(): String {
        val service = CoverageStateService.getInstance(project)
        val state = service.state ?: return "Proof: no scan run yet"
        val visibility = if (service.gutterVisible) "on" else "off"
        val lines = mutableListOf(
            "Proof - coverage view is $visibility (click to toggle)",
            "",
            "Overall (whole repo)",
            metricLine("jacoco-line", state.overall.jacocoLine),
            metricLine("strict-line", state.overall.strictLine),
            metricLine("sonar-compatible", state.overall.sonarCompatible),
            "",
            "New Code (lines in this diff)",
            newCodeLines(state.newCode),
        )
        return lines.joinToString("\n")
    }

    override fun getClickConsumer(): Consumer<MouseEvent>? = Consumer { toggleCoverage(project) }
}

private fun newCodeLines(newCode: NewCodeCoverage): String = when (newCode) {
    is NewCodeCoverage.Status -> if (newCode.status == "unavailable_no_vcs") "cannot be computed in no-vcs mode" else "an error occurred during the diff"
    is NewCodeCoverage.Metrics -> listOf(
        metricLine("jacoco-line", newCode.metricSet.jacocoLine),
        metricLine("strict-line", newCode.metricSet.strictLine),
        metricLine("sonar-compatible", newCode.metricSet.sonarCompatible),
    ).joinToString("\n")
}

private fun metricLine(name: String, metric: Metric): String {
    val percentText = if (metric.percent == null) "n/a" else "${metric.percent}%"
    return "$name: $percentText (${metric.numerator}/${metric.denominator})"
}
