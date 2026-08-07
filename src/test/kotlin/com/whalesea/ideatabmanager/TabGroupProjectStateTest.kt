package com.whalesea.ideatabmanager

import com.intellij.openapi.project.Project
import com.intellij.util.messages.MessageBus
import com.whalesea.ideatabmanager.model.TabGroupState
import com.whalesea.ideatabmanager.model.TabGroupRecord
import com.whalesea.ideatabmanager.model.TabReference
import com.whalesea.ideatabmanager.service.TabGroupChangeListener
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
    fun `focus safety notice is acknowledged only once per project workspace`() {
        val state = newState()

        assertEquals(true, state.needsFocusSafetyNotice())
        state.acknowledgeFocusSafetyNotice()

        assertEquals(false, state.needsFocusSafetyNotice())
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
    fun `collapse state is persisted with the group record`() {
        val state = newState()
        val group = state.createGroup("Feature")

        val collapsed = state.setGroupCollapsed(group.id, true)

        assertEquals(true, collapsed?.isCollapsed)
        assertEquals(true, state.groups().single().isCollapsed)
    }

    @Test
    fun `group title and note are persisted as separate user-facing metadata`() {
        val state = newState()
        val group = state.createGroup("Combat")

        state.renameGroup(group.id, "Combat Refactor")
        state.updateGroupComment(group.id, "AI, hit reactions, and shared combat types")

        val stored = state.groups().single()
        assertEquals("Combat Refactor", stored.name)
        assertEquals("AI, hit reactions, and shared combat types", stored.comment)
    }

    @Test
    fun `recent groups are ordered by last use time`() {
        val state = newState()
        state.loadState(
            TabGroupState(
                groups = mutableListOf(
                    TabGroupRecord(id = "older", name = "Older", lastUsedAtEpochMs = 100),
                    TabGroupRecord(id = "recent", name = "Recent", lastUsedAtEpochMs = 200),
                ),
            ),
        )

        assertEquals(listOf("recent", "older"), state.recentGroups().map { it.id })
    }

    @Test
    fun `moving a group changes only persisted display order`() {
        val state = newState()
        val first = state.createGroup("First", "blue", listOf(reference("file:///project/First.kt")), null)
        val second = state.createGroup("Second", "red", listOf(reference("file:///project/Second.kt")), null)
        val third = state.createGroup("Third", "green", listOf(reference("file:///project/Third.kt")), null)

        assertEquals(true, state.moveGroupBefore(third.id, first.id))
        assertEquals(listOf(third.id, first.id, second.id), state.groups().map { it.id })
        assertEquals(listOf("file:///project/Third.kt"), state.groups().first().tabs.map { it.fileUrl })
        assertEquals(false, state.moveGroupBefore(third.id, first.id))
        assertEquals(true, state.moveGroupBefore(third.id, null))
        assertEquals(listOf(first.id, second.id, third.id), state.groups().map { it.id })
        assertEquals(false, state.moveGroupBefore("missing", null))
    }

    @Test
    fun `blank group names and color IDs are rejected`() {
        val state = newState()

        assertFailsWith<IllegalArgumentException> { state.createGroup("   ") }
        assertFailsWith<IllegalArgumentException> { state.createGroup("Feature", " ") }
    }

    private fun newState(): TabGroupProjectState {
        val messageBus = Proxy.newProxyInstance(
            MessageBus::class.java.classLoader,
            arrayOf(MessageBus::class.java),
        ) { _, method, _ ->
            if (method.name == "syncPublisher") TabGroupChangeListener { } else null
        } as MessageBus
        val project = Proxy.newProxyInstance(
            Project::class.java.classLoader,
            arrayOf(Project::class.java),
        ) { _, method, _ ->
            if (method.name == "getMessageBus") messageBus else null
        } as Project
        return TabGroupProjectState(project)
    }

    private fun reference(url: String): TabReference = TabReference(
        fileUrl = url,
        lastKnownName = url.substringAfterLast('/'),
    )
}
