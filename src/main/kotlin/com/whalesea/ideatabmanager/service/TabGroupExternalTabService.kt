package com.whalesea.ideatabmanager.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.whalesea.ideatabmanager.model.TabGroupRecord

data class ExternalTabCandidate(
    val file: VirtualFile,
    val displayName: String,
    val relativePath: String?,
)

data class ExternalTabCloseResult(
    val closedFileCount: Int,
    val skippedModifiedFileCount: Int,
    val skippedNoLongerOpenCount: Int,
)

/** Public-API-only inspection and closing of tabs outside a target group. */
class TabGroupExternalTabService(private val project: Project) {
    fun cleanExternalTabs(group: TabGroupRecord): List<ExternalTabCandidate> {
        val fileEditorManager = FileEditorManager.getInstance(project)
        val documentManager = FileDocumentManager.getInstance()
        val targetUrls = group.tabs.map { it.fileUrl }.toSet()
        return fileEditorManager.openFiles
            .filter { it.url !in targetUrls }
            .filterNot { file -> documentManager.getDocument(file)?.let(documentManager::isDocumentUnsaved) == true }
            .map(::toCandidate)
    }

    fun closeCleanExternalTabs(group: TabGroupRecord, requestedFiles: Collection<VirtualFile>): ExternalTabCloseResult {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val fileEditorManager = FileEditorManager.getInstance(project)
        val documentManager = FileDocumentManager.getInstance()
        val targetUrls = group.tabs.map { it.fileUrl }.toSet()
        val requestedUrls = requestedFiles.map { it.url }.toSet()
        var closed = 0
        var skippedModified = 0
        var skippedNoLongerOpen = 0

        requestedUrls.forEach { url ->
            val file = fileEditorManager.openFiles.firstOrNull { it.url == url }
            if (file == null) {
                skippedNoLongerOpen++
            } else if (file.url in targetUrls) {
                skippedNoLongerOpen++
            } else if (documentManager.getDocument(file)?.let(documentManager::isDocumentUnsaved) == true) {
                skippedModified++
            } else {
                fileEditorManager.closeFile(file)
                closed++
            }
        }

        return ExternalTabCloseResult(closed, skippedModified, skippedNoLongerOpen)
    }

    private fun toCandidate(file: VirtualFile): ExternalTabCandidate = ExternalTabCandidate(
        file = file,
        displayName = file.name,
        relativePath = project.basePath?.let { FileUtil.getRelativePath(it, file.path, '/') }
            ?.takeIf(String::isNotBlank),
    )
}
