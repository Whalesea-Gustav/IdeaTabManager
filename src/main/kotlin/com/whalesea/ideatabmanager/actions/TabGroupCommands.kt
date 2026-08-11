package com.whalesea.ideatabmanager.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.whalesea.ideatabmanager.model.TabGroupRecord
import com.whalesea.ideatabmanager.model.TabReference
import com.whalesea.ideatabmanager.service.TabGroupProjectState
import com.whalesea.ideatabmanager.service.TabGroupExternalTabService
import com.whalesea.ideatabmanager.service.TabGroupRestorer
import com.whalesea.ideatabmanager.service.TabGroupUndoOperation
import com.whalesea.ideatabmanager.toolwindow.TabGroupColorPalette
import com.whalesea.ideatabmanager.toolwindow.GroupExternalTabsDialog
import com.whalesea.ideatabmanager.toolwindow.OpenTabsSelectionDialog
import com.whalesea.ideatabmanager.toolwindow.SingleChoiceDialog

/** Shared UI commands so editor actions and Tool Window buttons have identical semantics. */
object TabGroupCommands {
    fun createEmptyGroup(project: Project) {
        requestGroupName(project)?.let { name ->
            project.service<TabGroupProjectState>().createGroup(name, TabGroupColorPalette.randomColorId())
            notify(project, "Created tab group '$name'.")
        }
    }

    fun createFromOpenTabs(project: Project) {
        val state = project.service<TabGroupProjectState>()
        val captured = state.captureOpenTabs()
        requestGroupName(project)?.let { name ->
            state.createGroup(name, TabGroupColorPalette.randomColorId(), captured.tabs, captured.activeFileUrl)
            notify(project, "Saved ${captured.tabs.size} open tab(s) to '$name'.")
        }
    }

    fun selectOpenTabs(project: Project) {
        val captured = project.service<TabGroupProjectState>().captureOpenTabs()
        if (captured.tabs.isEmpty()) {
            notify(project, "Open one or more files before selecting tabs.", NotificationType.INFORMATION)
            return
        }
        OpenTabsSelectionDialog(project, captured.tabs, captured.activeFileUrl).show()
    }

    fun chooseAndAddOpenTabs(project: Project, group: TabGroupRecord) {
        val captured = project.service<TabGroupProjectState>().captureOpenTabs()
        val existingUrls = group.tabs.mapTo(hashSetOf()) { it.fileUrl }
        val candidates = captured.tabs.filterNot { it.fileUrl in existingUrls }
        if (candidates.isEmpty()) {
            val message = if (captured.tabs.isEmpty()) {
                "Open one or more files before selecting tabs."
            } else {
                "All open files are already in '${group.name}'."
            }
            notify(project, message, NotificationType.INFORMATION)
            return
        }
        OpenTabsSelectionDialog(project, candidates, captured.activeFileUrl, group).show()
    }

    fun addCurrentOpenFileToGroup(project: Project, group: TabGroupRecord) {
        val file = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        if (file == null) {
            notify(project, "No editor file is currently active.", NotificationType.INFORMATION)
            return
        }
        addFilesToGroup(project, group, listOf(file))
    }

    fun createFromCurrentFile(project: Project, file: VirtualFile) {
        val state = project.service<TabGroupProjectState>()
        requestGroupName(project, file.name)?.let { name ->
            state.createGroup(name, TabGroupColorPalette.randomColorId(), listOf(state.referenceFor(file)), file.url)
            notify(project, "Created '$name' with ${file.name}.")
        }
    }

    fun addCurrentFile(project: Project, file: VirtualFile) {
        val state = project.service<TabGroupProjectState>()
        chooseGroup(project, "Add Current Tab to Group", state.groups())?.let { group ->
            addFilesToGroup(project, group, listOf(file))
        }
    }

    fun removeCurrentFile(project: Project, file: VirtualFile) {
        val state = project.service<TabGroupProjectState>()
        val containingGroups = state.groups().filter { group -> group.tabs.any { it.fileUrl == file.url } }
        if (containingGroups.isEmpty()) {
            notify(project, "${file.name} is not in a tab group.", NotificationType.INFORMATION)
            return
        }
        chooseGroup(project, "Remove Current Tab from Group", containingGroups)?.let { group ->
            state.removeTabFromGroup(group.id, file.url)
            notify(project, "Removed ${file.name} from '${group.name}'.")
        }
    }

