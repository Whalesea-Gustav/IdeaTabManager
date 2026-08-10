package com.whalesea.ideatabmanager.toolwindow

import com.intellij.ide.DataManager
import com.intellij.icons.AllIcons
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
import java.awt.event.InputEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.border.Border

/** Native Swing Tool Window for selecting, saving, and restoring coding contexts. */
class TabGroupsPanel(private val project: Project) : JBPanel<TabGroupsPanel>(BorderLayout()), Disposable {
    private val state = project.getService(TabGroupProjectState::class.java)
    private val groupsPanel = JBPanel<JBPanel<*>>(VerticalLayout(JBUI.scale(6))).apply {
        border = JBUI.Borders.empty(8)
    }
    private var selectedGroupId: String? = null
    private var selectedHeaderField = HeaderField.TITLE
    private var inlineEdit: InlineEdit? = null
    private var activeDropIndicator: DropIndicator? = null
    private var draggedGroupId: String? = null
    private var draggedTab: DraggedTab? = null

    init {
        isFocusable = true
        installKeyboardShortcuts()
        add(createToolbar(), BorderLayout.NORTH)
        add(JBScrollPane(groupsPanel).apply { border = JBUI.Borders.empty() }, BorderLayout.CENTER)
        val connection = project.messageBus.connect(this)
        connection.subscribe(TabGroupChangeListener.TOPIC, TabGroupChangeListener { renderGroups() })
        renderGroups()
    }

