package com.whalesea.ideatabmanager.toolwindow

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class SingleChoiceDialogTest {
    @Test
    fun `group choices are built from a string list not the raw string array`() {
        val source = Files.readString(
            Path.of("src/main/kotlin/com/whalesea/ideatabmanager/toolwindow/SingleChoiceDialog.kt"),
            StandardCharsets.UTF_8,
        )
        assertContains(source, "JBList(options.toList())")
        assertFalse("JBList(options)" in source)
    }
}
