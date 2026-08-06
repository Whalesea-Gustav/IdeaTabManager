package com.whalesea.ideatabmanager

import com.intellij.openapi.project.Project
import com.whalesea.ideatabmanager.model.TabGroupState
import com.whalesea.ideatabmanager.model.TabReference
import com.whalesea.ideatabmanager.service.TabGroupProjectState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import java.lang.reflect.Proxy

class TabGroupProjectStateTest {
    @Test
    fun `new project state starts at the first schema version`() {
        assertEquals(1, TabGroupState().schemaVersion)
    }

    @Test
    fun `a group deduplicates URLs but files can belong to multiple groups`() {
        val state = newState()
        val shared = reference("file:///project/CommonTypes.h")
        val combat = state.createGroup("Combat", "red", listOf(shared, shared.copy()), shared.fileUrl)
        val inventory = state.createGroup("Inventory", "green", listOf(shared), shared.fileUrl)

        assertEquals(1, combat.tabs.size)
        assertEquals(1, inventory.tabs.size)
        assertEquals(2, state.groups().size)
    }

    @Test
    fun `removing the active file selects the next group file`() {
        val state = newState()
        val first = reference("file:///project/A.kt")
        val second = reference("file:///project/B.kt")
        val group = state.createGroup("Feature", "blue", listOf(first, second), first.fileUrl)

        val updated = state.removeTabFromGroup(group.id, first.fileUrl)

        assertEquals(listOf(second.fileUrl), updated?.tabs?.map { it.fileUrl })
        assertEquals(second.fileUrl, updated?.activeFileUrl)
    }

    @Test
    fun `service exposes defensive copies of persisted records`() {
        val state = newState()
        val group = state.createGroup("Feature", "blue", listOf(reference("file:///project/A.kt")), null)

        val exposed = state.groups().single()
        exposed.name = "Mutated outside service"
        exposed.tabs.clear()

        val persisted = state.groups().single()
        assertEquals(group.name, persisted.name)
        assertEquals(1, persisted.tabs.size)
    }

    @Test
    fun `updating a group clears an active file no longer in its snapshot`() {
        val state = newState()
        val first = reference("file:///project/A.kt")
        val second = reference("file:///project/B.kt")
        val group = state.createGroup("Feature", "blue", listOf(first), first.fileUrl)

        val updated = state.updateGroup(group.id, listOf(second, second.copy()), first.fileUrl)

        assertEquals(listOf(second.fileUrl), updated?.tabs?.map { it.fileUrl })
        assertNull(updated?.activeFileUrl)
    }

    @Test
    fun `blank group names and color IDs are rejected`() {
        val state = newState()

        assertFailsWith<IllegalArgumentException> { state.createGroup("   ") }
        assertFailsWith<IllegalArgumentException> { state.createGroup("Feature", " ") }
    }

    private fun newState(): TabGroupProjectState = TabGroupProjectState(
        Proxy.newProxyInstance(
            Project::class.java.classLoader,
            arrayOf(Project::class.java),
        ) { _, _, _ -> null } as Project,
    )

    private fun reference(url: String): TabReference = TabReference(
        fileUrl = url,
        lastKnownName = url.substringAfterLast('/'),
    )
}
