package io.github.alphabs.fishsync.core.files

import io.github.alphabs.fishsync.core.RootedPath
import io.github.alphabs.fishsync.core.progress.ByteProgress
import java.io.InputStream
import java.io.OutputStream

class VirtualSyncFile(
    path: RootedPath,
) : SyncFile(path) {
    override val isReadable = false
    override val isWritable = false

    override suspend fun openReadStream(): InputStream = throw UnsupportedOperationException()

    override suspend fun openWriteStream(): OutputStream = throw UnsupportedOperationException()

    override suspend fun copyTo(
        destination: OutputStream,
        progress: ((ByteProgress) -> Unit)?,
    ): Unit = throw UnsupportedOperationException()
}
