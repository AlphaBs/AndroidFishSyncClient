package io.github.alphabs.fishsync.core.storage

import io.github.alphabs.fishsync.core.PathOptions
import io.github.alphabs.fishsync.core.RootedPath
import io.github.alphabs.fishsync.core.files.SyncFile
import io.github.alphabs.fishsync.core.files.SyncFileChecksum
import io.github.alphabs.fishsync.core.files.SyncFileMetadata
import io.github.alphabs.fishsync.core.internals.StreamProgressHelper
import io.github.alphabs.fishsync.core.progress.ByteProgress
import java.io.InputStream
import java.io.OutputStream

internal class StorageSourceFile(
    private val source: SourceFile,
) : SyncFile(
        RootedPath.fromSubPath(source.path, PathOptions()),
        SyncFileMetadata(
            source.size ?: -1,
            source.checksum?.let {
                SyncFileChecksum(it.algorithm, it.hex)
            },
        ),
    ) {
    override val isReadable = true
    override val isWritable = false

    override suspend fun openReadStream(): InputStream = source.open()

    override suspend fun openWriteStream(): OutputStream = throw UnsupportedOperationException()

    override suspend fun copyTo(
        destination: OutputStream,
        progress: ((ByteProgress) -> Unit)?,
    ) {
        openReadStream().use { input ->
            StreamProgressHelper.copyStreamWithProgressPerBuffer(input, destination, 65536) {
                progress?.invoke(ByteProgress(0, it))
            }
        }
    }
}
