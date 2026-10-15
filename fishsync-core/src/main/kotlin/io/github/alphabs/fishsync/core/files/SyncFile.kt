package io.github.alphabs.fishsync.core.files

import io.github.alphabs.fishsync.core.RootedPath
import io.github.alphabs.fishsync.core.progress.ByteProgress
import java.io.InputStream
import java.io.OutputStream

abstract class SyncFile(
    val path: RootedPath,
) {
    var metadata: SyncFileMetadata? = null

    abstract val isReadable: Boolean
    abstract val isWritable: Boolean

    abstract suspend fun openReadStream(): InputStream

    abstract suspend fun openWriteStream(): OutputStream

    abstract suspend fun copyTo(
        destination: OutputStream,
        progress: ((ByteProgress) -> Unit)? = null,
    )

    override fun toString(): String = path.toString()

    override fun equals(other: Any?): Boolean =
        other is SyncFile && path == other.path && metadata == other.metadata

    override fun hashCode(): Int = path.hashCode() xor (metadata?.hashCode() ?: 0)
}
