package com.whalesea.ideatabmanager.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import com.whalesea.ideatabmanager.IdeaTabManagerBundle
import java.awt.BorderLayout

/** Phase 0 shell. Functional group management is added in the following implementation phases. */
class TabGroupsToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(12)
            add(
                JBPanel<JBPanel<*>>(BorderLayout(0, JBUI.scale(6))).apply {
                    add(JBLabel(IdeaTabManagerBundle.message("toolwindow.empty.title")), BorderLayout.NORTH)
                    add(JBLabel(IdeaTabManagerBundle.message("toolwindow.empty.description")), BorderLayout.CENTER)
                },
                BorderLayout.NORTH,
            )
        }

        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}
