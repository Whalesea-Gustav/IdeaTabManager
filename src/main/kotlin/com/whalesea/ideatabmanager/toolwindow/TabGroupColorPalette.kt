package com.whalesea.ideatabmanager.toolwindow

import com.intellij.ui.JBColor
import com.whalesea.ideatabmanager.IdeaTabManagerBundle
import com.whalesea.ideatabmanager.model.TabGroupRecord
import java.awt.Color
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

    fun colorFor(colorId: String): TabGroupColor {
        val normalized = normalizeColorId(colorId) ?: return colors.first()
        colors.firstOrNull { it.id == normalized }?.let { return it }
        val rgb = parseHexRgb(normalized) ?: return colors.first()
        return TabGroupColor(normalized, normalized, JBColor(rgb, rgb))
    }

    fun colorIds(): Array<String> = colors.map { it.id }.toTypedArray()

    fun displayName(colorId: String): String = colorFor(colorId).displayName

    fun idForDisplayName(displayName: String): String = colors.firstOrNull { it.displayName == displayName }?.id
        ?: TabGroupRecord.DEFAULT_COLOR_ID

    fun displayNames(): Array<String> = colors.map { it.displayName }.toTypedArray()

    fun randomColorId(): String = colors.random(Random.Default).id

    fun encodeHex(red: Int, green: Int, blue: Int): String =
        "#%02X%02X%02X".format(red.coerceIn(0, 255), green.coerceIn(0, 255), blue.coerceIn(0, 255))

    fun normalizeColorId(colorId: String): String? {
        val trimmed = colorId.trim()
        if (trimmed.isEmpty()) return null
        colors.firstOrNull { it.id.equals(trimmed, ignoreCase = true) }?.let { return it.id }
        HEX_COLOR.matchEntire(trimmed)?.let { return "#${it.groupValues[1].uppercase()}" }
        SHORT_HEX_COLOR.matchEntire(trimmed)?.let { match ->
            val digits = match.groupValues[1]
            return "#${digits[0]}${digits[0]}${digits[1]}${digits[1]}${digits[2]}${digits[2]}".uppercase()
        }
        parseRgbTriple(trimmed)?.let { (red, green, blue) -> return encodeHex(red, green, blue) }
        return null
    }

    fun rgbComponents(colorId: String): Triple<Int, Int, Int> {
        val awt = Color(colorFor(colorId).color.rgb, true)
        return Triple(awt.red, awt.green, awt.blue)
    }

    fun isNamedColor(colorId: String): Boolean = colors.any { it.id == normalizeColorId(colorId) }

    private fun parseHexRgb(colorId: String): Int? = HEX_COLOR.matchEntire(colorId)?.groupValues?.get(1)?.toIntOrNull(16)

    private fun parseRgbTriple(value: String): Triple<Int, Int, Int>? {
        val match = RGB_TRIPLE.matchEntire(value) ?: return null
        val red = match.groupValues[1].toInt()
        val green = match.groupValues[2].toInt()
        val blue = match.groupValues[3].toInt()
        if (red !in 0..255 || green !in 0..255 || blue !in 0..255) return null
        return Triple(red, green, blue)
    }

    private val HEX_COLOR = Regex("^#([0-9A-Fa-f]{6})$")
    private val SHORT_HEX_COLOR = Regex("^#([0-9A-Fa-f]{3})$")
    private val RGB_TRIPLE = Regex("^(?:rgb\\s*\\()?\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*\\)?$", RegexOption.IGNORE_CASE)
}
