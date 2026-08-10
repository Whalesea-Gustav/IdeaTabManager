package com.whalesea.ideatabmanager.model

/** Serializable project-workspace root for all saved tab groups. */
data class TabGroupState(
    var schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    var groups: MutableList<TabGroupRecord> = mutableListOf(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
