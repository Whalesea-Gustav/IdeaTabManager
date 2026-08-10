package com.whalesea.ideatabmanager.toolwindow

import com.intellij.openapi.util.IconLoader
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.image.BufferedImage
import kotlin.math.min
import javax.swing.Icon

/** Tool Window icons supplied by this plugin, including light and Darcula variants. */
object TabGroupIcons {
    val collapse: Icon = IconLoader.getIcon("/icons/collapseGroup.svg", TabGroupIcons::class.java)
    val expand: Icon = IconLoader.getIcon("/icons/expandGroup.svg", TabGroupIcons::class.java)
    val groupDragHandle: Icon = IconLoader.getIcon("/icons/groupDragHandle.svg", TabGroupIcons::class.java)
    val tabDragHandle: Icon = IconLoader.getIcon("/icons/tabDragHandle.svg", TabGroupIcons::class.java)
    val newEmptyGroup: Icon = IconLoader.getIcon("/icons/newEmptyGroup.svg", TabGroupIcons::class.java)
    val saveCurrentTabs: Icon = IconLoader.getIcon("/icons/saveCurrentTabs.svg", TabGroupIcons::class.java)
    val saveSelectedTabs: Icon = IconLoader.getIcon("/icons/saveSelectedTabs.svg", TabGroupIcons::class.java)
}

/** Compact four-way cursor used only while hovering a Tab Group reorder grip. */
object TabGroupCursors {
    val reorder: Cursor by lazy(::createReorderCursor)

    private fun createReorderCursor(): Cursor = runCatching {
        if (GraphicsEnvironment.isHeadless()) return@runCatching Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
        val toolkit = Toolkit.getDefaultToolkit()
        val cursorSize = toolkit.getBestCursorSize(CURSOR_CANVAS_SIZE, CURSOR_CANVAS_SIZE)
        if (cursorSize.width <= 0 || cursorSize.height <= 0) return@runCatching Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)

        val image = BufferedImage(cursorSize.width, cursorSize.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            drawFourWayGlyph(
                graphics,
                cursorSize.width / 2f,
                cursorSize.height / 2f,
                min(7f, min(cursorSize.width, cursorSize.height) * 0.26f),
            )
        } finally {
            graphics.dispose()
        }
        toolkit.createCustomCursor(image, Point(cursorSize.width / 2, cursorSize.height / 2), "TabGroupReorder")
    }.getOrElse { Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR) }

    private fun drawFourWayGlyph(graphics: Graphics2D, centerX: Float, centerY: Float, radius: Float) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        drawFourWayGlyph(graphics, centerX, centerY, radius, Color(255, 255, 255, 220), radius * 0.36f)
        drawFourWayGlyph(graphics, centerX, centerY, radius, Color(57, 60, 66), radius * 0.17f)
    }

    private fun drawFourWayGlyph(
        graphics: Graphics2D,
        centerX: Float,
        centerY: Float,
        radius: Float,
        color: Color,
        strokeWidth: Float,
    ) {
        val inner = radius * 0.38f
        val arrow = radius * 0.34f
        graphics.color = color
        graphics.stroke = BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        graphics.drawLine(centerX.toInt(), (centerY - inner).toInt(), centerX.toInt(), (centerY - radius).toInt())
        graphics.drawLine(centerX.toInt(), (centerY + inner).toInt(), centerX.toInt(), (centerY + radius).toInt())
        graphics.drawLine((centerX - inner).toInt(), centerY.toInt(), (centerX - radius).toInt(), centerY.toInt())
        graphics.drawLine((centerX + inner).toInt(), centerY.toInt(), (centerX + radius).toInt(), centerY.toInt())
        drawArrowHead(graphics, centerX, centerY - radius, -90f, arrow)
        drawArrowHead(graphics, centerX, centerY + radius, 90f, arrow)
        drawArrowHead(graphics, centerX - radius, centerY, 180f, arrow)
        drawArrowHead(graphics, centerX + radius, centerY, 0f, arrow)
    }

    private fun drawArrowHead(graphics: Graphics2D, x: Float, y: Float, degrees: Float, size: Float) {
        val radians = Math.toRadians(degrees.toDouble())
        val left = radians + Math.toRadians(135.0)
        val right = radians - Math.toRadians(135.0)
        graphics.drawLine(x.toInt(), y.toInt(), (x + kotlin.math.cos(left).toFloat() * size).toInt(), (y + kotlin.math.sin(left).toFloat() * size).toInt())
        graphics.drawLine(x.toInt(), y.toInt(), (x + kotlin.math.cos(right).toFloat() * size).toInt(), (y + kotlin.math.sin(right).toFloat() * size).toInt())
    }

    private const val CURSOR_CANVAS_SIZE = 20
}
