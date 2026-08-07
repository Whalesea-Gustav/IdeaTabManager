package com.whalesea.ideatabmanager.tortoise

import java.nio.file.Files
import java.nio.file.Path
import java.util.LinkedHashSet

internal data class TortoiseCommitInvocation(
    val arguments: List<String>,
    val pathFile: Path? = null,
) {
    fun deletePathFileIfPresent() {
        pathFile?.let { runCatching { Files.deleteIfExists(it) } }
    }
}

/** Builds documented command-line arguments without shell quoting or command concatenation. */
internal object TortoiseCommitInvocationBuilder {
    fun build(kind: TortoiseVcsKind, selectedPaths: Collection<Path>, temporaryDirectory: Path? = null): TortoiseCommitInvocation {
        val paths = normalize(selectedPaths)
        require(paths.isNotEmpty()) { "A Tortoise commit selection cannot be empty." }
        return when (kind) {
            TortoiseVcsKind.SVN -> buildSvn(paths, temporaryDirectory)
            TortoiseVcsKind.GIT -> buildGit(paths)
        }
    }

    private fun buildSvn(paths: List<Path>, temporaryDirectory: Path?): TortoiseCommitInvocation {
        val arguments = mutableListOf("/command:commit")
        var pathFile: Path? = null
        if (paths.size == 1) {
            arguments += "/path:${paths.single()}"
        } else {
            pathFile = TortoisePathFile.create(paths, temporaryDirectory)
            arguments += "/pathfile:$pathFile"
            arguments += "/deletepathfile"
        }
        arguments += "/closeonend:0"
        return TortoiseCommitInvocation(arguments, pathFile)
    }

    private fun buildGit(paths: List<Path>): TortoiseCommitInvocation = TortoiseCommitInvocation(
        arguments = listOf(
            "/command:commit",
            // TortoiseGit's documented multi-path syntax is one /path argument, delimited by '*'.
            "/path:${paths.joinToString("*")}",
            "/closeonend:0",
        ),
    )

    private fun normalize(selectedPaths: Collection<Path>): List<Path> {
        val paths = LinkedHashSet<Path>()
        selectedPaths.filterNotNull().forEach { path ->
            val normalized = path.toAbsolutePath().normalize()
            require('\r' !in normalized.toString() && '\n' !in normalized.toString()) {
                "Paths containing line breaks are not supported."
            }
            paths.add(normalized)
        }
        return paths.toList()
    }
}
