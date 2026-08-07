package com.whalesea.ideatabmanager.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import com.whalesea.ideatabmanager.actions.TabGroupCommands
import com.whalesea.ideatabmanager.model.TabGroupRecord
import com.whalesea.ideatabmanager.model.TabReference
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent

/** Modal selection surface used when an open-tab subset needs an explicit group operation. */
class OpenTabsSelectionDialog(
    private val project: Project,
    private val tabs: List<TabReference>,
    private val activeFileUrl: String?,
    private val targetGroup: TabGroupRecord? = null,
) : DialogWrapper(project) {
    private val rows = tabs.map { reference ->
        TabRow(reference, JBCheckBox(reference.lastKnownName, reference.fileUrl == activeFileUrl).apply {
            toolTipText = reference.projectRelativePath ?: reference.fileUrl
        })
    }

    private val addToExistingGroupAction = object : DialogWrapperAction("Add to Existing Group") {
        override fun doAction(event: ActionEvent?) {
            val selected = selectedTabsOrShowError() ?: return
            close(OK_EXIT_CODE)
            TabGroupCommands.addSelectedTabsToGroup(project, selected)
        }
    }

    init {
        title = targetGroup?.let { "Add Open Tabs to ${it.name}" } ?: "Save Selected Tabs"
        setOKButtonText(if (targetGroup == null) "Create Group" else "Add to Group")
        addToExistingGroupAction.isEnabled = project.service<com.whalesea.ideatabmanager.service.TabGroupProjectState>().groups().isNotEmpty()
        init()
    }

    override fun createCenterPanel(): JComponent {
        val selectionPanel = JBPanel<JBPanel<*>>(VerticalLayout(JBUI.scale(3))).apply {
            border = JBUI.Borders.empty(6)
            rows.forEach { row ->
                add(JBPanel<JBPanel<*>>(BorderLayout()).apply {
                    add(row.checkBox, BorderLayout.NORTH)
                    row.reference.projectRelativePath?.takeIf(String::isNotBlank)?.let { relativePath ->
                        add(JBLabel(relativePath).apply {
                            foreground = com.intellij.ui.JBColor.GRAY
                            border = JBUI.Borders.emptyLeft(24)
                        }, BorderLayout.CENTER)
                    }
                })
            }
        }
        val scrollPane = JBScrollPane(selectionPanel).apply {
            border = JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground())
            preferredSize = JBUI.size(500, minOf(400, 72 + rows.size * 42))
        }
        return JBPanel<JBPanel<*>>(BorderLayout(0, JBUI.scale(8))).apply {
            val prompt = targetGroup?.let { "Choose the open files to add to '${it.name}'." }
                ?: "Choose the open files to include in a tab group."
            add(JBLabel(prompt), BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
            add(JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
                add(JButton("Select All").apply { addActionListener { rows.forEach { it.checkBox.isSelected = true } } })
                add(JButton("Clear").apply { addActionListener { rows.forEach { it.checkBox.isSelected = false } } })
            }, BorderLayout.SOUTH)
        }
    }

    override fun createActions(): Array<Action> = if (targetGroup == null) {
        arrayOf(okAction, addToExistingGroupAction, cancelAction)
    } else {
        arrayOf(okAction, cancelAction)
    }

    override fun doOKAction() {
        val selected = selectedTabsOrShowError() ?: return
        close(OK_EXIT_CODE)
        targetGroup?.let { group ->
            TabGroupCommands.addSelectedTabsToGroup(project, group, selected)
            return
        }
        val selectedActiveUrl = activeFileUrl?.takeIf { active -> selected.any { it.fileUrl == active } }
        TabGroupCommands.createFromSelectedTabs(project, selected, selectedActiveUrl)
    }

    private fun selectedTabsOrShowError(): List<TabReference>? {
        val selected = rows.filter { it.checkBox.isSelected }.map(TabRow::reference)
        if (selected.isEmpty()) {
            setErrorText("Select at least one open file.")
            return null
        }
        return selected
    }

    private data class TabRow(val reference: TabReference, val checkBox: JBCheckBox)
}
