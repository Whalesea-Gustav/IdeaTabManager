package com.whalesea.ideatabmanager.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager

class ShowTabGroupsAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        event.project?.let { project -> ToolWindowManager.getInstance(project).getToolWindow("Tab Groups")?.activate(null) }
    }
}
