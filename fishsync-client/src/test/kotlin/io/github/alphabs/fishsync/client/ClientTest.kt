package io.github.alphabs.fishsync.client

import io.github.alphabs.fishsync.core.storage.FileSyncStorage
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClientTest {
    private val md5 = "202cb962ac59075b964b07152d234b70"

    @Test
    fun loginUsesApiPrefixAndReturnsToken() =
        runBlocking<Unit> {
            MockWebServer().use { server ->
                server.enqueue(jsonResponse("""{"token":"session"}"""))
                val api = FishSyncClient(apiUrl(server))

                assertEquals("session", api.login("name", "secret"))

                val request = server.takeRequest()
                assertEquals("/api/auth/login", request.path)
                assertEquals("POST", request.method)
                assertTrue(
                    request.body
                        .readUtf8()
                        .contains("\"username\":\"name\""),
                )
            }
        }

    @Test
    fun pullDownloadsWithoutLeakingApiToken() =
        runBlocking<Unit> {
            MockWebServer().use { server ->
                MockWebServer().use { downloads ->
                    val directory = temporaryDirectory("pull-test-")

                    try {
                        val downloadUrl =
                            downloads
                                .url("/file?signature=a%2Fb")
                                .toString()
                        server.enqueue(fileResponse("file.txt", location = downloadUrl))
                        downloads.enqueue(MockResponse().setBody("123"))
                        val api = FishSyncClient(apiUrl(server), { "private-token" })

                        api.pull("bucket", FileSyncStorage(directory))

                        assertEquals("123", File(directory, "file.txt").readText())
                        assertEquals(
                            "Bearer private-token",
                            server
                                .takeRequest()
                                .getHeader("Authorization"),
                        )
                        val transfer = downloads.takeRequest()
                        assertNull(transfer.getHeader("Authorization"))
                        assertEquals("/file?signature=a%2Fb", transfer.path)
                    } finally {
                        directory.deleteRecursively()
                    }
                }
            }
        }

    @Test
    fun uploadContractAndCommitRound() =
        runBlocking<Unit> {
            MockWebServer().use { server ->
                val directory = temporaryDirectory("push-test-")
                val scratch = temporaryDirectory("scratch-")
                val contentMd5 = "ICy5YqyQdZBLBxUtI0tncA=="

                try {
                    File(directory, "file").writeText("123")
                    server.enqueue(
                        actionResponse(
                            url =
                                server
                                    .url("/upload")
                                    .toString(),
                            headers = mapOf("Content-MD5" to contentMd5),
                        ),
                    )
                    server.enqueue(MockResponse().setResponseCode(204))
                    server.enqueue(
                        jsonResponse(
                            """
                            {
                                "isSuccess": true,
                                "requiredActions": [],
                                "updatedAt": "2026-10-15T00:00:00Z"
                            }
                            """.trimIndent(),
                        ),
                    )
                    val api = FishSyncClient(apiUrl(server), { "token" })

                    val result = api.push("bucket", FileSyncStorage(directory), scratch)

                    assertTrue(result.isSuccess)
                    val sync = server.takeRequest()
                    assertEquals("/api/buckets/common/bucket/sync", sync.path)
                    assertTrue(
                        sync.body
                            .readUtf8()
                            .contains(md5),
                    )

                    val upload = server.takeRequest()
                    assertEquals("PUT", upload.method)
                    assertEquals(
                        "123",
                        upload.body
                            .readUtf8(),
                    )
                    assertNull(upload.getHeader("Authorization"))
                    assertEquals(contentMd5, upload.getHeader("Content-MD5"))
                    assertEquals(
                        "/api/buckets/common/bucket/sync",
                        server
                            .takeRequest()
                            .path,
                    )
                    assertTrue(
                        scratch
                            .listFiles()!!
                            .isEmpty(),
                    )
                } finally {
                    directory.deleteRecursively()
                    scratch.deleteRecursively()
                }
            }
        }

    @Test
    fun rejectsLowercaseHttpAndCleansCopies() =
        runBlocking<Unit> {
            MockWebServer().use { server ->
                val directory = temporaryDirectory("push-test-")
                val scratch = temporaryDirectory("scratch-")

                try {
                    File(directory, "file").writeText("123")
                    server.enqueue(
                        actionResponse(
                            server
                                .url("/upload")
                                .toString(),
                            type = "http",
                        ),
                    )
                    val api = FishSyncClient(apiUrl(server))

                    assertFailsWith<PushFailure> {
                        api.push("bucket", FileSyncStorage(directory), scratch)
                    }

                    assertEquals(1, server.requestCount)
                    assertTrue(
                        scratch
                            .listFiles()!!
                            .isEmpty(),
                    )
                } finally {
                    directory.deleteRecursively()
                    scratch.deleteRecursively()
                }
            }
        }

    @Test
    fun uploadRedirectIsNotFollowed() =
        runBlocking<Unit> {
            MockWebServer().use { server ->
                val directory = temporaryDirectory("push-test-")
                val scratch = temporaryDirectory("scratch-")

                try {
                    File(directory, "file").writeText("123")
                    server.enqueue(
                        actionResponse(
                            server
                                .url("/upload")
                                .toString(),
                            method = "POST",
                        ),
                    )
                    server.enqueue(
                        MockResponse()
                            .setResponseCode(307)
                            .addHeader("Location", server.url("/unexpected")),
                    )
                    val api = FishSyncClient(apiUrl(server))

                    assertFailsWith<PushFailure> {
                        api.push("bucket", FileSyncStorage(directory), scratch)
                    }

                    assertEquals(2, server.requestCount)
                } finally {
                    directory.deleteRecursively()
                    scratch.deleteRecursively()
                }
            }
        }

    @Test
    fun changedUploadSnapshotIsRejectedBeforeServerMutation() =
        runBlocking<Unit> {
            MockWebServer().use { server ->
                val directory = temporaryDirectory("push-test-")
                val scratch = temporaryDirectory("scratch-")

                try {
                    val file = File(directory, "file").apply { writeText("123") }
                    server.enqueue(fileResponse("file", checksum = "0".repeat(32)))
                    server.enqueue(fileResponse("file", checksum = "0".repeat(32)))
                    val api = FishSyncClient(apiUrl(server))
                    val storage = FileSyncStorage(directory)
                    val preview = api.comparePush("bucket", storage)

                    file.writeText("456")
                    assertFailsWith<PushFailure> {
                        api.push("bucket", storage, scratch, expectedPlan = preview)
                    }

                    assertEquals(2, server.requestCount)
                    assertEquals(
                        "GET",
                        server
                            .takeRequest()
                            .method,
                    )
                    assertEquals(
                        "GET",
                        server
                            .takeRequest()
                            .method,
                    )
                    assertTrue(
                        scratch
                            .listFiles()!!
                            .isEmpty(),
                    )
                } finally {
                    directory.deleteRecursively()
                    scratch.deleteRecursively()
                }
            }
        }

    @Test
    fun malformedRemoteMetadataRejectedBeforeStorageAccess() =
        runBlocking<Unit> {
            MockWebServer().use { server ->
                server.enqueue(fileResponse("../escape"))
                val api = FishSyncClient(apiUrl(server))

                assertFails { api.files("bucket") }
            }
        }

    @Test
    fun remoteUnicodeDuplicatesUseStandardCaseComparison() =
        runBlocking<Unit> {
            MockWebServer().use { server ->
                val body =
                    buildJsonObject {
                        putJsonArray("files") {
                            for (name in listOf("I.txt", "ı.txt")) {
                                addJsonObject {
                                    put("path", name)
                                    putJsonObject("metadata") {
                                        put("size", 3)
                                        put("checksum", md5)
                                    }
                                }
                            }
                        }
                    }
                server.enqueue(jsonResponse(body.toString()))
                assertFailsWith<IllegalArgumentException> {
                    FishSyncClient(apiUrl(server)).files("bucket")
                }
            }
        }

    @Test
    fun pushComparisonUsesTheSameUnicodeCasePolicyAsPull() =
        runBlocking<Unit> {
            MockWebServer().use { server ->
                val directory = temporaryDirectory("unicode-push-")
                try {
                    File(directory, "I.txt").writeText("123")
                    server.enqueue(fileResponse("ı.txt"))
                    val plan =
                        FishSyncClient(
                            apiUrl(server),
                        ).comparePush("bucket", FileSyncStorage(directory))
                    assertEquals(listOf("I.txt"), plan.identical)
                    assertTrue(
                        plan.added
                            .isEmpty(),
                    )
                    assertTrue(
                        plan.updated
                            .isEmpty(),
                    )
                    assertTrue(
                        plan.deleted
                            .isEmpty(),
                    )
                } finally {
                    directory.deleteRecursively()
                }
            }
        }

    @Test
    fun requestCancellationDoesNotWaitForTimeout() =
        runBlocking<Unit> {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

                assertFailsWith<TimeoutCancellationException> {
                    withTimeout(250) {
                        FishSyncClient(apiUrl(server)).listBuckets()
                    }
                }
            }
        }

    private fun apiUrl(server: MockWebServer): String =
        server
            .url("/api")
            .toString()

    private fun temporaryDirectory(prefix: String): File =
        Files
            .createTempDirectory(prefix)
            .toFile()

    private fun jsonResponse(body: String): MockResponse = MockResponse().setBody(body)

    private fun fileResponse(
        path: String,
        location: String? = null,
        checksum: String = md5,
    ): MockResponse {
        val body =
            buildJsonObject {
                putJsonArray("files") {
                    addJsonObject {
                        put("path", path)
                        if (location != null) {
                            put("location", location)
                        }
                        putJsonObject("metadata") {
                            put("size", 3)
                            put("checksum", checksum)
                        }
                    }
                }
            }

        return jsonResponse(body.toString())
    }

    private fun actionResponse(
        url: String,
        method: String = "PUT",
        type: String = "Http",
        headers: Map<String, String> = emptyMap(),
    ): MockResponse {
        val body =
            buildJsonObject {
                put("isSuccess", false)
                putJsonArray("requiredActions") {
                    addJsonObject {
                        put("path", "file")
                        putJsonObject("action") {
                            put("type", type)
                            putJsonObject("parameters") {
                                put("method", method)
                                put("url", url)
                                for ((name, value) in headers) {
                                    put("headers.$name", value)
                                }
                            }
                        }
                    }
                }
            }

        return jsonResponse(body.toString())
    }
}
