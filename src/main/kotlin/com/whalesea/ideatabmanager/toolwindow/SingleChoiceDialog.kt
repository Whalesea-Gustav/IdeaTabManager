package com.whalesea.ideatabmanager.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.whalesea.ideatabmanager.IdeaTabManagerBundle
import java.awt.BorderLayout
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.ListSelectionModel

/** Stable replacement for the deprecated Messages.showChooseDialog API. */
class SingleChoiceDialog(
    project: Project,
    dialogTitle: String,
    private val prompt: String,
    options: Array<String>,
    initialIndex: Int = 0,
) : DialogWrapper(project) {
    private val list = JBList(options).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        selectedIndex = initialIndex.coerceIn(0, (model.size - 1).coerceAtLeast(0))
        visibleRowCount = minOf(10, model.size)
    }
    var selectedIndex: Int? = null
        private set

    init {
        title = dialogTitle
        init()
    }

    override fun createCenterPanel(): JComponent = JBPanel<JBPanel<*>>(BorderLayout(0, JBUI.scale(8))).apply {
        border = JBUI.Borders.empty(4)
        add(JBLabel(prompt), BorderLayout.NORTH)
        add(JBScrollPane(list).apply {
            border = JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground())
            preferredSize = JBUI.size(420, minOf(320, 42 + modelHeight()))
        }, BorderLayout.CENTER)
    }

    override fun createActions(): Array<Action> = arrayOf(okAction, cancelAction)

    override fun doOKAction() {
        val index = list.selectedIndex
        if (index < 0) {
            setErrorText(IdeaTabManagerBundle.message("error.choice.selection-required"))
            return
        }
        selectedIndex = index
        close(OK_EXIT_CODE)
    }

    private fun modelHeight(): Int = list.model.size * 24
}
