package com.whalesea.ideatabmanager.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.whalesea.ideatabmanager.model.TabGroupRecord
import com.whalesea.ideatabmanager.model.TabReference

data class PreparedTabGroupRestore(
    val groupId: String,
    val resolvedTabs: List<ResolvedTabReference>,
    val missingTabs: List<TabReference>,
    val activeFileUrl: String?,
)

data class ResolvedTabReference(
    val reference: TabReference,
    val file: VirtualFile,
)

data class TabGroupRestoreResult(
    val groupId: String,
    val openedFileCount: Int,
    val missingTabs: List<TabReference>,
    val activeFileRestored: Boolean,
)

data class FocusGroupResult(
    val restoreResult: TabGroupRestoreResult,
    val closedFileCount: Int,
    val keptModifiedFileCount: Int,
    val keptPinnedFileCount: Int,
    val automaticCleanupAvailable: Boolean,
)

/**
 * Opens a saved context through public File Editor APIs only.
 *
 * Resolution is deliberately separate from UI work so callers can prepare it on a pooled thread.
 */
class TabGroupRestorer(private val project: Project) {
    private val resolver = TabGroupFileResolver(project)

    fun restore(group: TabGroupRecord, onComplete: (TabGroupRestoreResult) -> Unit = {}) {
        val groupSnapshot = group.copy(tabs = group.tabs.map(::copyReference).toMutableList())
        ApplicationManager.getApplication().executeOnPooledThread {
            val prepared = prepare(groupSnapshot)
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) {
                    onComplete(restorePrepared(prepared))
                }
            }
        }
    }

    /**
     * Restores the target context first, then removes only safe non-target tabs.
     * Pinned and unsaved editors are intentionally retained.
     */
    fun focus(group: TabGroupRecord, onComplete: (FocusGroupResult) -> Unit = {}) {
        val groupSnapshot = group.copy(tabs = group.tabs.map(::copyReference).toMutableList())
        ApplicationManager.getApplication().executeOnPooledThread {
            val prepared = prepare(groupSnapshot)
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) {
                    onComplete(focusPrepared(prepared))
                }
            }
        }
    }

    fun prepare(group: TabGroupRecord): PreparedTabGroupRestore {
        val resolved = mutableListOf<ResolvedTabReference>()
        val missing = mutableListOf<TabReference>()

        group.tabs.forEach { reference ->
            val file = resolver.resolve(reference)
            if (file == null || !file.isValid || file.isDirectory) {
                missing += copyReference(reference)
            } else {
                resolved += ResolvedTabReference(copyReference(reference), file)
            }
        }

        return PreparedTabGroupRestore(group.id, resolved, missing, group.activeFileUrl)
    }

    fun restorePrepared(prepared: PreparedTabGroupRestore): TabGroupRestoreResult {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val fileEditorManager = FileEditorManager.getInstance(project)

        prepared.resolvedTabs.forEach { resolved ->
            fileEditorManager.openFile(resolved.file, false)
        }

        val active = prepared.resolvedTabs.firstOrNull { it.reference.fileUrl == prepared.activeFileUrl }
            ?: prepared.resolvedTabs.firstOrNull()
        val activeRestored = active?.let { openAndRestoreCaret(fileEditorManager, it) } ?: false

        return TabGroupRestoreResult(
            groupId = prepared.groupId,
            openedFileCount = prepared.resolvedTabs.size,
            missingTabs = prepared.missingTabs,
            activeFileRestored = activeRestored,
        )
    }

    fun focusPrepared(prepared: PreparedTabGroupRestore): FocusGroupResult {
        val restoreResult = restorePrepared(prepared)
        if (prepared.resolvedTabs.isEmpty()) {
            return FocusGroupResult(restoreResult, 0, 0, 0, true)
        }

        val fileEditorManager = FileEditorManager.getInstance(project)
        val documentManager = FileDocumentManager.getInstance()
        val targetUrls = prepared.resolvedTabs.map { it.file.url }.toSet()
        var closed = 0
        var keptModified = 0
        var keptPinned = 0

        fileEditorManager.openFiles
            .filter { it.url !in targetUrls }
            .toList()
            .forEach { file ->
                if (fileEditorManager.hasPinnedEditorTab(file)) {
                    keptPinned++
                } else if (documentManager.getDocument(file)?.let(documentManager::isDocumentUnsaved) == true) {
                    keptModified++
                } else {
                    fileEditorManager.closeFile(file)
                    closed++
                }
            }

        return FocusGroupResult(restoreResult, closed, keptModified, keptPinned, true)
    }

    private fun openAndRestoreCaret(fileEditorManager: FileEditorManager, active: ResolvedTabReference): Boolean {
        val offset = active.reference.caretOffset
        if (offset == null) {
            fileEditorManager.openFile(active.file, true)
            return true
        }

        val document = FileDocumentManager.getInstance().getDocument(active.file)
        if (document == null) {
            fileEditorManager.openFile(active.file, true)
            return true
        }

        val clampedOffset = offset.coerceIn(0, document.textLength)
        return fileEditorManager.openTextEditor(OpenFileDescriptor(project, active.file, clampedOffset), true) != null
    }

    private fun copyReference(reference: TabReference): TabReference = reference.copy()
}
