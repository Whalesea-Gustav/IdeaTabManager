package com.whalesea.ideatabmanager.tortoise

import com.intellij.openapi.vfs.VirtualFileManager
import com.whalesea.ideatabmanager.model.TabReference
import java.nio.file.Files
import java.nio.file.Path
import java.util.LinkedHashMap
import java.util.LinkedHashSet

/** A single external commit dialog target. Paths always belong to one working-copy root. */
data class TortoiseCommitTarget(
    val kind: TortoiseVcsKind,
    val workingCopyRoot: Path,
    val paths: List<Path>,
) {
    val fileCount: Int get() = paths.size
}

/**
 * Detects VCS ownership without depending on the IDE's optional Git/Subversion plugins.
 *
 * A file is eligible when its nearest ancestor working-copy marker is `.git` (a directory
 * or a worktree marker file) or `.svn` (a directory). This deliberately keeps unversioned
 * files within an existing working copy: both Tortoise commit dialogs can present them to
 * the user, while the user remains in control of whether to add or commit them.
 */
internal object TortoiseWorkingCopyClassifier {
    fun classifyReferences(references: Collection<TabReference>): List<TortoiseCommitTarget> {
        val paths = references.mapNotNull { reference ->
            val file = VirtualFileManager.getInstance().findFileByUrl(reference.fileUrl) ?: return@mapNotNull null
            if (!file.isValid || file.isDirectory || file.fileSystem.protocol != "file") return@mapNotNull null
            runCatching { Path.of(file.path) }.getOrNull()
        }
        return classifyPaths(paths)
    }

    fun classifyPaths(selectedPaths: Collection<Path>): List<TortoiseCommitTarget> {
        val grouped = LinkedHashMap<WorkingCopyKey, LinkedHashSet<Path>>()
        selectedPaths.asSequence()
            .mapNotNull(::normalizeRegularFile)
            .forEach { path ->
                val workingCopy = findWorkingCopy(path) ?: return@forEach
                grouped.getOrPut(WorkingCopyKey(workingCopy.kind, workingCopy.root)) { LinkedHashSet() }.add(path)
            }

        return grouped.map { (key, paths) ->
            TortoiseCommitTarget(key.kind, key.root, paths.toList())
        }.sortedWith(
            compareBy<TortoiseCommitTarget> { it.kind.ordinal }
                .thenBy { it.workingCopyRoot.toString().lowercase() },
        )
    }

    private fun normalizeRegularFile(path: Path): Path? = runCatching {
        path.toAbsolutePath().normalize().takeIf(Files::isRegularFile)
    }.getOrNull()

    private fun findWorkingCopy(file: Path): WorkingCopy? {
        var directory = file.parent ?: return null
        while (true) {
            when {
                hasGitMarker(directory) -> return WorkingCopy(TortoiseVcsKind.GIT, directory)
                hasSvnMarker(directory) -> return WorkingCopy(TortoiseVcsKind.SVN, directory)
            }
            directory = directory.parent ?: return null
        }
    }

    private fun hasGitMarker(directory: Path): Boolean = runCatching {
        val marker = directory.resolve(".git")
        Files.isDirectory(marker) || Files.isRegularFile(marker)
    }.getOrDefault(false)

    private fun hasSvnMarker(directory: Path): Boolean = runCatching {
        Files.isDirectory(directory.resolve(".svn"))
    }.getOrDefault(false)

    private data class WorkingCopy(val kind: TortoiseVcsKind, val root: Path)

    private data class WorkingCopyKey(val kind: TortoiseVcsKind, val root: Path)
}
