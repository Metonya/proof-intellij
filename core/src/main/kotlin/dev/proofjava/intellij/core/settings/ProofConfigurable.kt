package dev.proofjava.intellij.core.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

private val DIFF_MODE_ITEMS = arrayOf(
    ProofSettingsState.DIFF_MODE_UNCOMMITTED,
    ProofSettingsState.DIFF_MODE_NO_VCS,
    ProofSettingsState.DIFF_MODE_BASE,
)

/** Registered as a `<projectConfigurable>` - "Proof" in IntelliJ's own Settings dialog. Plain Swing + [FormBuilder], matching the rest of this plugin's UI (no Kotlin UI DSL dependency introduced just for this one page). */
class ProofConfigurable(private val project: Project) : Configurable {
    private val jarPathField = JBTextField()
    private val javaExecutableField = JBTextField()
    private val diffModeCombo = ComboBox(DIFF_MODE_ITEMS)
    private val baseRefField = JBTextField()
    private var panel: JPanel? = null

    override fun getDisplayName(): String = "Proof"

    override fun createComponent(): JComponent {
        val built = FormBuilder.createFormBuilder()
            .addLabeledComponent("proof-java.jar path (blank = auto-detect):", jarPathField)
            .addLabeledComponent("Java executable (blank = this project's own SDK, falling back to \"java\"):", javaExecutableField)
            .addLabeledComponent("Diff mode:", diffModeCombo)
            .addLabeledComponent("Base ref (only used when diff mode is \"base\"):", baseRefField)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        panel = built
        reset()
        return built
    }

    override fun isModified(): Boolean {
        val settings = ProofSettingsState.getInstance(project)
        return jarPathField.text.orEmpty() != settings.jarPath.orEmpty() ||
            javaExecutableField.text.orEmpty() != settings.javaExecutable.orEmpty() ||
            diffModeCombo.selectedItem != settings.diffMode ||
            baseRefField.text.orEmpty() != settings.baseRef.orEmpty()
    }

    override fun apply() {
        val settings = ProofSettingsState.getInstance(project)
        settings.jarPath = jarPathField.text
        settings.javaExecutable = javaExecutableField.text
        settings.diffMode = diffModeCombo.selectedItem as? String ?: ProofSettingsState.DIFF_MODE_UNCOMMITTED
        settings.baseRef = baseRefField.text
    }

    override fun reset() {
        val settings = ProofSettingsState.getInstance(project)
        jarPathField.text = settings.jarPath.orEmpty()
        javaExecutableField.text = settings.javaExecutable.orEmpty()
        diffModeCombo.selectedItem = settings.diffMode
        baseRefField.text = settings.baseRef.orEmpty()
    }
}
