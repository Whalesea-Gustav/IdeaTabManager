package com.whalesea.ideatabmanager.toolwindow

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import com.whalesea.ideatabmanager.IdeaTabManagerBundle
import com.whalesea.ideatabmanager.actions.TabGroupCommands
import com.whalesea.ideatabmanager.model.TabGroupRecord
import com.whalesea.ideatabmanager.model.TabReference
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent

/** Modal group operation surface with a collapsible folder tree for open tabs. */
class OpenTabsSelectionDialog(
    private val project: Project,
    private val tabs: List<TabReference>,
    private val activeFileUrl: String?,
    private val targetGroup: TabGroupRecord? = null,
) : DialogWrapper(project) {
    // Keep this dialog safe for all callers: an existing Group never appears as an add candidate.
    private val entries = tabs
        .filterNot { reference -> targetGroup?.tabs?.any { it.fileUrl == reference.fileUrl } == true }
        .map { reference ->
            FileEntry(reference, resolvePath(reference), JBCheckBox(reference.lastKnownName, reference.fileUrl == activeFileUrl).apply {
                toolTipText = reference.projectRelativePath ?: reference.fileUrl
            })
        }
    private val root = buildTree(entries)

    private val addToExistingGroupAction = object : DialogWrapperAction(IdeaTabManagerBundle.message("dialog.open-tabs.add-existing-group")) {
        override fun doAction(event: ActionEvent?) {
            val selected = selectedTabsOrShowError() ?: return
            close(OK_EXIT_CODE)
            TabGroupCommands.addSelectedTabsToGroup(project, selected)
        }
    }

    init {
        title = targetGroup?.let { IdeaTabManagerBundle.message("dialog.open-tabs.add-to-group.title", it.name) }
            ?: IdeaTabManagerBundle.message("dialog.open-tabs.save-selected.title")
        setOKButtonText(IdeaTabManagerBundle.message(if (targetGroup == null) "dialog.open-tabs.create-group" else "dialog.open-tabs.add-to-group"))
        addToExistingGroupAction.isEnabled = project.service<com.whalesea.ideatabmanager.service.TabGroupProjectState>().groups().isNotEmpty()
        init()
    }

    override fun createCenterPanel(): JComponent {
        val treePanel = JBPanel<JBPanel<*>>(VerticalLayout(JBUI.scale(2))).apply {
            border = JBUI.Borders.empty(4)
            add(createFolderPanel(root, 0))
        }
        val scrollPane = JBScrollPane(treePanel).apply {
            border = JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground())
            preferredSize = JBUI.size(560, minOf(500, 92 + entries.size * 30))
        }
        return JBPanel<JBPanel<*>>(BorderLayout(0, JBUI.scale(8))).apply {
            val prompt = targetGroup?.let { IdeaTabManagerBundle.message("dialog.open-tabs.add-to-group.prompt", it.name) }
                ?: IdeaTabManagerBundle.message("dialog.open-tabs.save-selected.prompt")
            add(JBLabel(prompt), BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
            add(JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
                add(JButton(IdeaTabManagerBundle.message("button.select-all")).apply { addActionListener { setFolderSelection(root, true) } })
                add(JButton(IdeaTabManagerBundle.message("button.clear")).apply { addActionListener { setFolderSelection(root, false) } })
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
        val selected = entries.filter { it.checkBox.isSelected }.map(FileEntry::reference)
        if (selected.isEmpty()) {
            setErrorText(IdeaTabManagerBundle.message("error.open-tabs.selection-required"))
            return null
        }
        return selected
    }

    private fun createFolderPanel(node: FolderNode, depth: Int): JComponent {
        val row = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(0, JBUI.scale(depth * 18), 0, 0)
        }
        val expandButton = JButton(if (node.expanded) TabGroupIcons.collapse else TabGroupIcons.expand).apply {
            isBorderPainted = false
            isContentAreaFilled = false
            isFocusPainted = false
            isOpaque = false
            toolTipText = IdeaTabManagerBundle.message(if (node.expanded) "tooltip.folder.collapse" else "tooltip.folder.expand")
            preferredSize = JBUI.size(20, 20)
            minimumSize = preferredSize
            maximumSize = preferredSize
            addActionListener {
                node.expanded = !node.expanded
                icon = if (node.expanded) TabGroupIcons.collapse else TabGroupIcons.expand
                toolTipText = IdeaTabManagerBundle.message(if (node.expanded) "tooltip.folder.collapse" else "tooltip.folder.expand")
                node.childrenPanel?.isVisible = node.expanded
                node.childrenPanel?.parent?.revalidate()
            }
        }
        val checkBox = JBCheckBox(node.name).apply {
            font = font.deriveFont(Font.BOLD)
            toolTipText = node.fullPath
            addActionListener { setFolderSelection(node, isSelected) }
        }
        node.checkBox = checkBox
        row.add(expandButton, BorderLayout.WEST)
        row.add(checkBox, BorderLayout.CENTER)

        val children = JBPanel<JBPanel<*>>(VerticalLayout(JBUI.scale(2))).apply {
            border = JBUI.Borders.emptyTop(1)
                node.children.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }).forEach {
                add(createFolderPanel(it, depth + 1))
            }
            node.files.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.reference.lastKnownName }).forEach { entry ->
                add(createFileRow(entry, depth + 1))
            }
        }
        node.childrenPanel = children
        children.isVisible = node.expanded
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(row, BorderLayout.NORTH)
            add(children, BorderLayout.CENTER)
        }
    }

    private fun createFileRow(entry: FileEntry, depth: Int): JComponent = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        border = JBUI.Borders.empty(0, JBUI.scale(depth * 18 + 20), 0, 0)
        add(entry.checkBox, BorderLayout.CENTER)
        entry.checkBox.addActionListener { updateFolderSelections(root) }
    }

    private fun setFolderSelection(node: FolderNode, selected: Boolean) {
        node.files.forEach { it.checkBox.isSelected = selected }
        node.children.values.forEach { setFolderSelection(it, selected) }
        updateFolderSelections(root)
    }

    private fun updateFolderSelections(node: FolderNode) {
        node.children.values.forEach(::updateFolderSelections)
        val allFiles = node.allFiles()
        node.checkBox?.isSelected = allFiles.isNotEmpty() && allFiles.all { it.checkBox.isSelected }
    }

    private fun resolvePath(reference: TabReference): String {
        val virtualFile = VirtualFileManager.getInstance().findFileByUrl(reference.fileUrl)
        if (virtualFile != null) return virtualFile.path.replace('\\', '/')
        val relative = reference.projectRelativePath?.trim('/', '\\')
        val projectBase = project.basePath?.replace('\\', '/')?.trimEnd('/')
        return when {
            relative != null && projectBase != null -> "$projectBase/$relative"
            relative != null -> relative
            else -> reference.fileUrl.replace('\\', '/')
        }
    }

    private fun buildTree(entries: List<FileEntry>): FolderNode {
        val parentParts = entries.map { splitPath(it.path).dropLast(1) }
        val commonParts = commonPrefix(parentParts)
        val rootPath = renderPath(commonParts)
        val root = FolderNode(if (rootPath.isBlank()) IdeaTabManagerBundle.message("folder.open-files") else rootPath, rootPath)
        entries.forEach { entry ->
            val parts = splitPath(entry.path).dropLast(1).drop(commonParts.size)
            var folder = root
            parts.forEach { part ->
                folder = folder.children.getOrPut(part) { FolderNode(part, joinPath(folder.fullPath, part)) }
            }
            folder.files += entry
        }
        return root
    }

    private fun splitPath(path: String): List<String> {
        val normalized = path.replace('\\', '/').trimEnd('/')
        if (normalized.isBlank()) return emptyList()
        val drive = Regex("^[A-Za-z]:").find(normalized)?.value
        val rest = if (drive != null) normalized.removePrefix(drive).trimStart('/') else normalized
        val prefix = when {
            drive != null -> listOf(drive)
            normalized.startsWith('/') -> listOf("")
            else -> emptyList()
        }
        return prefix + rest.split('/').filter(String::isNotBlank)
    }

    private fun commonPrefix(paths: List<List<String>>): List<String> {
        if (paths.isEmpty()) return emptyList()
        var common = paths.first()
        paths.drop(1).forEach { path ->
            val length = minOf(common.size, path.size)
            var shared = 0
            while (shared < length && common[shared].equals(path[shared], ignoreCase = true)) shared++
            common = common.take(shared)
        }
        return common
    }

    private fun renderPath(parts: List<String>): String = when {
        parts.isEmpty() -> ""
        parts.size == 1 && parts[0].matches(Regex("^[A-Za-z]:$")) -> "${parts[0]}/"
        parts.first().isEmpty() -> "/${parts.drop(1).joinToString("/")}"
        else -> parts.joinToString("/")
    }

    private fun joinPath(parent: String, child: String): String = when {
        parent.isBlank() -> child
        parent.endsWith('/') -> parent + child
        else -> "$parent/$child"
    }

    private class FolderNode(val name: String, val fullPath: String) {
        val children = linkedMapOf<String, FolderNode>()
        val files = mutableListOf<FileEntry>()
        var expanded = true
        var checkBox: JBCheckBox? = null
        var childrenPanel: JComponent? = null

        fun allFiles(): List<FileEntry> = files + children.values.flatMap(FolderNode::allFiles)
    }

    private data class FileEntry(
        val reference: TabReference,
        val path: String,
        val checkBox: JBCheckBox,
    )
}
