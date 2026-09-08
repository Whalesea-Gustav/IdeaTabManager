package com.whalesea.ideatabmanager

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class PluginMenuRegistrationTest {
    @Test
    fun `project tree menus are registered for IntelliJ Platform IDEs and Rider`() {
        val pluginXml = read("src/main/resources/META-INF/plugin.xml")
        val riderXml = read("src/main/resources/META-INF/rider.xml")

        assertContains(pluginXml, """<depends>com.intellij.modules.platform</depends>""")
        assertContains(pluginXml, """<depends>com.intellij.modules.lang</depends>""")
        assertContains(pluginXml, """config-file="rider.xml">com.intellij.modules.rider</depends>""")
        assertContains(pluginXml, """group-id="ProjectViewPopupMenu"""")
        assertContains(pluginXml, """group-id="NavbarPopupMenu"""")
        assertContains(pluginXml, """relative-to-action="VersionControlsGroup"""")
        assertContains(riderXml, """group-id="SolutionExplorerPopupMenu"""")
        assertTrue(pluginXml.contains("NonRiderAddSelectedProjectFilesToGroupActionGroup"))
        assertContains(pluginXml, """icon="/icons/tabGroupsAction.svg"""")
        assertContains(riderXml, """icon="/icons/tabGroupsAction.svg"""")
    }

    @Test
    fun `project tree menu icon is 16px so it lines up with other popup actions`() {
        val svg = read("src/main/resources/icons/tabGroupsAction.svg")
        assertContains(svg, """width="16"""")
        assertContains(svg, """height="16"""")
        assertTrue(!svg.contains("""width="20""""))
    }

    private fun read(path: String): String =
        Files.readString(Path.of(path), StandardCharsets.UTF_8)
}
