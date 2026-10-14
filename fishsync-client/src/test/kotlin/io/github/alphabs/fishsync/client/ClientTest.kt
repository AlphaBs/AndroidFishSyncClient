package io.github.alphabs.fishsync.client

import io.github.alphabs.fishsync.core.*
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import kotlin.test.*

class ClientTest {
    private val md5 = "202cb962ac59075b964b07152d234b70"
    @Test fun loginUsesApiPrefixAndReturnsToken() = runBlocking<Unit> {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"token\":\"session\"}"))
            val api = FishSyncClient(server.url("/api").toString())
            assertEquals("session", api.login("name", "secret"))
            val request = server.takeRequest(); assertEquals("/api/auth/login", request.path); assertEquals("POST", request.method)
            assertTrue(request.body.readUtf8().contains("\"username\":\"name\""))
        }
    }
    @Test fun pullDownloadsWithoutLeakingApiToken() = runBlocking<Unit> {
        MockWebServer().use { apiServer -> MockWebServer().use { downloads ->
            val dir = Files.createTempDirectory("pull-test-").toFile()
            try {
                apiServer.enqueue(MockResponse().setBody("{\"files\":[{\"path\":\"file.txt\",\"location\":\"${downloads.url("/file?signature=a%2Fb")}\",\"metadata\":{\"size\":3,\"checksum\":\"$md5\"}}]}"))
                downloads.enqueue(MockResponse().setBody("123"))
                val api = FishSyncClient(apiServer.url("/api").toString(), { "private-token" })
                api.pull("bucket", FileSyncStorage(dir))
                assertEquals("123", File(dir, "file.txt").readText())
                assertEquals("Bearer private-token", apiServer.takeRequest().getHeader("Authorization"))
                val transfer = downloads.takeRequest(); assertNull(transfer.getHeader("Authorization")); assertEquals("/file?signature=a%2Fb", transfer.path)
            } finally { dir.deleteRecursively() }
        } }
    }
    @Test fun uploadContractAndCommitRound() = runBlocking<Unit> {
        MockWebServer().use { server ->
            val dir = Files.createTempDirectory("push-test-").toFile(); val scratch = Files.createTempDirectory("scratch-").toFile()
            try {
                File(dir, "file").writeText("123")
                server.enqueue(MockResponse().setBody("{\"isSuccess\":false,\"requiredActions\":[{\"path\":\"file\",\"action\":{\"type\":\"Http\",\"parameters\":{\"method\":\"PUT\",\"url\":\"${server.url("/upload")}\",\"headers.Content-MD5\":\"ICy5YqyQdZBLBxUtI0tncA==\"}}}]}"))
                server.enqueue(MockResponse().setResponseCode(204))
                server.enqueue(MockResponse().setBody("{\"isSuccess\":true,\"requiredActions\":[],\"updatedAt\":\"2026-10-15T00:00:00Z\"}"))
                assertTrue(FishSyncClient(server.url("/api").toString(), { "token" }).push("bucket", FileSyncStorage(dir), scratch).isSuccess)
                val sync = server.takeRequest(); assertEquals("/api/buckets/common/bucket/sync", sync.path); assertTrue(sync.body.readUtf8().contains(md5))
                val upload = server.takeRequest(); assertEquals("PUT", upload.method); assertEquals("123", upload.body.readUtf8()); assertNull(upload.getHeader("Authorization")); assertEquals("ICy5YqyQdZBLBxUtI0tncA==", upload.getHeader("Content-MD5"))
                assertEquals("/api/buckets/common/bucket/sync", server.takeRequest().path); assertTrue(scratch.listFiles()!!.isEmpty())
            } finally { dir.deleteRecursively(); scratch.deleteRecursively() }
        }
    }
    @Test fun rejectsLowercaseHttpAndCleansCopies() = runBlocking<Unit> {
        MockWebServer().use { server ->
            val dir = Files.createTempDirectory("push-test-").toFile(); val scratch = Files.createTempDirectory("scratch-").toFile()
            try {
                File(dir, "file").writeText("123")
                server.enqueue(MockResponse().setBody("{\"isSuccess\":false,\"requiredActions\":[{\"path\":\"file\",\"action\":{\"type\":\"http\",\"parameters\":{}}}]}"))
                assertFailsWith<PushFailure> { FishSyncClient(server.url("/api").toString()).push("bucket", FileSyncStorage(dir), scratch) }
                assertEquals(1, server.requestCount); assertTrue(scratch.listFiles()!!.isEmpty())
            } finally { dir.deleteRecursively(); scratch.deleteRecursively() }
        }
    }
    @Test fun uploadRedirectIsNotFollowed() = runBlocking<Unit> {
        MockWebServer().use { server ->
            val dir = Files.createTempDirectory("push-test-").toFile(); val scratch = Files.createTempDirectory("scratch-").toFile()
            try {
                File(dir, "file").writeText("123")
                server.enqueue(MockResponse().setBody("{\"isSuccess\":false,\"requiredActions\":[{\"path\":\"file\",\"action\":{\"type\":\"Http\",\"parameters\":{\"method\":\"POST\",\"url\":\"${server.url("/upload")}\"}}}]}"))
                server.enqueue(MockResponse().setResponseCode(307).addHeader("Location", server.url("/unexpected")))
                assertFailsWith<PushFailure> { FishSyncClient(server.url("/api").toString()).push("bucket", FileSyncStorage(dir), scratch) }
                assertEquals(2, server.requestCount)
            } finally { dir.deleteRecursively(); scratch.deleteRecursively() }
        }
    }
    @Test fun changedUploadSnapshotIsRejectedBeforeServerMutation() = runBlocking<Unit> {
        MockWebServer().use { server ->
            val dir = Files.createTempDirectory("push-test-").toFile(); val scratch = Files.createTempDirectory("scratch-").toFile()
            try {
                val file = File(dir, "file").apply { writeText("123") }
                val remote = "{\"files\":[{\"path\":\"file\",\"metadata\":{\"size\":3,\"checksum\":\"${"0".repeat(32)}\"}}]}"
                server.enqueue(MockResponse().setBody(remote)); server.enqueue(MockResponse().setBody(remote))
                val api = FishSyncClient(server.url("/api").toString())
                val storage = FileSyncStorage(dir)
                val preview = api.comparePush("bucket", storage)
                file.writeText("456")
                assertFailsWith<PushFailure> { api.push("bucket", storage, scratch, expectedPlan = preview) }
                assertEquals(2, server.requestCount)
                assertEquals("GET", server.takeRequest().method); assertEquals("GET", server.takeRequest().method)
                assertTrue(scratch.listFiles()!!.isEmpty())
            } finally { dir.deleteRecursively(); scratch.deleteRecursively() }
        }
    }
    @Test fun malformedRemoteMetadataRejectedBeforeStorageAccess() = runBlocking<Unit> {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"files\":[{\"path\":\"../escape\",\"metadata\":{\"size\":3,\"checksum\":\"$md5\"}}]}"))
            assertFails { FishSyncClient(server.url("/api").toString()).files("bucket") }
        }
    }
    @Test fun requestCancellationDoesNotWaitForTimeout() = runBlocking<Unit> {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            assertFailsWith<TimeoutCancellationException> { withTimeout(250) { FishSyncClient(server.url("/api").toString()).listBuckets() } }
        }
    }
}
