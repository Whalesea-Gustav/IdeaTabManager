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