    fun removeTabFromGroup(project: Project, group: TabGroupRecord, reference: TabReference) {
        if (project.service<TabGroupProjectState>().removeTabFromGroup(group.id, reference.fileUrl) != null) {
            notify(project, "Removed ${reference.lastKnownName.ifBlank { reference.fileUrl.substringAfterLast('/') }} from '${group.name}'.")
        }
    }

    fun activate(project: Project, group: TabGroupRecord) {
        val state = project.service<TabGroupProjectState>()
        state.setOpenTabsUndo(state.captureOpenTabs())
        state.markGroupUsed(group.id)
        state.restoreGroup(group.id) { result ->
            if (result.missingTabs.isEmpty()) {
                notify(project, "Activated '${group.name}'.")
            } else {
                val names = result.missingTabs.joinToString(", ") { it.lastKnownName }
                notify(project, "Activated '${group.name}'; skipped missing file(s): $names.", NotificationType.WARNING)
            }
        }
    }

    fun activateAndReviewOtherTabs(project: Project, group: TabGroupRecord) {
        val state = project.service<TabGroupProjectState>()
        state.setOpenTabsUndo(state.captureOpenTabs())
        state.markGroupUsed(group.id)
        state.restoreGroup(group.id) {
            val service = TabGroupExternalTabService(project)
            val candidates = service.cleanExternalTabs(group)
            if (candidates.isEmpty()) {
                notify(project, "Opened '${group.name}'. There are no other open tabs without unsaved changes to close.")
                return@restoreGroup
            }
            GroupExternalTabsDialog(project, group.name, candidates) { selected ->
                val closeResult = service.closeCleanExternalTabs(group, selected.map { it.file })
                val skipped = closeResult.skippedModifiedFileCount + closeResult.skippedNoLongerOpenCount
                val message = if (skipped == 0) {
                    "Opened '${group.name}' and closed ${closeResult.closedFileCount} selected tab(s)."
                } else {
                    "Opened '${group.name}', closed ${closeResult.closedFileCount} selected tab(s), and kept $skipped tab(s) with unsaved changes or no longer open."
                }
                notify(project, message, if (skipped == 0) NotificationType.INFORMATION else NotificationType.WARNING)
            }.show()
        }
    }

    fun reviewExternalTabs(project: Project, group: TabGroupRecord) {
        val state = project.service<TabGroupProjectState>()
        val service = TabGroupExternalTabService(project)
        val candidates = service.cleanExternalTabs(group)
        if (candidates.isEmpty()) {
            notify(project, "There are no other open tabs without unsaved changes to close.")
            return
        }
        GroupExternalTabsDialog(project, group.name, candidates) { selected ->
            state.setOpenTabsUndo(state.captureOpenTabs())
            val result = service.closeCleanExternalTabs(group, selected.map { it.file })
            notify(project, "Closed ${result.closedFileCount} selected tab(s).")
        }.show()
    }

    fun closeUnsafeExternalTabs(project: Project, group: TabGroupRecord) {
        val state = project.service<TabGroupProjectState>()
        val service = TabGroupExternalTabService(project)
        val candidates = service.cleanExternalTabs(group)
        if (candidates.isEmpty()) {
            notify(project, "There are no other open tabs without unsaved changes to close.")
            return
        }
        val confirmation = "Close all other tabs? This will close all ${candidates.size} currently open tabs not in '${group.name}' that have no unsaved changes. Tabs with unsaved changes will not be closed. Warning: the IDE does not let this plugin identify pinned tabs, so pinned tabs without unsaved changes may also be closed."
        if (Messages.showYesNoDialog(project, confirmation, "Close Other Tabs (Unsafe)", null) != Messages.YES) return
        state.setOpenTabsUndo(state.captureOpenTabs())
        val result = service.closeCleanExternalTabs(group, candidates.map { it.file })
        val skipped = result.skippedModifiedFileCount + result.skippedNoLongerOpenCount
        val message = if (skipped == 0) {
            "Closed ${result.closedFileCount} other tab(s)."
        } else {
            "Closed ${result.closedFileCount} other tab(s); kept $skipped tab(s) with unsaved changes or no longer open."
        }
        notify(project, message, if (skipped == 0) NotificationType.INFORMATION else NotificationType.WARNING)
    }

