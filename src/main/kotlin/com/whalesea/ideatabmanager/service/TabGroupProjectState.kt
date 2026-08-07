package com.whalesea.ideatabmanager.service

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.whalesea.ideatabmanager.model.TabGroupRecord
import com.whalesea.ideatabmanager.model.TabGroupState
import com.whalesea.ideatabmanager.model.TabReference
import java.util.UUID

/**
 * Project-private storage root for future Tab Group records.
 *
 * The state deliberately lives in workspace.xml: groups describe an individual developer's
 * current coding context and must not become shared project configuration by default.
 */
@State(
    name = "IdeaTabManager.TabGroups",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)],
)
class TabGroupProjectState(private val project: Project) : PersistentStateComponent<TabGroupState> {
    private var state = TabGroupState()

    override fun getState(): TabGroupState = state

    override fun loadState(state: TabGroupState) {
        this.state = state.also { it.schemaVersion = TabGroupState.CURRENT_SCHEMA_VERSION }
    }

    fun groups(): List<TabGroupRecord> = state.groups.map(::copyGroup)

    fun recentGroups(limit: Int = 5): List<TabGroupRecord> = state.groups
        .sortedWith(compareByDescending<TabGroupRecord> { it.lastUsedAtEpochMs }.thenByDescending { it.updatedAtEpochMs })
        .take(limit)
        .map(::copyGroup)

    fun needsFocusSafetyNotice(): Boolean = !state.focusSafetyNoticeAcknowledged

    fun acknowledgeFocusSafetyNotice() {
        if (!state.focusSafetyNoticeAcknowledged) {
            state.focusSafetyNoticeAcknowledged = true
        }
    }

    fun createGroup(name: String, colorId: String = TabGroupRecord.DEFAULT_COLOR_ID): TabGroupRecord =
        createGroup(name, colorId, emptyList(), null)

