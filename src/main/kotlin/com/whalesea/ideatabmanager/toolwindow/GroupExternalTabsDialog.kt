package com.whalesea.ideatabmanager.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import com.whalesea.ideatabmanager.IdeaTabManagerBundle
import com.whalesea.ideatabmanager.service.ExternalTabCandidate
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent

/** Folder-based review surface for closing clean tabs outside a selected group. */
class GroupExternalTabsDialog(
    project: Project,
    private val groupName: String,
    private val candidates: List<ExternalTabCandidate>,
    private val onCloseSelected: (List<ExternalTabCandidate>) -> Unit,
) : DialogWrapper(project) {
    private val entries = candidates.map { candidate ->
        CandidateEntry(candidate, candidate.file.path.replace('\\', '/'), JBCheckBox(candidate.displayName).apply {
            toolTipText = candidate.file.path
        })
    }
    private val root = buildTree(entries)

    init {
        title = IdeaTabManagerBundle.message("dialog.external-tabs.title")
        setOKButtonText(IdeaTabManagerBundle.message("dialog.external-tabs.close-selected"))
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
            add(JBLabel(IdeaTabManagerBundle.message("dialog.external-tabs.prompt")), BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
            add(JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
                add(JButton(IdeaTabManagerBundle.message("button.select-all")).apply { addActionListener { setFolderSelection(root, true) } })
                add(JButton(IdeaTabManagerBundle.message("button.clear")).apply { addActionListener { setFolderSelection(root, false) } })
            }, BorderLayout.SOUTH)
        }
    }

    override fun createActions(): Array<Action> = arrayOf(okAction, cancelAction)

    override fun doOKAction() {
        val selected = entries.filter { it.checkBox.isSelected }.map(CandidateEntry::candidate)
        if (selected.isEmpty()) {
            setErrorText(IdeaTabManagerBundle.message("error.external-tabs.selection-required"))
            return
        }
        close(OK_EXIT_CODE)
        onCloseSelected(selected)
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
            node.files.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.candidate.displayName }).forEach { entry ->
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

    private fun createFileRow(entry: CandidateEntry, depth: Int): JComponent = JBPanel<JBPanel<*>>(BorderLayout()).apply {
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

    private fun buildTree(entries: List<CandidateEntry>): FolderNode {
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
        val files = mutableListOf<CandidateEntry>()
        var expanded = true
        var checkBox: JBCheckBox? = null
        var childrenPanel: JComponent? = null

        fun allFiles(): List<CandidateEntry> = files + children.values.flatMap(FolderNode::allFiles)
    }

    private data class CandidateEntry(
        val candidate: ExternalTabCandidate,
        val path: String,
        val checkBox: JBCheckBox,
    )
}
