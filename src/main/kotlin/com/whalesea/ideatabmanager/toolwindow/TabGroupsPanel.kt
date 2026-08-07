package com.whalesea.ideatabmanager.toolwindow

import com.intellij.ide.DataManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import com.whalesea.ideatabmanager.actions.TabGroupCommands
import com.whalesea.ideatabmanager.model.TabGroupRecord
import com.whalesea.ideatabmanager.model.TabReference
import com.whalesea.ideatabmanager.service.TabGroupChangeListener
import com.whalesea.ideatabmanager.service.TabGroupProjectState
import com.whalesea.ideatabmanager.tortoise.TortoiseCommitService
import com.whalesea.ideatabmanager.tortoise.TortoiseCommitTarget
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

/** Native Swing Tool Window for selecting, saving, and restoring coding contexts. */
class TabGroupsPanel(private val project: Project) : JBPanel<TabGroupsPanel>(BorderLayout()), Disposable {
    private val state = project.getService(TabGroupProjectState::class.java)
    private val groupsPanel = JBPanel<JBPanel<*>>(VerticalLayout(JBUI.scale(6))).apply {
        border = JBUI.Borders.empty(8)
    }
    private var selectedGroupId: String? = null
    private var selectedHeaderField = HeaderField.TITLE
    private var inlineEdit: InlineEdit? = null

    init {
        isFocusable = true
        installKeyboardShortcuts()
        add(createToolbar(), BorderLayout.NORTH)
        add(JBScrollPane(groupsPanel).apply { border = JBUI.Borders.empty() }, BorderLayout.CENTER)
        val connection = project.messageBus.connect(this)
        connection.subscribe(TabGroupChangeListener.TOPIC, TabGroupChangeListener { renderGroups() })
        renderGroups()
    }

    override fun dispose() = Unit

