package com.whalesea.ideatabmanager.service

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros

/**
 * Project-private storage root for future Tab Group records.
 *
 * The state deliberately lives in workspace.xml: groups describe an individual developer's
 * current coding context and must not become shared project configuration by default.
 */
@State(
    name = "IdeaTabManager.TabGroups",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)],
)
class TabGroupProjectState : PersistentStateComponent<TabGroupProjectState.State> {
    data class State(
        var schemaVersion: Int = 1,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }
}
