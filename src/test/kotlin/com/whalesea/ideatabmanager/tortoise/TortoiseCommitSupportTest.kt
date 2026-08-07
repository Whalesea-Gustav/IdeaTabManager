package com.whalesea.ideatabmanager.tortoise

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TortoiseCommitSupportTest {
    @Test
    fun `classifier groups files by nearest Git and SVN working-copy roots`() {
        val temporaryDirectory = Files.createTempDirectory("tab-group-tortoise-test-")
        try {
            val gitRoot = Files.createDirectory(temporaryDirectory.resolve("git repository"))
            Files.createDirectory(gitRoot.resolve(".git"))
            val gitFirst = Files.createFile(gitRoot.resolve("first.kt"))
            val gitNested = Files.createDirectories(gitRoot.resolve("src")).resolve("second.kt").also(Files::createFile)

            val svnRoot = Files.createDirectory(temporaryDirectory.resolve("svn repository"))
            Files.createDirectory(svnRoot.resolve(".svn"))
            val svnFile = Files.createFile(svnRoot.resolve("asset.txt"))
            val outside = Files.createFile(temporaryDirectory.resolve("outside.txt"))

            val targets = TortoiseWorkingCopyClassifier.classifyPaths(listOf(gitFirst, gitNested, svnFile, outside))

            assertEquals(2, targets.size)
            assertEquals(TortoiseVcsKind.SVN, targets[0].kind)
            assertEquals(listOf(svnFile.toAbsolutePath().normalize()), targets[0].paths)
            assertEquals(TortoiseVcsKind.GIT, targets[1].kind)
            assertEquals(listOf(gitFirst.toAbsolutePath().normalize(), gitNested.toAbsolutePath().normalize()), targets[1].paths)
        } finally {
            temporaryDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `classifier recognizes Git worktree marker files`() {
        val temporaryDirectory = Files.createTempDirectory("tab-group-tgit-worktree-")
        try {
            val root = Files.createDirectory(temporaryDirectory.resolve("worktree"))
            Files.writeString(root.resolve(".git"), "gitdir: ../main/.git/worktrees/worktree\n")
            val source = Files.createFile(root.resolve("source.py"))

            val target = TortoiseWorkingCopyClassifier.classifyPaths(listOf(source)).single()

            assertEquals(TortoiseVcsKind.GIT, target.kind)
            assertEquals(root.toAbsolutePath().normalize(), target.workingCopyRoot)
        } finally {
            temporaryDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `SVN multi-path invocation writes the validated native path-file format`() {
        val temporaryDirectory = Files.createTempDirectory("tab-group-tsvn-pathfile-")
        try {
            val first = Files.createFile(temporaryDirectory.resolve("first file.txt"))
            val second = Files.createFile(temporaryDirectory.resolve("第二个文件.txt"))

            val invocation = TortoiseCommitInvocationBuilder.build(TortoiseVcsKind.SVN, listOf(first, second), temporaryDirectory)
            val pathFile = assertNotNull(invocation.pathFile)
            val bytes = Files.readAllBytes(pathFile)
            val content = String(bytes, StandardCharsets.UTF_16LE)

            assertTrue(invocation.arguments.contains("/command:commit"))
            assertTrue(invocation.arguments.contains("/pathfile:$pathFile"))
            assertTrue(invocation.arguments.contains("/deletepathfile"))
            assertFalse(bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte())
            assertFalse('\r' in content)
            assertEquals("${first.toAbsolutePath().normalize()}\n${second.toAbsolutePath().normalize()}\n", content)
            TortoisePathFile.validate(pathFile, listOf(first.toAbsolutePath().normalize(), second.toAbsolutePath().normalize()))
        } finally {
            temporaryDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `Git multi-path invocation uses the documented star-delimited path argument`() {
        val temporaryDirectory = Files.createTempDirectory("tab-group-tgit-invocation-")
        try {
            val first = Files.createFile(temporaryDirectory.resolve("first file.py"))
            val second = Files.createFile(temporaryDirectory.resolve("second file.py"))

            val invocation = TortoiseCommitInvocationBuilder.build(TortoiseVcsKind.GIT, listOf(first, second), temporaryDirectory)

            assertNull(invocation.pathFile)
            assertTrue(invocation.arguments.contains("/command:commit"))
            assertTrue(invocation.arguments.contains("/path:${first.toAbsolutePath().normalize()}*${second.toAbsolutePath().normalize()}"))
            assertTrue(invocation.arguments.contains("/closeonend:0"))
        } finally {
            temporaryDirectory.toFile().deleteRecursively()
        }
    }
}
