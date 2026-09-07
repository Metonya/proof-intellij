package dev.proofjava.intellij.engine.java.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.vfs.LocalFileSystem
import dev.proofjava.intellij.engine.java.skill.SKILL_TARGETS
import dev.proofjava.intellij.engine.java.skill.SkillScope
import dev.proofjava.intellij.engine.java.skill.SkillTarget
import dev.proofjava.intellij.engine.java.skill.fetchSkillFiles
import java.io.File

/**
 * Port of `proof-vscode`'s `proof.installSkill` command (`ui/skillInstaller.ts`) -
 * two sequential choices (AI tool, then scope when the tool supports more
 * than one), same UX shape as the TS source's own two sequential
 * QuickPicks - not a nested popup submenu, to stay faithful to that flow
 * rather than inventing a different one. Real, disclosed gap closed - M8
 * (proof-vscode's own "Faz 34" skill-install convenience).
 */
class InstallSkillAction : AnAction("Proof: Install Skill") {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val repo = project.basePath
        val availableTargets = if (repo != null) SKILL_TARGETS else SKILL_TARGETS.filter { SkillScope.USER in it.scopes }

        val popup = JBPopupFactory.getInstance().createListPopup(
            object : BaseListPopupStep<SkillTarget>("Proof: Install Skill For – choose the AI tool", availableTargets) {
                override fun getTextFor(value: SkillTarget): String = "${value.label} – ${value.description}"

                override fun onChosen(selectedValue: SkillTarget, finalChoice: Boolean): PopupStep<*>? {
                    if (selectedValue.scopes.size == 1) {
                        runInstallSkill(project, selectedValue, selectedValue.scopes[0], repo)
                    } else {
                        ApplicationManager.getApplication().invokeLater { showScopePopup(project, selectedValue, repo) }
                    }
                    return PopupStep.FINAL_CHOICE
                }
            },
        )
        popup.showCenteredInCurrentWindow(project)
    }
}

private data class ScopePick(val scope: SkillScope, val label: String, val description: String)

private fun showScopePopup(project: Project, target: SkillTarget, repo: String?) {
    val picks = target.scopes.map {
        if (it == SkillScope.WORKSPACE) {
            ScopePick(it, "Workspace", "committed to this repo, shared with the team")
        } else {
            ScopePick(it, "User", "this machine only, every project")
        }
    }
    val popup = JBPopupFactory.getInstance().createListPopup(
        object : BaseListPopupStep<ScopePick>("Proof: Install Scope", picks) {
            override fun getTextFor(value: ScopePick): String = "${value.label} – ${value.description}"

            override fun onChosen(selectedValue: ScopePick, finalChoice: Boolean): PopupStep<*>? {
                runInstallSkill(project, target, selectedValue.scope, repo)
                return PopupStep.FINAL_CHOICE
            }
        },
    )
    popup.showCenteredInCurrentWindow(project)
}

private fun runInstallSkill(project: Project, target: SkillTarget, scope: SkillScope, repo: String?) {
    if (scope == SkillScope.WORKSPACE && repo == null) {
        showSkillErrorLater(project, "Proof: open a project first.")
        return
    }
    val destDir = target.resolveDir(scope, repo ?: "")

    object : Task.Backgroundable(project, "Proof: fetching the latest skill from GitHub", true) {
        override fun run(indicator: ProgressIndicator) {
            val files = try {
                fetchSkillFiles(indicator)
            } catch (e: Exception) {
                showSkillErrorLater(project, "Proof: could not fetch the skill from GitHub: ${e.message}")
                return
            }
            try {
                for (file in files) {
                    val outFile = File(destDir, file.relativePath.replace('/', File.separatorChar))
                    outFile.parentFile?.mkdirs()
                    outFile.writeBytes(file.content)
                }
            } catch (e: Exception) {
                showSkillErrorLater(project, "Proof: fetched the skill but could not write it to $destDir: ${e.message}")
                return
            }

            ApplicationManager.getApplication().invokeLater {
                val choice = Messages.showYesNoDialog(
                    project,
                    "Installed the skill to $destDir (${files.size} file(s)).",
                    "Proof",
                    "Open SKILL.md",
                    "OK",
                    Messages.getInformationIcon(),
                )
                if (choice == Messages.YES) {
                    val skillMdPath = File(destDir, "SKILL.md").path
                    val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(skillMdPath)
                    if (virtualFile != null) {
                        FileEditorManager.getInstance(project).openFile(virtualFile, true)
                    }
                }
            }
        }
    }.queue()
}

private fun showSkillErrorLater(project: Project, message: String) {
    ApplicationManager.getApplication().invokeLater { Messages.showErrorDialog(project, message, "Proof") }
}