    override fun dispose() {
        draggedGroupId = null
        draggedTab = null
        clearDropIndicator()
    }

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
                "Undo Last Group Action",
                "Undo the last Group creation, open, or close action",
                com.intellij.icons.AllIcons.Actions.Undo,
            ) {
                override fun update(event: AnActionEvent) {
                    event.presentation.isEnabled = project.getService(TabGroupProjectState::class.java).hasUndo()
                }

                override fun actionPerformed(event: AnActionEvent) = TabGroupCommands.undoLast(project)
            })
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
            warmUpTortoiseCommitTargets(groups)
        }
        groupsPanel.revalidate()
        groupsPanel.repaint()
    }

    private fun warmUpTortoiseCommitTargets(groups: List<TabGroupRecord>) {
        ApplicationManager.getApplication().executeOnPooledThread {
            TortoiseCommitService.warmUp(groups)
        }
    }

    private fun createGroupPanel(group: TabGroupRecord): JBPanel<JBPanel<*>> = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        val selected = group.id == selectedGroupId
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(if (selected) JBColor(0x7AA6E8, 0x5C8AC4) else JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()),
            JBUI.Borders.empty(6),
        )
        isOpaque = selected
        if (selected) background = JBColor(0xF2F7FF, 0x333E4D)
        putClientProperty(GROUP_PANEL_ID_PROPERTY, group.id)
        val header = createGroupHeader(group)
        add(header, BorderLayout.NORTH)
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
        val dragHandle = JLabel(TabGroupIcons.groupDragHandle).apply {
            toolTipText = "Drag to reorder tab group"
            cursor = TabGroupCursors.reorder
            preferredSize = JBUI.size(16, 20)
            minimumSize = preferredSize
            maximumSize = preferredSize
            val dragListener = object : MouseAdapter() {
                override fun mousePressed(event: MouseEvent) {
                    draggedGroupId = null
                    showGroupMenuIfRequested(event, group)
                }

                override fun mouseReleased(event: MouseEvent) {
                    val wasDragging = draggedGroupId == group.id
                    if (wasDragging) completeGroupReorder(event.component, event.point, group.id)
                    draggedGroupId = null
                    clearDropIndicator()
                    if (!wasDragging) showGroupMenuIfRequested(event, group)
                }

                override fun mouseDragged(event: MouseEvent) {
                    if (event.modifiersEx and InputEvent.BUTTON1_DOWN_MASK != 0) {
                        draggedGroupId = group.id
                        updateGroupReorderTarget(event.component, event.point, group.id)
                    }
                }
            }
            addMouseListener(dragListener)
            addMouseMotionListener(dragListener)
        }
        val dot = JBPanel<JBPanel<*>>().apply {
            background = color
            preferredSize = JBUI.size(10, 10)
            minimumSize = preferredSize
            // BoxLayout otherwise treats a JPanel as horizontally expandable and lets
            // the color marker consume the header space needed by the title metadata.
            maximumSize = preferredSize
        }
        val titleComponent = textComponent(group, HeaderField.TITLE)
        val countComponent = groupCountComponent(group)
        val commentComponent = textComponent(group, HeaderField.COMMENT)
        val groupTitle = JBPanel<JBPanel<*>>().apply {
            // Do not use BorderLayout.CENTER for the title: it expands to all remaining width and
            // pushes the note to the far right, making a small intended gap look very large.
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(dot.apply { alignmentY = 0.5f })
            add(Box.createHorizontalStrut(JBUI.scale(4)))
            add(titleComponent.apply { alignmentY = 0.5f })
            add(Box.createHorizontalStrut(JBUI.scale(6)))
            add(countComponent.apply { alignmentY = 0.5f })
            add(Box.createHorizontalStrut(JBUI.scale(4)))
            add(commentComponent.apply { alignmentY = 0.5f })
            add(Box.createHorizontalGlue())
        }
        val titlePanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(JBPanel<JBPanel<*>>(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, JBUI.scale(2), 0)).apply {
                border = JBUI.Borders.emptyRight(8)
                add(dragHandle)
                add(collapseButton)
            }, BorderLayout.WEST)
            add(groupTitle, BorderLayout.CENTER)
        }
        add(titlePanel, BorderLayout.CENTER)
        listOf<Component>(this, titlePanel, groupTitle, dot, countComponent).forEach { attachGroupHeaderInteractions(it, group, null) }
        attachGroupHeaderInteractions(titleComponent, group, HeaderField.TITLE)
        attachGroupHeaderInteractions(commentComponent, group, HeaderField.COMMENT)
        collapseButton.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) = showGroupMenuIfRequested(event, group)

            override fun mouseReleased(event: MouseEvent) = showGroupMenuIfRequested(event, group)
        })
    }

    private fun showDropIndicator(target: JComponent, position: DropPosition) {
        if (activeDropIndicator?.target == target && activeDropIndicator?.position == position) return
        clearDropIndicator()
        val originalBorder = target.border
        val indicatorBorder = BorderFactory.createMatteBorder(
            if (position == DropPosition.BEFORE) JBUI.scale(2) else 0,
            0,
            if (position == DropPosition.AFTER) JBUI.scale(2) else 0,
            0,
            JBColor(0x4B8DF8, 0x4B8DF8),
        )
        target.border = BorderFactory.createCompoundBorder(indicatorBorder, originalBorder)
        activeDropIndicator = DropIndicator(target, originalBorder, position)
        target.revalidate()
        target.repaint()
    }

    private fun clearDropIndicator() {
        val indicator = activeDropIndicator ?: return
        indicator.target.border = indicator.originalBorder
        indicator.target.revalidate()
        indicator.target.repaint()
        activeDropIndicator = null
    }

    /**
     * Handles group reordering directly from the plugin-owned Swing hierarchy.
     *
     * TransferHandler based DnD is unreliable inside embedded Tool Windows: the DropTarget can be a
     * Header child while its coordinates belong to that child rather than the Group panel. Resolving
     * the target from the current pointer keeps the interaction local and makes before/after feedback
     * agree with the persisted order.
     */
    private fun updateGroupReorderTarget(source: Component, sourcePoint: java.awt.Point, sourceGroupId: String) {
        val target = groupPanelAt(source, sourcePoint) ?: run {
            clearDropIndicator()
            return
        }
        val targetGroupId = target.getClientProperty(GROUP_PANEL_ID_PROPERTY) as? String ?: return
        if (targetGroupId == sourceGroupId) {
            clearDropIndicator()
            return
        }
        showDropIndicator(target, dropPosition(source, sourcePoint, target))
    }

    private fun completeGroupReorder(source: Component, sourcePoint: java.awt.Point, sourceGroupId: String) {
        val target = groupPanelAt(source, sourcePoint) ?: return
        val targetGroupId = target.getClientProperty(GROUP_PANEL_ID_PROPERTY) as? String ?: return
        if (targetGroupId == sourceGroupId) return

        val groups = state.groups()
        val targetIndex = groups.indexOfFirst { it.id == targetGroupId }
        if (targetIndex < 0) return
        val beforeGroupId = when (dropPosition(source, sourcePoint, target)) {
            DropPosition.BEFORE -> targetGroupId
            DropPosition.AFTER -> groups.getOrNull(targetIndex + 1)?.id
        }
        state.moveGroupBefore(sourceGroupId, beforeGroupId)
    }

    private fun groupPanelAt(source: Component, sourcePoint: java.awt.Point): JComponent? {
        val pointInGroups = SwingUtilities.convertPoint(source, sourcePoint, groupsPanel)
        var candidate: Component? = SwingUtilities.getDeepestComponentAt(groupsPanel, pointInGroups.x, pointInGroups.y)
        while (candidate != null && candidate !== groupsPanel) {
            if (candidate is JComponent && candidate.getClientProperty(GROUP_PANEL_ID_PROPERTY) != null) return candidate
            candidate = candidate.parent
        }
        return null
    }

    private fun dropPosition(source: Component, sourcePoint: java.awt.Point, target: JComponent): DropPosition {
        val pointInTarget = SwingUtilities.convertPoint(source, sourcePoint, target)
        return if (pointInTarget.y < target.height / 2) DropPosition.BEFORE else DropPosition.AFTER
    }

    private fun textComponent(group: TabGroupRecord, field: HeaderField): JComponent {
        if (inlineEdit == InlineEdit(group.id, field)) return createInlineEditor(group, field)
        val text = when (field) {
            HeaderField.TITLE -> group.name
            HeaderField.COMMENT -> group.comment.ifBlank { "Add note" }
        }
        return JBLabel(text).apply {
            border = JBUI.Borders.empty()
            if (field == HeaderField.TITLE) font = font.deriveFont(Font.BOLD)
            if (field == HeaderField.COMMENT) foreground = JBColor.GRAY
            toolTipText = when {
                field == HeaderField.COMMENT && group.comment.isBlank() -> "Select this note and press F2 to edit"
                field == HeaderField.COMMENT -> group.comment
                else -> null
            }
        }
    }

    private fun groupCountComponent(group: TabGroupRecord): JComponent = JBLabel("num=${group.tabs.size}").apply {
        // Keep the count legible without competing with the editable group title.
        font = font.deriveFont(Font.PLAIN, (font.size2D - 1f).coerceAtLeast(10f))
        foreground = JBColor(0x737B87, 0x8A939F)
        border = JBUI.Borders.empty()
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
            group.tabs.forEach { add(createTabLine(group, it)) }
        }
    }

    private fun createTabLine(group: TabGroupRecord, reference: TabReference): JBPanel<JBPanel<*>> = JBPanel<JBPanel<*>>(
        java.awt.FlowLayout(java.awt.FlowLayout.LEFT, JBUI.scale(4), 0),
    ).apply {
        val fileName = reference.lastKnownName.ifBlank { reference.fileUrl.substringAfterLast('/') }
        val parentPath = reference.projectRelativePath?.substringBeforeLast('/', "")?.takeIf(String::isNotBlank)
        val dragHandle = JLabel(TabGroupIcons.tabDragHandle).apply {
            toolTipText = "Drag to reorder file in group"
            cursor = TabGroupCursors.reorder
            preferredSize = JBUI.size(14, 18)
            minimumSize = preferredSize
            maximumSize = preferredSize
            val dragListener = object : MouseAdapter() {
                override fun mousePressed(event: MouseEvent) {
                    draggedTab = null
                    clearDropIndicator()
                }

                override fun mouseReleased(event: MouseEvent) {
                    val dragged = draggedTab
                    if (dragged != null) completeTabReorder(event.component, event.point, dragged)
                    draggedTab = null
                    clearDropIndicator()
                }

                override fun mouseDragged(event: MouseEvent) {
                    if (event.modifiersEx and InputEvent.BUTTON1_DOWN_MASK != 0) {
                        val dragged = DraggedTab(group.id, reference.fileUrl)
                        draggedTab = dragged
                        updateTabReorderTarget(event.component, event.point, dragged)
                    }
                }
            }
            addMouseListener(dragListener)
            addMouseMotionListener(dragListener)
        }
        val nameLabel = JBLabel(fileName).apply {
            toolTipText = reference.fileUrl
        }
        val pathLabel = JBLabel(parentPath ?: "").apply {
            foreground = JBColor.GRAY
            toolTipText = reference.fileUrl
        }
        val removeButton = JButton(AllIcons.Actions.Close).apply {
            toolTipText = "Remove file from group"
            isBorderPainted = false
            isContentAreaFilled = false
            isFocusPainted = false
            preferredSize = JBUI.size(20, 20)
            minimumSize = preferredSize
            maximumSize = preferredSize
            addActionListener { TabGroupCommands.removeTabFromGroup(project, group, reference) }
        }
        border = JBUI.Borders.empty(2, 8, 2, 4)
        add(dragHandle)
        add(nameLabel)
        add(removeButton)
        if (parentPath != null) add(pathLabel)
        listOf<Component>(this, nameLabel, pathLabel).forEach { component ->
            component.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(event: MouseEvent) {
                    if (event.clickCount == 2 && event.button == MouseEvent.BUTTON1) TabGroupCommands.openReference(project, reference)
                }
            })
        }
        putClientProperty(TAB_ROW_GROUP_ID_PROPERTY, group.id)
        putClientProperty(TAB_ROW_FILE_URL_PROPERTY, reference.fileUrl)
    }

    private fun updateTabReorderTarget(source: Component, sourcePoint: java.awt.Point, dragged: DraggedTab) {
        val target = tabLineAt(source, sourcePoint) ?: run {
            clearDropIndicator()
            return
        }
        val targetGroupId = target.getClientProperty(TAB_ROW_GROUP_ID_PROPERTY) as? String ?: return
        val targetFileUrl = target.getClientProperty(TAB_ROW_FILE_URL_PROPERTY) as? String ?: return
        if (targetGroupId != dragged.groupId || targetFileUrl == dragged.fileUrl) {
            clearDropIndicator()
            return
        }
        showDropIndicator(target, dropPosition(source, sourcePoint, target))
    }

    private fun completeTabReorder(source: Component, sourcePoint: java.awt.Point, dragged: DraggedTab) {
        val target = tabLineAt(source, sourcePoint) ?: return
        val targetGroupId = target.getClientProperty(TAB_ROW_GROUP_ID_PROPERTY) as? String ?: return
        val targetFileUrl = target.getClientProperty(TAB_ROW_FILE_URL_PROPERTY) as? String ?: return
        if (targetGroupId != dragged.groupId || targetFileUrl == dragged.fileUrl) return
        val group = state.groups().firstOrNull { it.id == dragged.groupId } ?: return
        val targetIndex = group.tabs.indexOfFirst { it.fileUrl == targetFileUrl }
        if (targetIndex < 0) return
        val beforeFileUrl = when (dropPosition(source, sourcePoint, target)) {
            DropPosition.BEFORE -> targetFileUrl
            DropPosition.AFTER -> group.tabs.getOrNull(targetIndex + 1)?.fileUrl
        }
        state.moveTabBefore(dragged.groupId, dragged.fileUrl, beforeFileUrl)
    }

    private fun tabLineAt(source: Component, sourcePoint: java.awt.Point): JComponent? {
        val pointInGroups = SwingUtilities.convertPoint(source, sourcePoint, groupsPanel)
        var candidate: Component? = SwingUtilities.getDeepestComponentAt(groupsPanel, pointInGroups.x, pointInGroups.y)
        while (candidate != null && candidate !== groupsPanel) {
            if (candidate is JComponent && candidate.getClientProperty(TAB_ROW_GROUP_ID_PROPERTY) != null) return candidate
            candidate = candidate.parent
        }
        return null
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
            add(groupAction("Open Group Tabs and Close Others…") { TabGroupCommands.activateAndReviewOtherTabs(project, group) })
            addSeparator()
            add(groupAction("Add Open Tabs…") { TabGroupCommands.chooseAndAddOpenTabs(project, group) })
            add(groupAction("Add Files…") { TabGroupCommands.chooseAndAddFiles(project, group) })
            add(groupAction("Replace Group Contents with Current Open Tabs") { TabGroupCommands.updateFromOpenTabs(project, group) })
            addSeparator()
            add(groupAction("Choose Other Tabs to Close…") { TabGroupCommands.reviewExternalTabs(project, group) })
            add(groupAction("Close All Other Tabs with No Unsaved Changes (Unsafe)") { TabGroupCommands.closeUnsafeExternalTabs(project, group) })
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

    private enum class DropPosition { BEFORE, AFTER }

    private data class DraggedTab(val groupId: String, val fileUrl: String)

    private data class DropIndicator(val target: JComponent, val originalBorder: Border?, val position: DropPosition)

    private data class InlineEdit(val groupId: String, val field: HeaderField)

    private companion object {
        const val GROUP_PANEL_ID_PROPERTY = "IdeaTabManager.GroupPanelId"
        const val TAB_ROW_GROUP_ID_PROPERTY = "IdeaTabManager.TabRowGroupId"
        const val TAB_ROW_FILE_URL_PROPERTY = "IdeaTabManager.TabRowFileUrl"
    }
}
