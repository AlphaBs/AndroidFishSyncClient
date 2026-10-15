package io.github.alphabs.fishsync.core.files

data class SyncFileMetadata(
    val size: Long = 0,
    val checksum: SyncFileChecksum? = null,
)
