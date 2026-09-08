package com.whalesea.ideatabmanager.service

import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

data class ProjectSelectionCollectResult(
    val files: List<Path>,
    val truncated: Boolean,
)

/** Flattens Project View / Solution Explorer selections into local files for Tab Group membership. */
object ProjectSelectionCollector {
    const val CONFIRM_THRESHOLD = 100
    const val HARD_LIMIT = 500

    private val skippedDirectoryNames = setOf(
        ".git",
        ".svn",
        ".hg",
        ".idea",
        ".vs",
        "node_modules",
        "intermediate",
        "binaries",
        "saved",
        "deriveddatacache",
        "bin",
        "obj",
        "__pycache__",
    )

    private val skippedFileExtensions = setOf(
        "png", "jpg", "jpeg", "gif", "webp", "ico", "bmp", "tga", "psd",
        "dll", "exe", "pdb", "lib", "so", "dylib", "a",
        "zip", "7z", "rar", "gz", "tar", "pak",
        "uasset", "umap", "uexp", "ubulk",
        "woff", "woff2", "ttf", "eot",
        "mp3", "mp4", "wav", "ogg",
        "pdf",
    )

    fun collect(
        roots: Collection<Path>,
        skipDirectory: (Path) -> Boolean = ::isSkippedDirectory,
        skipFile: (Path) -> Boolean = ::isSkippedFile,
        limit: Int = HARD_LIMIT,
    ): ProjectSelectionCollectResult {
        val collected = LinkedHashSet<Path>()
        var truncated = false
        for (root in roots) {
            if (truncated) break
            val normalized = runCatching { root.toAbsolutePath().normalize() }.getOrNull() ?: continue
            if (!Files.exists(normalized)) continue
            truncated = !collectPath(normalized, collected, skipDirectory, skipFile, limit)
        }
        return ProjectSelectionCollectResult(collected.toList(), truncated)
    }

    fun needsConfirmation(fileCount: Int): Boolean = fileCount >= CONFIRM_THRESHOLD

    fun isSkippedDirectory(path: Path): Boolean = path.name.lowercase(Locale.ROOT) in skippedDirectoryNames

    fun isSkippedFile(path: Path): Boolean = path.extension.lowercase(Locale.ROOT) in skippedFileExtensions

    private fun collectPath(
        path: Path,
        collected: LinkedHashSet<Path>,
        skipDirectory: (Path) -> Boolean,
        skipFile: (Path) -> Boolean,
        limit: Int,
    ): Boolean {
        if (path.isDirectory()) {
            if (skipDirectory(path)) return true
            val children = runCatching { path.listDirectoryEntries() }.getOrElse { emptyList() }
                .sortedBy { it.name.lowercase(Locale.ROOT) }
            for (child in children) {
                if (!collectPath(child, collected, skipDirectory, skipFile, limit)) {
                    return false
                }
            }
            return true
        }
        if (!path.isRegularFile() || skipFile(path)) return true
        if (collected.size >= limit) return false
        collected.add(path)
        return true
    }
}