    private fun installKeyboardShortcuts() {
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "editSelectedGroupField")
        actionMap.put("editSelectedGroupField", object : AbstractAction() {
            override fun actionPerformed(event: java.awt.event.ActionEvent) {
                val groupId = selectedGroupId ?: return
                beginInlineEdit(groupId, selectedHeaderField)
            }
        })
    }

    private fun createToolbar() = ActionManager.getInstance().createActionToolbar(
        "TabGroupsToolbar",
        DefaultActionGroup().apply {
            add(object : DumbAwareAction(
                "Create Empty Group",
                "Create an empty tab group",
                TabGroupIcons.newEmptyGroup,
            ) {
                override fun actionPerformed(event: AnActionEvent) = TabGroupCommands.createEmptyGroup(project)
            })
            add(object : DumbAwareAction(
                "Save All Tabs",
                "Save all open editor tabs as a tab group",
                TabGroupIcons.saveCurrentTabs,
            ) {
                override fun actionPerformed(event: AnActionEvent) = TabGroupCommands.createFromOpenTabs(project)
            })
            add(object : DumbAwareAction(
                "Save Selected Tabs",
                "Choose open editor tabs to create or update a tab group",
                TabGroupIcons.saveSelectedTabs,
            ) {
                override fun actionPerformed(event: AnActionEvent) = TabGroupCommands.selectOpenTabs(project)
            })
        },
        true,
    ).apply { targetComponent = this@TabGroupsPanel }.component

    private fun renderGroups() {
        groupsPanel.removeAll()
        groupsPanel.add(JBLabel("Tab Groups"))

        val groups = state.groups()
        if (groups.isEmpty()) {
            groupsPanel.add(JBLabel("No tab groups yet. Select open tabs or save the current editor context.").apply {
                border = JBUI.Borders.empty(8)
            })
        } else {
            groups.forEach { groupsPanel.add(createGroupPanel(it)) }
        }
        groupsPanel.revalidate()
        groupsPanel.repaint()
    }

    private fun createGroupPanel(group: TabGroupRecord): JBPanel<JBPanel<*>> = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        val selected = group.id == selectedGroupId
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(if (selected) JBColor(0x7AA6E8, 0x5C8AC4) else JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()),
            JBUI.Borders.empty(6),
        )
        isOpaque = selected
        if (selected) background = JBColor(0xF2F7FF, 0x333E4D)
        add(createGroupHeader(group), BorderLayout.NORTH)
        if (!group.isCollapsed) add(createTabList(group), BorderLayout.CENTER)
    }

    private fun createGroupHeader(group: TabGroupRecord): JBPanel<JBPanel<*>> = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        val color = TabGroupColorPalette.colorFor(group.colorId).color
        val collapseButton = JButton(if (group.isCollapsed) TabGroupIcons.expand else TabGroupIcons.collapse).apply {
            toolTipText = if (group.isCollapsed) "Expand tab group" else "Collapse tab group"
            isBorderPainted = false
            isContentAreaFilled = false
            isFocusPainted = false
            isOpaque = false
            preferredSize = JBUI.size(20, 20)
            minimumSize = preferredSize
            maximumSize = preferredSize
            addActionListener { TabGroupCommands.setCollapsed(project, group, !group.isCollapsed) }
        }
        val dot = JBPanel<JBPanel<*>>().apply {
            background = color
            preferredSize = JBUI.size(10, 10)
            minimumSize = preferredSize
        }
        val titleComponent = textComponent(group, HeaderField.TITLE)
        val commentComponent = textComponent(group, HeaderField.COMMENT)
        val groupTitle = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(dot, BorderLayout.WEST)
            add(titleComponent, BorderLayout.CENTER)
            add(commentComponent, BorderLayout.EAST)
        }
        val titlePanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(collapseButton, BorderLayout.WEST)
            add(groupTitle, BorderLayout.CENTER)
        }
        add(titlePanel, BorderLayout.CENTER)
        listOf<Component>(this, titlePanel, groupTitle, dot).forEach { attachGroupHeaderInteractions(it, group, null) }
        attachGroupHeaderInteractions(titleComponent, group, HeaderField.TITLE)
        attachGroupHeaderInteractions(commentComponent, group, HeaderField.COMMENT)
        collapseButton.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) = showGroupMenuIfRequested(event, group)

            override fun mouseReleased(event: MouseEvent) = showGroupMenuIfRequested(event, group)
        })
    }

    private fun textComponent(group: TabGroupRecord, field: HeaderField): JComponent {
        if (inlineEdit == InlineEdit(group.id, field)) return createInlineEditor(group, field)
        val text = when (field) {
            HeaderField.TITLE -> "${group.name}  ${group.tabs.size}"
            HeaderField.COMMENT -> group.comment.ifBlank { "Add note" }
        }
        return JBLabel(text).apply {
            border = if (field == HeaderField.TITLE) JBUI.Borders.emptyLeft(6) else JBUI.Borders.emptyLeft(12)
            if (field == HeaderField.TITLE) font = font.deriveFont(Font.BOLD)
            if (field == HeaderField.COMMENT) foreground = JBColor.GRAY
            toolTipText = when {
                field == HeaderField.COMMENT && group.comment.isBlank() -> "Select this note and press F2 to edit"
                field == HeaderField.COMMENT -> group.comment
                else -> null
            }
        }
    }

    private fun createInlineEditor(group: TabGroupRecord, field: HeaderField): JBTextField {
        val initialValue = if (field == HeaderField.TITLE) group.name else group.comment
        return JBTextField(initialValue).apply {
            toolTipText = "Enter to save, Escape to cancel"
            addActionListener { finishInlineEdit(group, field, text) }
            addKeyListener(object : KeyAdapter() {
                override fun keyPressed(event: KeyEvent) {
                    if (event.keyCode == KeyEvent.VK_ESCAPE) cancelInlineEdit()
                }
            })
            addFocusListener(object : FocusAdapter() {
                override fun focusLost(event: FocusEvent) = finishInlineEdit(group, field, text)
            })
            SwingUtilities.invokeLater {
                requestFocusInWindow()
                selectAll()
            }
        }
    }

    private fun beginInlineEdit(groupId: String, field: HeaderField) {
        if (state.groups().none { it.id == groupId }) return
        inlineEdit = InlineEdit(groupId, field)
        selectedGroupId = groupId
        selectedHeaderField = field
        renderGroups()
    }

    private fun finishInlineEdit(group: TabGroupRecord, field: HeaderField, text: String) {
        if (inlineEdit != InlineEdit(group.id, field)) return
        inlineEdit = null
        when (field) {
            HeaderField.TITLE -> text.trim().takeIf(String::isNotEmpty)?.let { state.renameGroup(group.id, it) } ?: renderGroups()
            HeaderField.COMMENT -> state.updateGroupComment(group.id, text)
        }
    }

    private fun cancelInlineEdit() {
        if (inlineEdit == null) return
        inlineEdit = null
        renderGroups()
    }

    private fun createTabList(group: TabGroupRecord): JBPanel<JBPanel<*>> = JBPanel<JBPanel<*>>(VerticalLayout(JBUI.scale(2))).apply {
        if (group.tabs.isEmpty()) {
            add(JBLabel("Empty group").apply { border = JBUI.Borders.empty(4, 16) })
        } else {
            group.tabs.forEach { add(createTabLine(it)) }
        }
    }

    private fun createTabLine(reference: TabReference): SimpleColoredComponent = SimpleColoredComponent().apply {
        border = JBUI.Borders.empty(2, 16)
        append(reference.lastKnownName.ifBlank { reference.fileUrl.substringAfterLast('/') }, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        val parentPath = reference.projectRelativePath?.substringBeforeLast('/', "")?.takeIf(String::isNotBlank)
        if (parentPath != null) append("  $parentPath", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2 && event.button == MouseEvent.BUTTON1) TabGroupCommands.openReference(project, reference)
            }
        })
    }

    private fun attachGroupHeaderInteractions(component: Component, group: TabGroupRecord, field: HeaderField?) {
        component.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) = showGroupMenuIfRequested(event, group)

            override fun mouseReleased(event: MouseEvent) = showGroupMenuIfRequested(event, group)

            override fun mouseClicked(event: MouseEvent) {
                if (event.isPopupTrigger || event.button != MouseEvent.BUTTON1) return
                selectedGroupId = group.id
                selectedHeaderField = field ?: HeaderField.TITLE
                requestFocusInWindow()
                if (event.clickCount == 2) TabGroupCommands.activate(project, group) else renderGroups()
            }
        })
    }

    private fun showGroupMenuIfRequested(event: MouseEvent, group: TabGroupRecord) {
        if (!event.isPopupTrigger) return
        val popupPoint = RelativePoint(event)
        val dataContext = DataManager.getInstance().getDataContext(event.component)
        // Working-copy discovery may touch mounted/network paths and client lookup reads the registry.
        // Keep both outside EDT, then present the finished dynamic menu.
        ApplicationManager.getApplication().executeOnPooledThread {
            val commitTargets = TortoiseCommitService.availableTargets(group)
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) showGroupMenu(popupPoint, dataContext, group, commitTargets)
            }
        }
    }

    private fun showGroupMenu(
        popupPoint: RelativePoint,
        dataContext: DataContext,
        group: TabGroupRecord,
        commitTargets: List<TortoiseCommitTarget>,
    ) {
        val actions = DefaultActionGroup().apply {
            add(groupAction("Open Group") { TabGroupCommands.activate(project, group) })
            add(groupAction("Focus Group (Safe)") { TabGroupCommands.focus(project, group) })
            add(groupAction("Update from Current Open Tabs") { TabGroupCommands.updateFromOpenTabs(project, group) })
            add(groupAction("Add Files…") { TabGroupCommands.chooseAndAddFiles(project, group) })
            if (commitTargets.isNotEmpty()) {
                addSeparator()
                addTortoiseCommitActions(this, commitTargets)
            }
            addSeparator()
            add(groupAction("Edit Title") { beginInlineEdit(group.id, HeaderField.TITLE) })
            add(groupAction("Edit Note") { beginInlineEdit(group.id, HeaderField.COMMENT) })
            add(groupAction("Change Color") { TabGroupCommands.changeColor(project, group) })
            add(groupAction(if (group.isCollapsed) "Expand" else "Collapse") {
                TabGroupCommands.setCollapsed(project, group, !group.isCollapsed)
            })
            addSeparator()
            add(groupAction("Delete") { TabGroupCommands.delete(project, group) })
        }
        JBPopupFactory.getInstance().createActionGroupPopup(
            "Tab Group",
            actions,
            dataContext,
            JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
            false,
        ).show(popupPoint)
    }

    private fun addTortoiseCommitActions(
        actions: DefaultActionGroup,
        commitTargets: List<TortoiseCommitTarget>,
    ) {
        commitTargets.groupBy { it.kind }.forEach { (kind, targets) ->
            val totalFiles = targets.sumOf(TortoiseCommitTarget::fileCount)
            if (targets.size == 1) {
                val target = targets.single()
                actions.add(groupAction("Commit with ${kind.displayName} ($totalFiles)") {
                    TortoiseCommitService.launch(project, target)
                })
            } else {
                actions.add(DefaultActionGroup("Commit with ${kind.displayName} ($totalFiles)", true).apply {
                    targets.forEach { target ->
                        add(groupAction("${target.workingCopyRoot} (${target.fileCount})") {
                            TortoiseCommitService.launch(project, target)
                        })
                    }
                })
            }
        }
    }

    private fun groupAction(text: String, action: () -> Unit): DumbAwareAction = object : DumbAwareAction(text) {
        override fun actionPerformed(event: AnActionEvent) = action()
    }

    private enum class HeaderField { TITLE, COMMENT }

    private data class InlineEdit(val groupId: String, val field: HeaderField)
}
