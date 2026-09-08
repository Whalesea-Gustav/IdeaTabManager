package com.whalesea.ideatabmanager.service

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectSelectionCollectorTest {
    @Test
    fun `collecting a selected file returns that file`() {
        withTempDir { root ->
            val source = Files.createFile(root.resolve("PlayerController.cpp"))

            val result = ProjectSelectionCollector.collect(listOf(source))

            assertEquals(listOf(source.toAbsolutePath().normalize()), result.files)
            assertFalse(result.truncated)
        }
    }

    @Test
    fun `duplicate selected files are kept once in selection order`() {
        withTempDir { root ->
            val first = Files.createFile(root.resolve("A.kt"))
            val second = Files.createFile(root.resolve("B.kt"))

            val result = ProjectSelectionCollector.collect(listOf(first, second, first))

            assertEquals(
                listOf(first.toAbsolutePath().normalize(), second.toAbsolutePath().normalize()),
                result.files,
            )
        }
    }

    @Test
    fun `collecting a directory returns nested files and not the directory itself`() {
        withTempDir { root ->
            val combat = Files.createDirectories(root.resolve("Combat"))
            val header = Files.createFile(combat.resolve("CombatComponent.h"))
            val nested = Files.createDirectories(combat.resolve("Private"))
            val source = Files.createFile(nested.resolve("CombatComponent.cpp"))

            val result = ProjectSelectionCollector.collect(listOf(combat))

            assertEquals(
                listOf(header.toAbsolutePath().normalize(), source.toAbsolutePath().normalize()),
                result.files,
            )
        }
    }

    @Test
    fun `junk directories and binary files are skipped while expanding a folder`() {
        withTempDir { root ->
            val feature = Files.createDirectories(root.resolve("Inventory"))
            val source = Files.createFile(feature.resolve("InventoryComponent.cpp"))
            Files.createFile(feature.resolve("icon.png"))
            val git = Files.createDirectories(feature.resolve(".git"))
            Files.createFile(git.resolve("HEAD"))
            val intermediate = Files.createDirectories(feature.resolve("Intermediate"))
            Files.createFile(intermediate.resolve("note.txt"))

            val result = ProjectSelectionCollector.collect(listOf(feature))

            assertEquals(listOf(source.toAbsolutePath().normalize()), result.files)
        }
    }

    @Test
    fun `explicitly selected binary files are skipped`() {
        withTempDir { root ->
            val image = Files.createFile(root.resolve("splash.png"))

            val result = ProjectSelectionCollector.collect(listOf(image))

            assertEquals(emptyList(), result.files)
        }
    }

    @Test
    fun `custom skipDirectory predicate excludes additional folders`() {
        withTempDir { root ->
            val feature = Files.createDirectories(root.resolve("Quest"))
            val kept = Files.createFile(feature.resolve("Quest.cpp"))
            val generated = Files.createDirectories(feature.resolve("Generated"))
            Files.createFile(generated.resolve("Quest.gen.cpp"))

            val result = ProjectSelectionCollector.collect(
                listOf(feature),
                skipDirectory = { path ->
                    ProjectSelectionCollector.isSkippedDirectory(path) || path.fileName.toString() == "Generated"
                },
            )

            assertEquals(listOf(kept.toAbsolutePath().normalize()), result.files)
        }
    }

    @Test
    fun `collection stops and reports truncation when the hard limit is reached`() {
        withTempDir { root ->
            val folder = Files.createDirectories(root.resolve("Many"))
            repeat(4) { index ->
                Files.createFile(folder.resolve("File$index.kt"))
            }

            val result = ProjectSelectionCollector.collect(listOf(folder), limit = 2)

            assertEquals(2, result.files.size)
            assertTrue(result.truncated)
        }
    }

    @Test
    fun `needsConfirmation follows the batch-add threshold`() {
        assertFalse(ProjectSelectionCollector.needsConfirmation(99))
        assertTrue(ProjectSelectionCollector.needsConfirmation(100))
        assertTrue(ProjectSelectionCollector.needsConfirmation(500))
    }

    @Test
    fun `mixed file and folder selections keep files and expand directories`() {
        withTempDir { root ->
            val extra = Files.createFile(root.resolve("Shared.h"))
            val combat = Files.createDirectories(root.resolve("Combat"))
            val source = Files.createFile(combat.resolve("Combat.cpp"))

            val result = ProjectSelectionCollector.collect(listOf(extra, combat))

            assertEquals(
                listOf(extra.toAbsolutePath().normalize(), source.toAbsolutePath().normalize()),
                result.files,
            )
        }
    }

    @Test
    fun `selecting a skipped directory by itself yields no files`() {
        withTempDir { root ->
            val intermediate = Files.createDirectories(root.resolve("Intermediate"))
            Files.createFile(intermediate.resolve("note.txt"))

            val result = ProjectSelectionCollector.collect(listOf(intermediate))

            assertEquals(emptyList(), result.files)
        }
    }

    private fun withTempDir(block: (Path) -> Unit) {
        val temporaryDirectory = Files.createTempDirectory("tab-group-selection-")
        try {
            block(temporaryDirectory)
        } finally {
            temporaryDirectory.toFile().deleteRecursively()
        }
    }
}
