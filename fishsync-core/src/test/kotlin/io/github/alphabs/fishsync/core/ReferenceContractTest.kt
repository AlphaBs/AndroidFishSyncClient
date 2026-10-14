package io.github.alphabs.fishsync.core

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class ReferenceContractTest {
    @Test fun dotnetComparisonFixtures() = runBlocking<Unit> {
        val corpus = javaClass.getResourceAsStream("/comparison-contract.json")!!.bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonArray }
        for (element in corpus) {
            val fixture = element.jsonObject
            val dir = Files.createTempDirectory("contract-").toFile()
            try {
                fixture.getValue("targets").jsonArray.forEach { t -> val file = File(dir, t.jsonObject.getValue("path").jsonPrimitive.content); file.parentFile.mkdirs(); file.writeText(t.jsonObject.getValue("text").jsonPrimitive.content) }
                val sources = fixture.getValue("sources").jsonArray.map { s ->
                    val obj = s.jsonObject; val text = obj.getValue("text").jsonPrimitive.content
                    val checksum = if (obj.getValue("checksum").jsonPrimitive.boolean) Checksum("md5", hash(text.byteInputStream())) else null
                    SourceFile(obj.getValue("path").jsonPrimitive.content, text.toByteArray().size.toLong(), checksum) { text.byteInputStream() }
                }
                val rules = fixture.getValue("rules").jsonArray.map { r -> val obj = r.jsonObject; SyncRule(SyncAction.valueOf(obj.getValue("action").jsonPrimitive.content), obj.getValue("pattern").jsonPrimitive.content, SyncCondition.valueOf(obj.getValue("condition").jsonPrimitive.content)) }
                val context = fixture.getValue("context").jsonObject
                val result = SyncEngine().compare(sources, FileSyncStorage(dir), rules, SyncContext(context["isNewVersion"]?.jsonPrimitive?.boolean ?: false, context["isForced"]?.jsonPrimitive?.boolean ?: false))
                val expected = fixture.getValue("expected").jsonObject
                for ((name, actual) in mapOf("added" to result.added, "updated" to result.updated, "deleted" to result.deleted, "identical" to result.identical)) {
                    assertEquals(expected.getValue(name).jsonArray.map { it.jsonPrimitive.content }, actual, "${fixture.getValue("name")}: $name")
                }
            } finally { dir.deleteRecursively() }
        }
    }
}
