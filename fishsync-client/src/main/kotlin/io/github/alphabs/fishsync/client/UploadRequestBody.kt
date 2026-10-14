package io.github.alphabs.fishsync.client

import io.github.alphabs.fishsync.core.SyncProgress
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File

internal class UploadRequestBody(
    private val file: File,
    private val path: String,
    private val mediaType: String,
    private val onProgress: (SyncProgress) -> Unit,
) : RequestBody() {
    override fun isOneShot() = true

    override fun contentType() = mediaType.toMediaType()

    override fun contentLength() = file.length()

    override fun writeTo(sink: BufferedSink) {
        file.inputStream().use { input ->
            var sent = 0L
            val buffer = ByteArray(65536)

            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead < 0) {
                    break
                }

                sink.write(buffer, 0, bytesRead)
                sent += bytesRead
                onProgress(SyncProgress(path, "upload", sent, file.length()))
            }
        }
    }
}
