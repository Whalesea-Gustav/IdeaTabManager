package com.whalesea.ideatabmanager.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VirtualFile
import com.whalesea.ideatabmanager.IdeaTabManagerBundle
import com.whalesea.ideatabmanager.model.TabGroupRecord
import com.whalesea.ideatabmanager.service.TabGroupProjectState
import com.whalesea.ideatabmanager.toolwindow.TabGroupIcons

/** Dynamic project-tree submenu for batch-adding the current file or folder selection. */
open class AddSelectedProjectFilesToGroupActionGroup : DefaultActionGroup(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.icon = TabGroupIcons.menu
        event.presentation.isEnabledAndVisible = event.project != null && selectedRoots(event).isNotEmpty()
    }

    override fun getChildren(event: AnActionEvent?): Array<AnAction> {
        val project = event?.project ?: return emptyArray()
        val selected = selectedRoots(event)
        if (selected.isEmpty()) return emptyArray()

        val actions = mutableListOf<AnAction>(
            object : DumbAwareAction(IdeaTabManagerBundle.message("project-view.new-group-from-selection")) {
                override fun actionPerformed(actionEvent: AnActionEvent) {
                    TabGroupCommands.createGroupFromProjectSelection(project, selected)
                }
            },
        )

        val state = project.service<TabGroupProjectState>()
        val allGroups = state.groups()
        if (allGroups.isEmpty()) return actions.toTypedArray()

        val recent = state.recentGroups()
        actions += Separator.getInstance()
        actions += recent.map { group ->
            object : DumbAwareAction(groupLabel(group)) {
                override fun actionPerformed(actionEvent: AnActionEvent) {
                    TabGroupCommands.addProjectSelectionToGroup(project, group, selected)
                }
            }
        }
        if (allGroups.size > recent.size) {
            actions += object : DumbAwareAction(IdeaTabManagerBundle.message("project-view.more-groups")) {
                override fun actionPerformed(actionEvent: AnActionEvent) {
                    TabGroupCommands.addProjectSelectionToChosenGroup(project, selected)
                }
            }
        }
        return actions.toTypedArray()
    }

    companion object {
        fun selectedRoots(event: AnActionEvent): List<VirtualFile> {
            val array = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(event.dataContext)
                ?: CommonDataKeys.VIRTUAL_FILE.getData(event.dataContext)?.let(::arrayOf)
                ?: return emptyList()
            return array.filter { it.isValid && it.isInLocalFileSystem }.distinctBy { it.url }
        }

        fun groupLabel(group: TabGroupRecord): String = buildString {
            append(group.name)
            if (group.comment.isNotBlank()) append(" — ").append(group.comment)
        }
    }
}
