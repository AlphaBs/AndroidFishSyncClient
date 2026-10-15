package io.github.alphabs.fishsync.core

import io.github.alphabs.fishsync.core.versions.VersionManager
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class GeneratedVersionContractTest {
    @Test
    fun versionTrimmingMatchesDotnetWhitespaceRules() =
        runBlocking<Unit> {
            val input =
                requireNotNull(javaClass.getResourceAsStream("/generated-version-contract.json"))
            val fixtures =
                input.bufferedReader().use {
                    Json
                        .parseToJsonElement(
                            it.readText(),
                        ).jsonArray
                }
            val directory =
                Files
                    .createTempDirectory("version-trim-")
                    .toFile()
            try {
                val file = File(directory, "version")
                val manager = VersionManager(file.path)
                for (element in fixtures) {
                    val row = element.jsonObject
                    val value =
                        row
                            .getValue("input")
                            .jsonPrimitive.content
                    val expected =
                        row
                            .getValue("expected")
                            .jsonPrimitive.content
                    file.writeText(value, Charsets.UTF_8)
                    assertEquals(expected, manager.getCurrentVersion())
                    assertFalse(manager.checkNewVersion(value))
                    manager.updateVersion(value)
                    assertEquals(expected, file.readText(Charsets.UTF_8))
                }
            } finally {
                directory.deleteRecursively()
            }
        }
}
