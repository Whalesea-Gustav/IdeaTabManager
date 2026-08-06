package com.whalesea.ideatabmanager.service

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.whalesea.ideatabmanager.model.TabReference
import java.nio.file.Path

/** Resolves saved references without deleting records whose files are temporarily unavailable. */
class TabGroupFileResolver(private val project: Project) {
    fun resolve(reference: TabReference): VirtualFile? {
        val fromProjectPath = reference.projectRelativePath
            ?.takeIf(String::isNotBlank)
            ?.let(::resolveProjectRelativePath)

        return fromProjectPath
            ?: reference.fileUrl.takeIf(String::isNotBlank)?.let(VirtualFileManager.getInstance()::findFileByUrl)
    }

    private fun resolveProjectRelativePath(relativePath: String): VirtualFile? {
        val basePath = project.basePath ?: return null
        val base = runCatching { Path.of(basePath).normalize() }.getOrNull() ?: return null
        val path = runCatching { base.resolve(relativePath).normalize() }.getOrNull() ?: return null
        if (!path.startsWith(base)) return null
        return LocalFileSystem.getInstance().findFileByNioFile(path)
    }
}
