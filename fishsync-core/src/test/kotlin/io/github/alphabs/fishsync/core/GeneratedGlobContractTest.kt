package io.github.alphabs.fishsync.core

import io.github.alphabs.fishsync.core.syncer.glob
import kotlinx.serialization.json.*
import kotlin.test.*

class GeneratedGlobContractTest {
    @Test
    fun matchesPinnedDotnetGlobResults() {
        val input =
            requireNotNull(javaClass.getResourceAsStream("/generated-glob-contract.json")) {
                "Generate original glob contracts in CI before running tests"
            }
        val cases =
            input
                .bufferedReader()
                .use {
                    Json
                        .parseToJsonElement(it.readText())
                        .jsonArray
                }
        val mismatches = mutableListOf<String>()
        for (element in cases) {
            val fixture = element.jsonObject
            val pattern =
                fixture
                    .getValue("pattern")
                    .jsonPrimitive.content
            val subject =
                fixture
                    .getValue("subject")
                    .jsonPrimitive.content
            val insensitive =
                fixture
                    .getValue("insensitive")
                    .jsonPrimitive.boolean
            val failed =
                fixture
                    .getValue("failed")
                    .jsonPrimitive.boolean
            val actual = runCatching { glob(pattern, insensitive).matches(subject) }
            val expected =
                fixture["matched"]
                    ?.jsonPrimitive
                    ?.booleanOrNull
            if (actual.isFailure != failed || (!failed && actual.getOrNull() != expected)) {
                mismatches.add(
                    "pattern=$pattern subject=$subject insensitive=$insensitive " +
                        "expected=$expected failed=$failed actual=$actual",
                )
            }
        }
        assertTrue(
            mismatches.isEmpty(),
            mismatches
                .take(30)
                .joinToString("\n"),
        )
    }
}