    fun undoLast(project: Project) {
        val state = project.service<TabGroupProjectState>()
        when (val operation = state.takeUndoOperation() ?: return) {
            is TabGroupUndoOperation.Groups -> state.restoreGroups(operation.state)
            is TabGroupUndoOperation.OpenTabs -> restoreOpenTabs(project, operation)
        }
    }

    fun openReference(project: Project, reference: TabReference) {
        project.service<TabGroupProjectState>().restoreReference(reference)
    }

    fun updateFromOpenTabs(project: Project, group: TabGroupRecord) {
        val updated = project.service<TabGroupProjectState>().updateGroupFromOpenTabs(group.id)
        if (updated != null) {
            notify(project, "'${group.name}' now contains all ${updated.tabs.size} currently open tab(s).")
        }
    }

    fun createFromSelectedTabs(project: Project, tabs: List<TabReference>, activeFileUrl: String?) {
        if (tabs.isEmpty()) {
            notify(project, "Select one or more open tabs first.", NotificationType.INFORMATION)
            return
        }
        requestGroupName(project)?.let { name ->
            project.service<TabGroupProjectState>().createGroup(name, TabGroupColorPalette.randomColorId(), tabs, activeFileUrl)
            notify(project, "Created '$name' from ${tabs.size} selected tab(s).")
        }
    }

    fun addSelectedTabsToGroup(project: Project, tabs: List<TabReference>) {
        if (tabs.isEmpty()) {
            notify(project, "Select one or more open tabs first.", NotificationType.INFORMATION)
            return
        }
        val state = project.service<TabGroupProjectState>()
        chooseGroup(project, "Add Selected Tabs to Group", state.groups())?.let { group ->
            addReferencesToGroup(project, group, tabs)
        }
    }

    fun addSelectedTabsToGroup(project: Project, group: TabGroupRecord, tabs: List<TabReference>) {
        if (tabs.isEmpty()) {
            notify(project, "Select one or more open tabs first.", NotificationType.INFORMATION)
            return
        }
        addReferencesToGroup(project, group, tabs)
    }

    fun addFilesToGroup(project: Project, group: TabGroupRecord, files: Collection<VirtualFile>) {
        val validFiles = files.filter { it.isValid && !it.isDirectory }.distinctBy { it.url }
        if (validFiles.isEmpty()) {
            notify(project, "Select one or more files first.", NotificationType.INFORMATION)
            return
        }
        val state = project.service<TabGroupProjectState>()
        addReferencesToGroup(project, group, validFiles.map(state::referenceFor))
    }

    fun addFilesToChosenGroup(project: Project, files: Collection<VirtualFile>) {
        val validFiles = files.filter { it.isValid && !it.isDirectory }.distinctBy { it.url }
        if (validFiles.isEmpty()) {
            notify(project, "Select one or more files first.", NotificationType.INFORMATION)
            return
        }
        val state = project.service<TabGroupProjectState>()
        chooseGroup(project, "Add Selected Files to Group", state.groups())?.let { group ->
            addFilesToGroup(project, group, validFiles)
        }
    }

    fun chooseAndAddFiles(project: Project, group: TabGroupRecord) {
        val descriptor = FileChooserDescriptorFactory.createMultipleFilesNoJarsDescriptor().apply {
            title = "Add Files to ${group.name}"
            description = "Select files to add to this tab group."
        }
        addFilesToGroup(project, group, FileChooser.chooseFiles(descriptor, project, null).toList())
    }

    fun rename(project: Project, group: TabGroupRecord) {
        val name = Messages.showInputDialog(project, "Group name:", "Rename Tab Group", null, group.name, null)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return
        project.service<TabGroupProjectState>().renameGroup(group.id, name)
    }

