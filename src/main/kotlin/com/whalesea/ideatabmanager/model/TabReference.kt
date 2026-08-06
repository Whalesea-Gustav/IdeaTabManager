package com.whalesea.ideatabmanager.model

/** A stable reference to an editor file and its best-effort text caret position. */
data class TabReference(
    var fileUrl: String = "",
    var projectRelativePath: String? = null,
    var lastKnownName: String = "",
    var caretOffset: Int? = null,
)
