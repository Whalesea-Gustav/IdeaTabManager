package com.whalesea.ideatabmanager.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VirtualFile
import com.whalesea.ideatabmanager.model.TabGroupRecord
import com.whalesea.ideatabmanager.service.TabGroupProjectState

/** Dynamic Project View submenu: recent groups are one click away, with a full chooser as fallback. */
class AddSelectedProjectFilesToGroupActionGroup : ActionGroup("Add Selected Files to Group", true), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null && selectedFiles(event).isNotEmpty()
    }

    override fun getChildren(event: AnActionEvent?): Array<AnAction> {
        val project = event?.project ?: return emptyArray()
        val files = selectedFiles(event)
        if (files.isEmpty()) return emptyArray()

        val allGroups = project.service<TabGroupProjectState>().groups()
        if (allGroups.isEmpty()) return arrayOf(disabledAction("Create a tab group first"))

        val recent = project.service<TabGroupProjectState>().recentGroups()
        val actions = recent.map { group ->
            object : DumbAwareAction(groupLabel(group)) {
                override fun actionPerformed(actionEvent: AnActionEvent) {
                    TabGroupCommands.addFilesToGroup(project, group, files)
                }
            }
        }.toMutableList<AnAction>()
        if (allGroups.size > recent.size) {
            actions += object : DumbAwareAction("More Groups…") {
                override fun actionPerformed(actionEvent: AnActionEvent) {
                    TabGroupCommands.addFilesToChosenGroup(project, files)
                }
            }
        }
        return actions.toTypedArray()
    }

    private fun selectedFiles(event: AnActionEvent): List<VirtualFile> {
        val array = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(event.dataContext)
            ?: CommonDataKeys.VIRTUAL_FILE.getData(event.dataContext)?.let(::arrayOf)
            ?: return emptyList()
        return array.filter { it.isValid && !it.isDirectory }.distinctBy { it.url }
    }

    private fun groupLabel(group: TabGroupRecord): String = buildString {
        append(group.name)
        if (group.comment.isNotBlank()) append(" — ").append(group.comment)
    }

    private fun disabledAction(text: String): AnAction = object : DumbAwareAction(text) {
        override fun update(event: AnActionEvent) {
            event.presentation.isEnabled = false
        }

        override fun actionPerformed(event: AnActionEvent) = Unit
    }
}
