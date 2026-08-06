package com.whalesea.ideatabmanager.model

/** A named, user-owned coding context. Files can occur in more than one group. */
data class TabGroupRecord(
    var id: String = "",
    var name: String = "",
    var colorId: String = DEFAULT_COLOR_ID,
    var tabs: MutableList<TabReference> = mutableListOf(),
    var activeFileUrl: String? = null,
    var createdAtEpochMs: Long = 0,
    var updatedAtEpochMs: Long = 0,
) {
    companion object {
        const val DEFAULT_COLOR_ID = "blue"
    }
}
