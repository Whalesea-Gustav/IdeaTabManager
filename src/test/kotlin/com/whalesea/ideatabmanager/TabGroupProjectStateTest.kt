package com.whalesea.ideatabmanager

import com.whalesea.ideatabmanager.service.TabGroupProjectState
import kotlin.test.Test
import kotlin.test.assertEquals

class TabGroupProjectStateTest {
    @Test
    fun `new project state starts at the first schema version`() {
        assertEquals(1, TabGroupProjectState.State().schemaVersion)
    }
}
