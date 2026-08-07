package com.whalesea.ideatabmanager.tortoise

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** TortoiseSVN's exact, validated multi-path transport format. */
internal object TortoisePathFile {
    private val utf16LeLf = byteArrayOf('\n'.code.toByte(), 0)

    fun create(expectedPaths: List<Path>, temporaryDirectory: Path? = null): Path {
        require(expectedPaths.isNotEmpty()) { "A TortoiseSVN path file cannot be empty." }
        val pathFile = temporaryDirectory?.let { Files.createTempFile(it, "idea-tab-groups-", ".paths") }
            ?: Files.createTempFile("idea-tab-groups-", ".paths")
        try {
            Files.write(pathFile, encode(expectedPaths))
            validate(pathFile, expectedPaths)
            return pathFile
        } catch (exception: Exception) {
            runCatching { Files.deleteIfExists(pathFile) }
            throw exception
        }
    }

    fun validate(pathFile: Path, expectedPaths: List<Path>) {
        val bytes = Files.readAllBytes(pathFile)
        if (bytes.size % 2 != 0) invalid("contains an incomplete UTF-16LE code unit")
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) invalid("contains a byte-order mark")

        val content = String(bytes, StandardCharsets.UTF_16LE)
        if ('\r' in content) invalid("contains a carriage return; TortoiseSVN requires LF-only lines")
        val lines = content.split('\n')
        if (lines.size != expectedPaths.size + 1 || lines.last().isNotEmpty()) {
            invalid("does not contain exactly one LF-terminated line per selected path")
        }
        expectedPaths.forEachIndexed { index, expected ->
            val actual = lines[index]
            if (actual != expected.toString()) invalid("changed selected path at line ${index + 1}")
            val decoded = runCatching { Path.of(actual).toAbsolutePath().normalize() }
                .getOrElse { throw IOException("Invalid TortoiseSVN path-file line ${index + 1}", it) }
            if (decoded != expected) invalid("does not round-trip selected path at line ${index + 1}")
        }
    }

    private fun encode(paths: List<Path>): ByteArray = ByteArrayOutputStream().use { output ->
        paths.forEach { path ->
            output.write(path.toString().toByteArray(StandardCharsets.UTF_16LE))
            output.write(utf16LeLf)
        }
        output.toByteArray()
    }

    private fun invalid(reason: String): Nothing = throw IOException("Invalid TortoiseSVN path-file format: $reason")
}
