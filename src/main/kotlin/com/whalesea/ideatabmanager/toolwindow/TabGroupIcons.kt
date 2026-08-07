package com.whalesea.ideatabmanager.toolwindow

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/** Tool Window icons supplied by this plugin, including light and Darcula variants. */
object TabGroupIcons {
    val collapse: Icon = IconLoader.getIcon("/icons/collapseGroup.svg", TabGroupIcons::class.java)
    val expand: Icon = IconLoader.getIcon("/icons/expandGroup.svg", TabGroupIcons::class.java)
    val newEmptyGroup: Icon = IconLoader.getIcon("/icons/newEmptyGroup.svg", TabGroupIcons::class.java)
    val saveCurrentTabs: Icon = IconLoader.getIcon("/icons/saveCurrentTabs.svg", TabGroupIcons::class.java)
    val saveSelectedTabs: Icon = IconLoader.getIcon("/icons/saveSelectedTabs.svg", TabGroupIcons::class.java)
}
