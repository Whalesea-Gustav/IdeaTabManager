package com.whalesea.ideatabmanager.toolwindow

import kotlin.test.Test
import kotlin.test.assertEquals

class TabGroupColorPaletteTest {
    @Test
    fun `named color ids still resolve to the built-in palette`() {
        assertEquals("blue", TabGroupColorPalette.normalizeColorId("blue"))
        assertEquals("red", TabGroupColorPalette.colorFor("red").id)
    }

    @Test
    fun `hex colors are normalized and used as custom ids`() {
        assertEquals("#3D7EFF", TabGroupColorPalette.normalizeColorId("#3d7eff"))
        assertEquals("#3D7EFF", TabGroupColorPalette.colorFor("#3d7eff").id)
    }

    @Test
    fun `short hex and rgb triples encode to six-digit hex`() {
        assertEquals("#112233", TabGroupColorPalette.normalizeColorId("#123"))
        assertEquals("#0A14C8", TabGroupColorPalette.normalizeColorId("10,20,200"))
        assertEquals("#FF0000", TabGroupColorPalette.encodeHex(255, 0, 0))
    }

    @Test
    fun `rgb components round-trip through a custom color id`() {
        val id = TabGroupColorPalette.encodeHex(12, 34, 56)
        assertEquals(Triple(12, 34, 56), TabGroupColorPalette.rgbComponents(id))
    }

    @Test
    fun `invalid color ids fall back to the default named color`() {
        assertEquals(null, TabGroupColorPalette.normalizeColorId("not-a-color"))
        assertEquals("blue", TabGroupColorPalette.colorFor("not-a-color").id)
        assertEquals("blue", TabGroupColorPalette.colorFor("").id)
    }
}
