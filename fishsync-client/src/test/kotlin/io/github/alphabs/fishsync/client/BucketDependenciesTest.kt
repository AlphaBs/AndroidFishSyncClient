package io.github.alphabs.fishsync.client

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BucketDependenciesTest {
    @Test
    fun rootOverridesDependencyAndUsesLatestVersionWithoutLogin() =
        runBlocking<Unit> {
            MockWebServer().use { server ->
                server.enqueue(response("root", "2026-10-01T00:00:00Z", "root", "base"))
                server.enqueue(response("base", "2026-10-02T00:00:00Z", "base"))
                val client =
                    FishSyncClient(
                        server
                            .url("/api")
                            .toString(),
                    )

                val result = client.filesRecursively("root")

                assertEquals("root", result.id)
                assertEquals("2026-10-02T00:00Z", result.lastUpdated)
                assertEquals(
                    "https://example.com/root",
                    result.files
                        .single()
                        .location,
                )
                assertEquals(listOf("root", "base"), result.dependencies)
                val rootRequest = server.takeRequest()
                assertEquals("/api/buckets/common/root/files", rootRequest.path)
                assertNull(rootRequest.getHeader("Authorization"))
                assertEquals(
                    "/api/buckets/common/base/files",
                    server
                        .takeRequest()
                        .path,
                )
            }
        }

    @Test
    fun repeatedAndCyclicDependenciesAreFetchedOnlyOnce() =
        runBlocking<Unit> {
            MockWebServer().use { server ->
                server.enqueue(response("root", "2026-10-01T00:00:00Z", "root", "base", "base"))
                server.enqueue(response("base", "2026-10-01T00:00:00Z", "base", "root"))
                val client =
                    FishSyncClient(
                        server
                            .url("/api")
                            .toString(),
                    )

                val result = client.filesRecursively("root")

                assertEquals(2, server.requestCount)
                assertEquals(listOf("root", "base"), result.dependencies)
            }
        }

    @Test
    fun dependencyLimitRejectsBeforeExcessRequest() =
        runBlocking<Unit> {
            MockWebServer().use { server ->
                server.enqueue(response("root", "2026-10-01T00:00:00Z", "root", "base"))
                val client =
                    FishSyncClient(
                        server
                            .url("/api")
                            .toString(),
                    )

                assertFailsWith<IllegalArgumentException> {
                    client.filesRecursively("root", maxBuckets = 1)
                }
                assertEquals(1, server.requestCount)
            }
        }

    private fun response(
        id: String,
        updated: String,
        location: String,
        vararg dependencies: String,
    ): MockResponse {
        val dependencyJson = dependencies.joinToString(",") { "\"$it\"" }
        return MockResponse().setBody(
            """
            {
                "id":"$id",
                "lastUpdated":"$updated",
                "dependencies":[$dependencyJson],
                "files":[{
                    "path":"shared.txt",
                    "location":"https://example.com/$location",
                    "metadata":{"size":3,"checksum":"202cb962ac59075b964b07152d234b70"}
                }]
            }
            """.trimIndent(),
        )
    }
}
