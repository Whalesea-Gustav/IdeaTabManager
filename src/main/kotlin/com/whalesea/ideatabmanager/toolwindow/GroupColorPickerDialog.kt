package com.whalesea.ideatabmanager.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.whalesea.ideatabmanager.IdeaTabManagerBundle
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import javax.swing.event.ChangeListener
import javax.swing.event.DocumentEvent

/** RGB/hex color picker with a live preview and the original named swatches as shortcuts. */
class GroupColorPickerDialog(
    project: Project,
    initialColorId: String,
) : DialogWrapper(project) {
    var selectedColorId: String? = null
        private set

    private var namedColorId: String? = TabGroupColorPalette.normalizeColorId(initialColorId)
        ?.takeIf(TabGroupColorPalette::isNamedColor)
    private var updating = false

    private val preview = JPanel().apply {
        isOpaque = true
        preferredSize = JBUI.size(88, 48)
        minimumSize = preferredSize
        border = JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground())
    }
    private val redSpinner = colorSpinner()
    private val greenSpinner = colorSpinner()
    private val blueSpinner = colorSpinner()
    private val hexField = JBTextField().apply {
        columns = 8
    }

    init {
        title = IdeaTabManagerBundle.message("dialog.change-color.title")
        val (red, green, blue) = TabGroupColorPalette.rgbComponents(initialColorId)
        setRgb(red, green, blue, updateHex = true)
        hexField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) = syncFromHex()
        })
        init()
    }

    override fun createCenterPanel(): JComponent = JBPanel<JBPanel<*>>(BorderLayout(0, JBUI.scale(10))).apply {
        border = JBUI.Borders.empty(8)
        add(JBLabel(IdeaTabManagerBundle.message("dialog.change-color.prompt")), BorderLayout.NORTH)
        add(createEditor(), BorderLayout.CENTER)
    }

    override fun createActions(): Array<Action> = arrayOf(okAction, cancelAction)

    override fun doOKAction() {
        selectedColorId = namedColorId ?: TabGroupColorPalette.encodeHex(redValue(), greenValue(), blueValue())
        super.doOKAction()
    }

    private fun createEditor(): JComponent {
        val editor = JPanel(GridBagLayout())
        val constraints = GridBagConstraints().apply {
            anchor = GridBagConstraints.WEST
            insets = Insets(JBUI.scale(4), JBUI.scale(4), JBUI.scale(4), JBUI.scale(4))
        }

        fun addRow(row: Int, label: String, field: JComponent, fillField: Boolean = false) {
            constraints.gridx = 0
            constraints.gridy = row
            constraints.weightx = 0.0
            constraints.fill = GridBagConstraints.NONE
            editor.add(JBLabel(label), constraints)
            constraints.gridx = 1
            constraints.weightx = 1.0
            constraints.fill = if (fillField) GridBagConstraints.HORIZONTAL else GridBagConstraints.NONE
            editor.add(field, constraints)
        }

        addRow(0, IdeaTabManagerBundle.message("dialog.change-color.preview"), preview)
        addRow(1, IdeaTabManagerBundle.message("dialog.change-color.presets"), createPresetRow(), fillField = true)
        addRow(2, IdeaTabManagerBundle.message("dialog.change-color.red"), redSpinner)
        addRow(3, IdeaTabManagerBundle.message("dialog.change-color.green"), greenSpinner)
        addRow(4, IdeaTabManagerBundle.message("dialog.change-color.blue"), blueSpinner)
        addRow(5, IdeaTabManagerBundle.message("dialog.change-color.hex"), hexField, fillField = true)
        return editor
    }

    private fun createPresetRow(): JComponent = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
        isOpaque = false
        TabGroupColorPalette.colors.forEach { named ->
            add(JButton().apply {
                toolTipText = named.displayName
                background = named.color
                isOpaque = true
                isBorderPainted = true
                preferredSize = Dimension(JBUI.scale(22), JBUI.scale(22))
                minimumSize = preferredSize
                maximumSize = preferredSize
                addActionListener { applyNamedColor(named.id) }
            })
        }
    }

    private fun colorSpinner(): JSpinner = JSpinner(SpinnerNumberModel(0, 0, 255, 1)).apply {
        editor = JSpinner.NumberEditor(this, "0")
        preferredSize = JBUI.size(72, 28)
        addChangeListener(ChangeListener {
            if (!updating) {
                namedColorId = null
                syncFromRgb()
            }
        })
    }

    private fun applyNamedColor(colorId: String) {
        namedColorId = colorId
        val (red, green, blue) = TabGroupColorPalette.rgbComponents(colorId)
        setRgb(red, green, blue, updateHex = true)
    }

    private fun setRgb(red: Int, green: Int, blue: Int, updateHex: Boolean) {
        updating = true
        try {
            redSpinner.value = red
            greenSpinner.value = green
            blueSpinner.value = blue
            if (updateHex) hexField.text = TabGroupColorPalette.encodeHex(red, green, blue)
            preview.background = Color(red, green, blue)
            preview.repaint()
        } finally {
            updating = false
        }
    }

    private fun syncFromRgb() {
        val red = redValue()
        val green = greenValue()
        val blue = blueValue()
        setRgb(red, green, blue, updateHex = true)
    }

    private fun syncFromHex() {
        if (updating) return
        val normalized = TabGroupColorPalette.normalizeColorId(hexField.text) ?: return
        namedColorId = normalized.takeIf(TabGroupColorPalette::isNamedColor)
        val (red, green, blue) = TabGroupColorPalette.rgbComponents(normalized)
        setRgb(red, green, blue, updateHex = false)
    }

    private fun redValue(): Int = (redSpinner.value as Number).toInt()
    private fun greenValue(): Int = (greenSpinner.value as Number).toInt()
    private fun blueValue(): Int = (blueSpinner.value as Number).toInt()
}
