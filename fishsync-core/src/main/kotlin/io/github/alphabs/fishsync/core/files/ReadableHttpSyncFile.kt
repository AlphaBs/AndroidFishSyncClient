package io.github.alphabs.fishsync.core.files

import io.github.alphabs.fishsync.core.RootedPath
import io.github.alphabs.fishsync.core.internals.StreamProgressHelper
import io.github.alphabs.fishsync.core.progress.ByteProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import kotlin.coroutines.resumeWithException

class ReadableHttpSyncFile(
    path: RootedPath,
    private val httpClient: OkHttpClient,
) : SyncFile(path) {
    var uploaded: Instant = Instant.EPOCH
    var location: String? = null
    override val isReadable = true
    override val isWritable = false

    override suspend fun openReadStream(): InputStream {
        val request =
            Request
                .Builder()
                .url(requireNotNull(location))
                .build()
        val call = httpClient.newCall(request)
        val response =
            suspendCancellableCoroutine<Response> { continuation ->
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(
                    object : Callback {
                        override fun onFailure(
                            call: Call,
                            exception: IOException,
                        ) {
                            if (continuation.isActive) continuation.resumeWithException(exception)
                        }

                        override fun onResponse(
                            call: Call,
                            response: Response,
                        ) {
                            continuation.resume(response) { _, value, _ -> value.close() }
                        }
                    },
                )
            }
        try {
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val body = response.body ?: error("Empty HTTP body")
            val watcher =
                CoroutineScope(
                    currentCoroutineContext(),
                ).launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        awaitCancellation()
                    } finally {
                        call.cancel()
                    }
                }
            return ResponseStream(
                body.byteStream(),
                response,
                body
                    .contentLength()
                    .takeIf { it >= 0 },
                watcher,
            )
        } catch (failure: Throwable) {
            response.close()
            throw failure
        }
    }

    override suspend fun openWriteStream(): OutputStream = throw UnsupportedOperationException()

    override suspend fun copyTo(
        destination: OutputStream,
        progress: ((ByteProgress) -> Unit)?,
    ) {
        val registered = metadata?.size ?: 0
        openReadStream().use { source ->
            val size = (source as? ResponseStream)?.contentLength ?: registered
            progress?.invoke(ByteProgress(size - registered, 0))
            StreamProgressHelper.copyStreamWithProgressPerBuffer(
                source,
                destination,
                StreamProgressHelper.getBufferSize(size),
            ) {
                progress?.invoke(ByteProgress(0, it))
            }
        }
    }

    private class ResponseStream(
        input: InputStream,
        private val response: Response,
        val contentLength: Long?,
        private val watcher: Job,
    ) : FilterInputStream(input) {
        override fun close() {
            try {
                response.close()
            } finally {
                watcher.cancel()
            }
        }
    }
}
