package dev.proofjava.intellij.engine.java.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import dev.proofjava.intellij.engine.java.locator.downloadLatestJar
import dev.proofjava.intellij.engine.java.locator.userJarPath
import dev.proofjava.intellij.engine.java.locator.workspaceJarPath
import java.io.File

/**
 * Port of `proof-vscode`'s `proof.downloadJar` command (`ui/jarDownloaderUi.ts`) -
 * "Workspace" and "User" both write to a location [dev.proofjava.intellij.engine.java.locator.locateJar]'s
 * own default search order already checks, so nothing else needs to change
 * afterward (no `jarPath` setting to set, no reload). Real, disclosed gap
 * closed - M8 (proof-vscode's own "Faz 34" jar-download convenience).
 */
class DownloadJarAction : AnAction("Proof: Download proof-java.jar") {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private data class ScopeChoice(val label: String, val description: String, val destPath: String)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val repo = project.basePath

        val choices = buildList {
            if (repo != null) {
                add(ScopeChoice("Workspace", ".proof-java/proof-java.jar in this project only", workspaceJarPath(repo)))
            }
            add(ScopeChoice("User", "~/.proof-java/proof-java.jar - shared by every project on this machine", userJarPath()))
        }

        val popup = JBPopupFactory.getInstance().createListPopup(
            object : BaseListPopupStep<ScopeChoice>("Proof: Download proof-java.jar - Where should the jar be installed?", choices) {
                override fun getTextFor(value: ScopeChoice): String = "${value.label} – ${value.description}"

                override fun onChosen(selectedValue: ScopeChoice, finalChoice: Boolean): PopupStep<*>? {
                    runDownloadJar(project, selectedValue.destPath)
                    return PopupStep.FINAL_CHOICE
                }
            },
        )
        popup.showCenteredInCurrentWindow(project)
    }
}

private fun runDownloadJar(project: Project, destPath: String) {
    object : Task.Backgroundable(project, "Proof: downloading proof-java.jar from GitHub", true) {
        override fun run(indicator: ProgressIndicator) {
            val result = try {
                downloadLatestJar(indicator)
            } catch (e: Exception) {
                showJarErrorLater(project, "Proof: could not download proof-java.jar: ${e.message}")
                return
            }
            try {
                val file = File(destPath)
                file.parentFile?.mkdirs()
                file.writeBytes(result.content)
            } catch (e: Exception) {
                showJarErrorLater(project, "Proof: downloaded proof-java.jar but could not write it to $destPath: ${e.message}")
                return
            }
            ApplicationManager.getApplication().invokeLater {
                Messages.showInfoMessage(project, "Installed proof-java.jar ${result.version} to $destPath.", "Proof")
            }
        }
    }.queue()
}

private fun showJarErrorLater(project: Project, message: String) {
    ApplicationManager.getApplication().invokeLater { Messages.showErrorDialog(project, message, "Proof") }
}
