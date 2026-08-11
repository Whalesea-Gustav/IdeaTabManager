package com.whalesea.ideatabmanager.toolwindow

import com.intellij.ui.JBColor
import com.whalesea.ideatabmanager.IdeaTabManagerBundle
import com.whalesea.ideatabmanager.model.TabGroupRecord
import kotlin.random.Random

data class TabGroupColor(
    val id: String,
    val displayName: String,
    val color: JBColor,
)

object TabGroupColorPalette {
    val colors = listOf(
        TabGroupColor("blue", IdeaTabManagerBundle.message("color.blue"), JBColor(0x3D7EFF, 0x5B9DFF)),
        TabGroupColor("green", IdeaTabManagerBundle.message("color.green"), JBColor(0x3A9B5C, 0x5EC77A)),
        TabGroupColor("red", IdeaTabManagerBundle.message("color.red"), JBColor(0xC45151, 0xEF7777)),
        TabGroupColor("orange", IdeaTabManagerBundle.message("color.orange"), JBColor(0xC77A27, 0xF0A14A)),
        TabGroupColor("purple", IdeaTabManagerBundle.message("color.purple"), JBColor(0x8558C7, 0xAD83EA)),
    )

    fun colorFor(colorId: String): TabGroupColor = colors.firstOrNull { it.id == colorId } ?: colors.first()

    fun colorIds(): Array<String> = colors.map { it.id }.toTypedArray()

    fun displayName(colorId: String): String = colorFor(colorId).displayName

    fun idForDisplayName(displayName: String): String = colors.firstOrNull { it.displayName == displayName }?.id
        ?: TabGroupRecord.DEFAULT_COLOR_ID

    fun displayNames(): Array<String> = colors.map { it.displayName }.toTypedArray()

    fun randomColorId(): String = colors.random(Random.Default).id
}
