package com.whalesea.ideatabmanager.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationInfo

/** Hides the Project View entry in Rider so Solution Explorer is the single file-tree menu. */
class NonRiderAddSelectedProjectFilesToGroupActionGroup : AddSelectedProjectFilesToGroupActionGroup() {
    override fun update(event: AnActionEvent) {
        super.update(event)
        if (ApplicationInfo.getInstance().build.productCode == "RD") {
            event.presentation.isEnabledAndVisible = false
        }
    }
}
