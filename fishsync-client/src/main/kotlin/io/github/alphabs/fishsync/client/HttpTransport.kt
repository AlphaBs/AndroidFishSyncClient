package io.github.alphabs.fishsync.client

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.resumeWithException

internal class HttpTransport(
    private val http: OkHttpClient,
) {
    private val uploadHttp =
        http
            .newBuilder()
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

    suspend fun requestText(
        request: Request,
        upload: Boolean = false,
    ): String =
        coroutineScope {
            val (call, response) = awaitHeaders(request, upload)
            val watcher =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        awaitCancellation()
                    } finally {
                        call.cancel()
                    }
                }

            try {
                withContext(Dispatchers.IO) {
                    response.use { readResponse(it) }
                }
            } finally {
                watcher.cancel()
            }
        }

    suspend fun openDownload(location: String?): InputStream {
        // 다운로드 URL에는 API 인증 토큰을 자동으로 붙이지 않는다.
        val url = location?.toHttpUrl() ?: error("Missing download URL")
        val request =
            Request
                .Builder()
                .url(url)
                .build()
        val (call, response) = awaitHeaders(request)

        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw ApiException(code)
        }

        val stream =
            response.body
                ?.byteStream()
        if (stream == null) {
            response.close()
            error("Empty download")
        }

        val watcher =
            CoroutineScope(currentCoroutineContext())
                .launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        awaitCancellation()
                    } finally {
                        call.cancel()
                    }
                }

        return object : FilterInputStream(stream) {
            override fun close() {
                try {
                    response.close()
                } finally {
                    watcher.cancel()
                }
            }
        }
    }

    private suspend fun awaitHeaders(
        request: Request,
        upload: Boolean = false,
    ): Pair<Call, Response> {
        val client = if (upload) uploadHttp else http
        val call = client.newCall(request)
        val response =
            suspendCancellableCoroutine<Response> { continuation ->
                continuation.invokeOnCancellation { call.cancel() }

                call.enqueue(
                    object : Callback {
                        override fun onFailure(
                            call: Call,
                            exception: IOException,
                        ) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(exception)
                            }
                        }

                        override fun onResponse(
                            call: Call,
                            response: Response,
                        ) {
                            if (!continuation.isActive) {
                                response.close()
                                return
                            }

                            continuation.resume(response) { _, value, _ -> value.close() }
                        }
                    },
                )
            }

        return call to response
    }

    private suspend fun readResponse(response: Response): String {
        if (!response.isSuccessful) {
            throw ApiException(response.code)
        }

        val body = response.body ?: error("Empty HTTP response")
        require(body.contentLength() <= MAX_RESPONSE_BYTES) { "Response too large" }

        return body.byteStream().use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)

            while (true) {
                currentCoroutineContext().ensureActive()
                val bytesRead = input.read(buffer)
                if (bytesRead < 0) {
                    break
                }

                require(output.size() + bytesRead <= MAX_RESPONSE_BYTES)
                output.write(buffer, 0, bytesRead)
            }

            output.toString("UTF-8")
        }
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 10 * 1024 * 1024
    }
}
