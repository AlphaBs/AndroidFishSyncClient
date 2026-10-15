package io.github.alphabs.fishsync.core.storage

import io.github.alphabs.fishsync.core.files.SyncFile
import io.github.alphabs.fishsync.core.storage.relativePath
import java.io.InputStream

data class SourceFile(
    val path: String,
    val size: Long?,
    val checksum: Checksum?,
    val open: suspend () -> InputStream,
) {
    init {
        require(path == relativePath(path))
        require(size == null || size >= 0)
    }

    fun toSyncFile(): SyncFile = StorageSourceFile(this)
}