    fun createGroup(
        name: String,
        colorId: String,
        tabs: Collection<TabReference>,
        activeFileUrl: String?,
    ): TabGroupRecord {
        val now = System.currentTimeMillis()
        val group = TabGroupRecord(
            id = UUID.randomUUID().toString(),
            name = requireName(name),
            colorId = requireColorId(colorId),
            tabs = distinctReferences(tabs).toMutableList(),
            activeFileUrl = activeFileUrl?.takeIf { url -> tabs.any { it.fileUrl == url } },
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        state.groups += group
        notifyGroupsChanged()
        return copyGroup(group)
    }

    fun captureOpenTabs(): CapturedOpenTabs {
        val fileEditorManager = FileEditorManager.getInstance(project)
        val activeFileUrl = fileEditorManager.selectedFiles.firstOrNull()?.url
        val tabs = fileEditorManager.openFiles.map(::toReference)
        return CapturedOpenTabs(tabs, activeFileUrl)
    }

    fun referenceFor(file: VirtualFile): TabReference = toReference(file)

    fun updateGroupFromOpenTabs(groupId: String): TabGroupRecord? {
        val captured = captureOpenTabs()
        return updateGroup(groupId, captured.tabs, captured.activeFileUrl)
    }

    fun updateGroup(groupId: String, tabs: Collection<TabReference>, activeFileUrl: String?): TabGroupRecord? =
        findMutable(groupId)?.also { group ->
            group.tabs = distinctReferences(tabs).toMutableList()
            group.activeFileUrl = activeFileUrl?.takeIf { url -> group.tabs.any { it.fileUrl == url } }
            touch(group)
            notifyGroupsChanged()
        }?.let(::copyGroup)

    fun renameGroup(groupId: String, name: String): TabGroupRecord? =
        findMutable(groupId)?.also {
            it.name = requireName(name)
            touch(it)
            notifyGroupsChanged()
        }?.let(::copyGroup)

    fun updateGroupComment(groupId: String, comment: String): TabGroupRecord? =
        findMutable(groupId)?.also {
            it.comment = comment.replace(Regex("[\\r\\n]+"), " ").trim()
            touch(it)
            notifyGroupsChanged()
        }?.let(::copyGroup)

    fun changeGroupColor(groupId: String, colorId: String): TabGroupRecord? =
        findMutable(groupId)?.also {
            it.colorId = requireColorId(colorId)
            touch(it)
            notifyGroupsChanged()
        }?.let(::copyGroup)

    fun setGroupCollapsed(groupId: String, isCollapsed: Boolean): TabGroupRecord? =
        findMutable(groupId)?.also {
            if (it.isCollapsed != isCollapsed) {
                it.isCollapsed = isCollapsed
                touch(it)
                notifyGroupsChanged()
            }
        }?.let(::copyGroup)

    fun deleteGroup(groupId: String): Boolean = state.groups.removeIf { it.id == groupId }.also { removed ->
        if (removed) notifyGroupsChanged()
    }

    /** Reorders the persisted group list without affecting recent-use metadata or group contents. */
    fun moveGroupBefore(groupId: String, beforeGroupId: String?): Boolean {
        val sourceIndex = state.groups.indexOfFirst { it.id == groupId }
        if (sourceIndex < 0 || beforeGroupId == groupId) return false

        val beforeIndex = beforeGroupId?.let { id -> state.groups.indexOfFirst { it.id == id } } ?: -1
        if (beforeGroupId != null && beforeIndex < 0) return false
        if (beforeIndex == sourceIndex || beforeIndex == sourceIndex + 1) return false
        if (beforeGroupId == null && sourceIndex == state.groups.lastIndex) return false

        val group = state.groups.removeAt(sourceIndex)
        val destinationIndex = beforeGroupId?.let { id -> state.groups.indexOfFirst { it.id == id } } ?: state.groups.size
        state.groups.add(destinationIndex, group)
        notifyGroupsChanged()
        return true
    }

    fun addTabToGroup(groupId: String, reference: TabReference): TabGroupRecord? {
        require(reference.fileUrl.isNotBlank()) { "Tab reference URL must not be blank." }
        return addTabsToGroup(groupId, listOf(reference))
    }

    fun addTabsToGroup(groupId: String, references: Collection<TabReference>): TabGroupRecord? =
        findMutable(groupId)?.also { group ->
            val additions = distinctReferences(references).filter { candidate -> group.tabs.none { it.fileUrl == candidate.fileUrl } }
            if (additions.isNotEmpty()) {
                group.tabs += additions
                touch(group)
                notifyGroupsChanged()
            }
        }?.let(::copyGroup)

    fun markGroupUsed(groupId: String): TabGroupRecord? =
        findMutable(groupId)?.also {
            it.lastUsedAtEpochMs = System.currentTimeMillis()
            notifyGroupsChanged()
        }?.let(::copyGroup)

    fun removeTabFromGroup(groupId: String, fileUrl: String): TabGroupRecord? =
        findMutable(groupId)?.also { group ->
            if (group.tabs.removeIf { it.fileUrl == fileUrl }) {
                if (group.activeFileUrl == fileUrl) {
                    group.activeFileUrl = group.tabs.firstOrNull()?.fileUrl
                }
                touch(group)
                notifyGroupsChanged()
            }
        }?.let(::copyGroup)

    fun restoreGroup(groupId: String, onComplete: (TabGroupRestoreResult) -> Unit = {}): Boolean {
        val group = findMutable(groupId)?.let(::copyGroup) ?: return false
        TabGroupRestorer(project).restore(group, onComplete)
        return true
    }

    fun focusGroup(groupId: String, onComplete: (FocusGroupResult) -> Unit = {}): Boolean {
        val group = findMutable(groupId)?.let(::copyGroup) ?: return false
        TabGroupRestorer(project).focus(group, onComplete)
        return true
    }

    fun restoreReference(reference: TabReference, onComplete: (TabGroupRestoreResult) -> Unit = {}) {
        val singleTabGroup = TabGroupRecord(
            id = "",
            tabs = mutableListOf(reference.copy()),
            activeFileUrl = reference.fileUrl,
        )
        TabGroupRestorer(project).restore(singleTabGroup, onComplete)
    }

    private fun toReference(file: VirtualFile): TabReference {
        val editor = FileEditorManager.getInstance(project).getEditors(file)
            .filterIsInstance<TextEditor>()
            .firstOrNull()
        return TabReference(
            fileUrl = file.url,
            projectRelativePath = project.basePath?.let { FileUtil.getRelativePath(it, file.path, '/') },
            lastKnownName = file.name,
            caretOffset = editor?.editor?.caretModel?.offset,
        )
    }

    private fun findMutable(groupId: String): TabGroupRecord? = state.groups.firstOrNull { it.id == groupId }

    private fun touch(group: TabGroupRecord) {
        group.updatedAtEpochMs = System.currentTimeMillis()
    }

    private fun notifyGroupsChanged() {
        project.messageBus.syncPublisher(TabGroupChangeListener.TOPIC).groupsChanged()
    }

    private fun requireName(name: String): String = name.trim().also { require(it.isNotEmpty()) { "Group name must not be blank." } }

    private fun requireColorId(colorId: String): String = colorId.trim().also { require(it.isNotEmpty()) { "Color ID must not be blank." } }

    private fun distinctReferences(references: Collection<TabReference>): List<TabReference> =
        references.filter { it.fileUrl.isNotBlank() }.distinctBy { it.fileUrl }.map { it.copy() }

    private fun copyGroup(group: TabGroupRecord): TabGroupRecord = group.copy(tabs = group.tabs.map { it.copy() }.toMutableList())
}

data class CapturedOpenTabs(
    val tabs: List<TabReference>,
    val activeFileUrl: String?,
)
