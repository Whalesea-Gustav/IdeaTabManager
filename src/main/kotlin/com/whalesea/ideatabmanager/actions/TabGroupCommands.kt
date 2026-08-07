package com.whalesea.ideatabmanager.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
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
import com.whalesea.ideatabmanager.toolwindow.TabGroupColorPalette
import com.whalesea.ideatabmanager.toolwindow.OpenTabsSelectionDialog

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
        if (captured.tabs.isEmpty()) {
            notify(project, "Open one or more files before selecting tabs.", NotificationType.INFORMATION)
            return
        }
        OpenTabsSelectionDialog(project, captured.tabs, captured.activeFileUrl, group).show()
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

    fun activate(project: Project, group: TabGroupRecord) {
        val state = project.service<TabGroupProjectState>()
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

    fun focus(project: Project, group: TabGroupRecord) {
        val state = project.service<TabGroupProjectState>()
        val confirmation = "Focus '${group.name}'? Clean tabs outside this group will close. Pinned and unsaved tabs stay open."
        if (state.needsFocusSafetyNotice()) {
            if (Messages.showYesNoDialog(project, confirmation, "Focus Tab Group", null) != Messages.YES) return
            state.acknowledgeFocusSafetyNotice()
        }
        state.markGroupUsed(group.id)
        state.focusGroup(group.id) { result ->
            val kept = result.keptModifiedFileCount + result.keptPinnedFileCount
            notify(
                project,
                "Focused '${group.name}': closed ${result.closedFileCount} tab(s), kept $kept protected tab(s).",
                if (result.restoreResult.missingTabs.isEmpty()) NotificationType.INFORMATION else NotificationType.WARNING,
            )
        }
    }

    fun openReference(project: Project, reference: TabReference) {
        project.service<TabGroupProjectState>().restoreReference(reference)
    }

    fun updateFromOpenTabs(project: Project, group: TabGroupRecord) {
        val updated = project.service<TabGroupProjectState>().updateGroupFromOpenTabs(group.id)
        if (updated != null) {
            notify(project, "Updated '${group.name}' from ${updated.tabs.size} open tab(s).")
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
        val selectedIndex = Messages.showChooseDialog(
            project,
            "Group color:",
            "Change Tab Group Color",
            null,
            TabGroupColorPalette.displayNames(),
            TabGroupColorPalette.displayName(group.colorId),
        )
        if (selectedIndex < 0) return
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
        val added = references.count { it.fileUrl !in before }
        state.addTabsToGroup(group.id, references)
        state.markGroupUsed(group.id)
        val message = if (added == 0) {
            "All selected files are already in '${group.name}'."
        } else {
            "Added $added file(s) to '${group.name}'."
        }
        notify(project, message)
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
        val selectedIndex = Messages.showChooseDialog(project, "Choose a tab group:", title, null, labels, labels.first())
        return groups.getOrNull(selectedIndex)
    }

    private fun notify(project: Project, message: String, type: NotificationType = NotificationType.INFORMATION) {
        NotificationGroupManager.getInstance().getNotificationGroup("Tab Groups")
            .createNotification(message, type)
            .notify(project)
    }
}