    fun changeColor(project: Project, group: TabGroupRecord) {
        val colorNames = TabGroupColorPalette.displayNames()
        val dialog = SingleChoiceDialog(
            project,
            "Change Tab Group Color",
            "Group color:",
            colorNames,
            colorNames.indexOf(TabGroupColorPalette.displayName(group.colorId)),
        )
        dialog.show()
        val selectedIndex = dialog.selectedIndex ?: return
        val displayName = TabGroupColorPalette.displayNames()[selectedIndex]
        project.service<TabGroupProjectState>().changeGroupColor(group.id, TabGroupColorPalette.idForDisplayName(displayName))
    }

    fun delete(project: Project, group: TabGroupRecord) {
        if (Messages.showYesNoDialog(project, "Delete tab group '${group.name}'?", "Delete Tab Group", null) == Messages.YES) {
            project.service<TabGroupProjectState>().deleteGroup(group.id)
        }
    }

    fun setCollapsed(project: Project, group: TabGroupRecord, isCollapsed: Boolean) {
        project.service<TabGroupProjectState>().setGroupCollapsed(group.id, isCollapsed)
    }

    private fun addReferencesToGroup(project: Project, group: TabGroupRecord, references: Collection<TabReference>) {
        val state = project.service<TabGroupProjectState>()
        val before = group.tabs.map { it.fileUrl }.toSet()
        val additions = references.filter { it.fileUrl !in before }.distinctBy { it.fileUrl }
        if (additions.isEmpty()) {
            notify(project, "All selected files are already in '${group.name}'.")
            return
        }
        state.addTabsToGroup(group.id, additions)
        state.markGroupUsed(group.id)
        notify(project, "Added ${additions.size} file(s) to '${group.name}'.")
    }

    private fun restoreOpenTabs(project: Project, operation: TabGroupUndoOperation.OpenTabs) {
        val snapshotGroup = TabGroupRecord(
            id = "undo",
            tabs = operation.tabs.map { it.copy() }.toMutableList(),
            activeFileUrl = operation.activeFileUrl,
        )
        TabGroupRestorer(project).restore(snapshotGroup) { result ->
            val externalService = TabGroupExternalTabService(project)
            val extras = externalService.cleanExternalTabs(snapshotGroup)
            val closeResult = externalService.closeCleanExternalTabs(snapshotGroup, extras.map { it.file })
            val skipped = closeResult.skippedModifiedFileCount + closeResult.skippedNoLongerOpenCount
            val message = if (skipped == 0) {
                "Undid the last tab action: restored ${result.openedFileCount} previous tab(s), closed ${closeResult.closedFileCount} extra tab(s)."
            } else {
                "Undid the last tab action: restored ${result.openedFileCount} previous tab(s), closed ${closeResult.closedFileCount} extra tab(s), kept $skipped changed or unavailable tab(s)."
            }
            notify(project, message, if (skipped == 0) NotificationType.INFORMATION else NotificationType.WARNING)
        }
    }

    private fun requestGroupName(project: Project, suggestedName: String = "New Tab Group"): String? =
        Messages.showInputDialog(project, "Group name:", "Create Tab Group", null, suggestedName, null)
            ?.trim()
            ?.takeIf(String::isNotEmpty)

    private fun chooseGroup(project: Project, title: String, groups: List<TabGroupRecord>): TabGroupRecord? {
        if (groups.isEmpty()) {
            notify(project, "Create a tab group first.", NotificationType.INFORMATION)
            return null
        }
        val labels = groups.map { "${it.name} — ${it.tabs.size} file(s) — ${it.id.take(6)}" }.toTypedArray()
        val dialog = SingleChoiceDialog(project, title, "Choose a tab group:", labels)
        dialog.show()
        val selectedIndex = dialog.selectedIndex ?: return null
        return groups.getOrNull(selectedIndex)
    }

    private fun notify(project: Project, message: String, type: NotificationType = NotificationType.INFORMATION) {
        NotificationGroupManager.getInstance().getNotificationGroup("Tab Groups")
            .createNotification(message, type)
            .notify(project)
    }
}
