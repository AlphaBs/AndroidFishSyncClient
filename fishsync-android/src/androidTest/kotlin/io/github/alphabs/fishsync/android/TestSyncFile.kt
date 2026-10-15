package io.github.alphabs.fishsync.android

import io.github.alphabs.fishsync.core.PathOptions
import io.github.alphabs.fishsync.core.RootedPath
import io.github.alphabs.fishsync.core.files.SyncFile
import io.github.alphabs.fishsync.core.files.SyncFileChecksum
import io.github.alphabs.fishsync.core.files.SyncFileMetadata
import io.github.alphabs.fishsync.core.progress.ByteProgress
import java.io.InputStream
import java.io.OutputStream

fun testSyncFile(
    path: String,
    size: Long?,
    checksum: SyncFileChecksum?,
    open: suspend () -> InputStream,
): SyncFile =
    object : SyncFile(
        RootedPath.fromSubPath(path, PathOptions()),
        SyncFileMetadata(size ?: -1, checksum),
    ) {
        override val isReadable = true
        override val isWritable = false

        override suspend fun openReadStream(): InputStream = open()

        override suspend fun openWriteStream(): OutputStream = throw UnsupportedOperationException()

        override suspend fun copyTo(
            destination: OutputStream,
            progress: ((ByteProgress) -> Unit)?,
        ) {
            openReadStream().use { input ->
                val buffer = ByteArray(65536)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    destination.write(buffer, 0, count)
                    progress?.invoke(ByteProgress(0, count.toLong()))
                }
            }
        }
    }
