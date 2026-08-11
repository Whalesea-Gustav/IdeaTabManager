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
import com.whalesea.ideatabmanager.IdeaTabManagerBundle
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
            notify(project, IdeaTabManagerBundle.message("notification.group.created", name))
        }
    }

    fun createFromOpenTabs(project: Project) {
        val state = project.service<TabGroupProjectState>()
        val captured = state.captureOpenTabs()
        requestGroupName(project)?.let { name ->
            state.createGroup(name, TabGroupColorPalette.randomColorId(), captured.tabs, captured.activeFileUrl)
            notify(project, IdeaTabManagerBundle.message("notification.group.saved-open-tabs", captured.tabs.size, name))
        }
    }

    fun selectOpenTabs(project: Project) {
        val captured = project.service<TabGroupProjectState>().captureOpenTabs()
        if (captured.tabs.isEmpty()) {
            notify(project, IdeaTabManagerBundle.message("notification.open-tabs.required"), NotificationType.INFORMATION)
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
                IdeaTabManagerBundle.message("notification.open-tabs.required")
            } else {
                IdeaTabManagerBundle.message("notification.group.all-open-files-present", group.name)
            }
            notify(project, message, NotificationType.INFORMATION)
            return
        }
        OpenTabsSelectionDialog(project, candidates, captured.activeFileUrl, group).show()
    }

    fun addCurrentOpenFileToGroup(project: Project, group: TabGroupRecord) {
        val file = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        if (file == null) {
            notify(project, IdeaTabManagerBundle.message("notification.active-editor.required"), NotificationType.INFORMATION)
            return
        }
        addFilesToGroup(project, group, listOf(file))
    }

    fun createFromCurrentFile(project: Project, file: VirtualFile) {
        val state = project.service<TabGroupProjectState>()
        requestGroupName(project, file.name)?.let { name ->
            state.createGroup(name, TabGroupColorPalette.randomColorId(), listOf(state.referenceFor(file)), file.url)
            notify(project, IdeaTabManagerBundle.message("notification.group.created-from-file", name, file.name))
        }
    }

    fun addCurrentFile(project: Project, file: VirtualFile) {
        val state = project.service<TabGroupProjectState>()
        chooseGroup(project, IdeaTabManagerBundle.message("dialog.add-current-tab.title"), state.groups())?.let { group ->
            addFilesToGroup(project, group, listOf(file))
        }
    }

    fun removeCurrentFile(project: Project, file: VirtualFile) {
        val state = project.service<TabGroupProjectState>()
        val containingGroups = state.groups().filter { group -> group.tabs.any { it.fileUrl == file.url } }
        if (containingGroups.isEmpty()) {
            notify(project, IdeaTabManagerBundle.message("notification.file.not-in-group", file.name), NotificationType.INFORMATION)
            return
        }
        chooseGroup(project, IdeaTabManagerBundle.message("dialog.remove-current-tab.title"), containingGroups)?.let { group ->
            state.removeTabFromGroup(group.id, file.url)
            notify(project, IdeaTabManagerBundle.message("notification.file.removed", file.name, group.name))
        }
    }

    fun removeTabFromGroup(project: Project, group: TabGroupRecord, reference: TabReference) {
        if (project.service<TabGroupProjectState>().removeTabFromGroup(group.id, reference.fileUrl) != null) {
            notify(project, IdeaTabManagerBundle.message("notification.file.removed", reference.lastKnownName.ifBlank { reference.fileUrl.substringAfterLast('/') }, group.name))
        }
    }

    fun activate(project: Project, group: TabGroupRecord) {
        val state = project.service<TabGroupProjectState>()
        state.setOpenTabsUndo(state.captureOpenTabs())
        state.markGroupUsed(group.id)
        state.restoreGroup(group.id) { result ->
            if (result.missingTabs.isEmpty()) {
                notify(project, IdeaTabManagerBundle.message("notification.group.activated", group.name))
            } else {
                val names = result.missingTabs.joinToString(", ") { it.lastKnownName }
                notify(project, IdeaTabManagerBundle.message("notification.group.activated-missing", group.name, names), NotificationType.WARNING)
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
                notify(project, IdeaTabManagerBundle.message("notification.group.opened-no-clean-external-tabs", group.name))
                return@restoreGroup
            }
            GroupExternalTabsDialog(project, group.name, candidates) { selected ->
                val closeResult = service.closeCleanExternalTabs(group, selected.map { it.file })
                val skipped = closeResult.skippedModifiedFileCount + closeResult.skippedNoLongerOpenCount
                val message = if (skipped == 0) {
                    IdeaTabManagerBundle.message("notification.group.opened-and-closed", group.name, closeResult.closedFileCount)
                } else {
                    IdeaTabManagerBundle.message("notification.group.opened-and-closed-with-kept", group.name, closeResult.closedFileCount, skipped)
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
            notify(project, IdeaTabManagerBundle.message("notification.no-clean-external-tabs"))
            return
        }
        GroupExternalTabsDialog(project, group.name, candidates) { selected ->
            state.setOpenTabsUndo(state.captureOpenTabs())
            val result = service.closeCleanExternalTabs(group, selected.map { it.file })
            notify(project, IdeaTabManagerBundle.message("notification.tabs.closed-selected", result.closedFileCount))
        }.show()
    }

    fun closeUnsafeExternalTabs(project: Project, group: TabGroupRecord) {
        val state = project.service<TabGroupProjectState>()
        val service = TabGroupExternalTabService(project)
        val candidates = service.cleanExternalTabs(group)
        if (candidates.isEmpty()) {
            notify(project, IdeaTabManagerBundle.message("notification.no-clean-external-tabs"))
            return
        }
        val confirmation = IdeaTabManagerBundle.message("dialog.close-other-tabs.unsafe.message", candidates.size, group.name)
        if (Messages.showYesNoDialog(project, confirmation, IdeaTabManagerBundle.message("dialog.close-other-tabs.unsafe.title"), null) != Messages.YES) return
        state.setOpenTabsUndo(state.captureOpenTabs())
        val result = service.closeCleanExternalTabs(group, candidates.map { it.file })
        val skipped = result.skippedModifiedFileCount + result.skippedNoLongerOpenCount
        val message = if (skipped == 0) {
            IdeaTabManagerBundle.message("notification.other-tabs.closed", result.closedFileCount)
        } else {
            IdeaTabManagerBundle.message("notification.other-tabs.closed-with-kept", result.closedFileCount, skipped)
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
            notify(project, IdeaTabManagerBundle.message("notification.group.replaced-open-tabs", group.name, updated.tabs.size))
        }
    }

    fun createFromSelectedTabs(project: Project, tabs: List<TabReference>, activeFileUrl: String?) {
        if (tabs.isEmpty()) {
            notify(project, IdeaTabManagerBundle.message("notification.open-tabs.selection-required"), NotificationType.INFORMATION)
            return
        }
        requestGroupName(project)?.let { name ->
            project.service<TabGroupProjectState>().createGroup(name, TabGroupColorPalette.randomColorId(), tabs, activeFileUrl)
            notify(project, IdeaTabManagerBundle.message("notification.group.created-from-selected-tabs", name, tabs.size))
        }
    }

    fun addSelectedTabsToGroup(project: Project, tabs: List<TabReference>) {
        if (tabs.isEmpty()) {
            notify(project, IdeaTabManagerBundle.message("notification.open-tabs.selection-required"), NotificationType.INFORMATION)
            return
        }
        val state = project.service<TabGroupProjectState>()
        chooseGroup(project, IdeaTabManagerBundle.message("dialog.add-selected-tabs.title"), state.groups())?.let { group ->
            addReferencesToGroup(project, group, tabs)
        }
    }

    fun addSelectedTabsToGroup(project: Project, group: TabGroupRecord, tabs: List<TabReference>) {
        if (tabs.isEmpty()) {
            notify(project, IdeaTabManagerBundle.message("notification.open-tabs.selection-required"), NotificationType.INFORMATION)
            return
        }
        addReferencesToGroup(project, group, tabs)
    }

    fun addFilesToGroup(project: Project, group: TabGroupRecord, files: Collection<VirtualFile>) {
        val validFiles = files.filter { it.isValid && !it.isDirectory }.distinctBy { it.url }
        if (validFiles.isEmpty()) {
            notify(project, IdeaTabManagerBundle.message("notification.files.selection-required"), NotificationType.INFORMATION)
            return
        }
        val state = project.service<TabGroupProjectState>()
        addReferencesToGroup(project, group, validFiles.map(state::referenceFor))
    }

    fun addFilesToChosenGroup(project: Project, files: Collection<VirtualFile>) {
        val validFiles = files.filter { it.isValid && !it.isDirectory }.distinctBy { it.url }
        if (validFiles.isEmpty()) {
            notify(project, IdeaTabManagerBundle.message("notification.files.selection-required"), NotificationType.INFORMATION)
            return
        }
        val state = project.service<TabGroupProjectState>()
        chooseGroup(project, IdeaTabManagerBundle.message("dialog.add-selected-files.title"), state.groups())?.let { group ->
            addFilesToGroup(project, group, validFiles)
        }
    }

    fun chooseAndAddFiles(project: Project, group: TabGroupRecord) {
        val descriptor = FileChooserDescriptorFactory.createMultipleFilesNoJarsDescriptor().apply {
            title = IdeaTabManagerBundle.message("file-chooser.add-files.title", group.name)
            description = IdeaTabManagerBundle.message("file-chooser.add-files.description")
        }
        addFilesToGroup(project, group, FileChooser.chooseFiles(descriptor, project, null).toList())
    }

    fun rename(project: Project, group: TabGroupRecord) {
        val name = Messages.showInputDialog(project, IdeaTabManagerBundle.message("dialog.group-name.prompt"), IdeaTabManagerBundle.message("dialog.rename-group.title"), null, group.name, null)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return
        project.service<TabGroupProjectState>().renameGroup(group.id, name)
    }

    fun changeColor(project: Project, group: TabGroupRecord) {
        val colorNames = TabGroupColorPalette.displayNames()
        val dialog = SingleChoiceDialog(
            project,
            IdeaTabManagerBundle.message("dialog.change-color.title"),
            IdeaTabManagerBundle.message("dialog.change-color.prompt"),
            colorNames,
            colorNames.indexOf(TabGroupColorPalette.displayName(group.colorId)),
        )
        dialog.show()
        val selectedIndex = dialog.selectedIndex ?: return
        val displayName = TabGroupColorPalette.displayNames()[selectedIndex]
        project.service<TabGroupProjectState>().changeGroupColor(group.id, TabGroupColorPalette.idForDisplayName(displayName))
    }

    fun delete(project: Project, group: TabGroupRecord) {
        if (Messages.showYesNoDialog(project, IdeaTabManagerBundle.message("dialog.delete-group.message", group.name), IdeaTabManagerBundle.message("dialog.delete-group.title"), null) == Messages.YES) {
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
            notify(project, IdeaTabManagerBundle.message("notification.group.selected-files-present", group.name))
            return
        }
        state.addTabsToGroup(group.id, additions)
        state.markGroupUsed(group.id)
        notify(project, IdeaTabManagerBundle.message("notification.group.files-added", additions.size, group.name))
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
                IdeaTabManagerBundle.message("notification.undo-tabs", result.openedFileCount, closeResult.closedFileCount)
            } else {
                IdeaTabManagerBundle.message("notification.undo-tabs-with-kept", result.openedFileCount, closeResult.closedFileCount, skipped)
            }
            notify(project, message, if (skipped == 0) NotificationType.INFORMATION else NotificationType.WARNING)
        }
    }

    private fun requestGroupName(project: Project, suggestedName: String = IdeaTabManagerBundle.message("dialog.new-group.default-name")): String? =
        Messages.showInputDialog(project, IdeaTabManagerBundle.message("dialog.group-name.prompt"), IdeaTabManagerBundle.message("dialog.create-group.title"), null, suggestedName, null)
            ?.trim()
            ?.takeIf(String::isNotEmpty)

    private fun chooseGroup(project: Project, title: String, groups: List<TabGroupRecord>): TabGroupRecord? {
        if (groups.isEmpty()) {
            notify(project, IdeaTabManagerBundle.message("notification.group.required"), NotificationType.INFORMATION)
            return null
        }
        val labels = groups.map { IdeaTabManagerBundle.message("group-choice.label", it.name, it.tabs.size, it.id.take(6)) }.toTypedArray()
        val dialog = SingleChoiceDialog(project, title, IdeaTabManagerBundle.message("dialog.group-choice.prompt"), labels)
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
