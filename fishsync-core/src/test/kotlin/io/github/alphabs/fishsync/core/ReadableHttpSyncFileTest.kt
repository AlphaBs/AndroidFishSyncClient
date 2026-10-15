package io.github.alphabs.fishsync.core

import io.github.alphabs.fishsync.core.files.ReadableHttpSyncFile
import io.github.alphabs.fishsync.core.files.SyncFileMetadata
import io.github.alphabs.fishsync.core.progress.ByteProgress
import io.github.alphabs.fishsync.core.progress.ByteProgressAggregator
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.test.*

class ReadableHttpSyncFileTest {
    @Test
    fun contentLengthCorrectsRegisteredByteTotals() =
        runBlocking<Unit> {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setBody("123"))
                val file =
                    ReadableHttpSyncFile(
                        RootedPath.fromSubPath("file", PathOptions()),
                        OkHttpClient(),
                        SyncFileMetadata(100),
                    )
                file.location =
                    server
                        .url("/file")
                        .toUri()
                var total = ByteProgress()
                val aggregator = ByteProgressAggregator { total = it }
                aggregator.report(ByteProgress(100, 0))
                val output = ByteArrayOutputStream()
                file.copyTo(output, aggregator::report)
                assertEquals("123", output.toString("UTF-8"))
                assertEquals(ByteProgress(3, 3), total)
            }
        }

    @Test
    fun failedResponseReleasesBodyBeforeNextRequest() =
        runBlocking<Unit> {
            MockWebServer().use { server ->
                server.enqueue(
                    MockResponse()
                        .setResponseCode(404)
                        .setBody("missing"),
                )
                server.enqueue(MockResponse().setBody("available"))
                val file =
                    ReadableHttpSyncFile(
                        RootedPath.fromSubPath("file", PathOptions()),
                        OkHttpClient(),
                    )
                file.location =
                    server
                        .url("/file")
                        .toUri()
                assertFailsWith<IOException> { file.openReadStream() }
                assertEquals(
                    "available",
                    file
                        .openReadStream()
                        .bufferedReader()
                        .use { it.readText() },
                )
                assertEquals(2, server.requestCount)
            }
        }
}
