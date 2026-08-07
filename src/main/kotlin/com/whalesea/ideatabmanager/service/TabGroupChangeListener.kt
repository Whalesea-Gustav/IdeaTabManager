package com.whalesea.ideatabmanager.service

import com.intellij.util.messages.Topic

/** Project-local event used by actions and the Tool Window to keep one rendered state. */
fun interface TabGroupChangeListener {
    fun groupsChanged()

    companion object {
        val TOPIC: Topic<TabGroupChangeListener> = Topic.create("IdeaTabManager tab groups changed", TabGroupChangeListener::class.java)
    }
}
