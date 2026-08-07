package com.whalesea.ideatabmanager.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

class AddCurrentTabToGroupAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null && CommonDataKeys.VIRTUAL_FILE.getData(event.dataContext)?.isDirectory == false
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val file = CommonDataKeys.VIRTUAL_FILE.getData(event.dataContext) ?: return
        TabGroupCommands.addCurrentFile(project, file)
    }
}
