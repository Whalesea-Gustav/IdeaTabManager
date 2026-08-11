package com.whalesea.ideatabmanager

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals

class IdeaTabManagerBundleTest {
    @Test
    fun `simplified Chinese bundle matches the default bundle keys and message parameters`() {
        val english = loadBundle("IdeaTabManagerBundle.properties")
        val simplifiedChinese = loadBundle("IdeaTabManagerBundle_zh_CN.properties")

        assertEquals(english.stringPropertyNames(), simplifiedChinese.stringPropertyNames())
        english.stringPropertyNames().sorted().forEach { key ->
            assertEquals(
                messageParameters(english.getProperty(key)),
                messageParameters(simplifiedChinese.getProperty(key)),
                "Message parameters differ for '$key'.",
            )
        }
    }

    private fun loadBundle(fileName: String): Properties = Properties().apply {
        Files.newBufferedReader(Path.of("src", "main", "resources", "messages", fileName), StandardCharsets.UTF_8).use(::load)
    }

    private fun messageParameters(message: String): Set<String> = MESSAGE_PARAMETER.findAll(message)
        .map { it.groupValues[1] }
        .toSet()

    private companion object {
        val MESSAGE_PARAMETER = Regex("\\{(\\d+)(?:,[^}]*)?}")
    }
}
