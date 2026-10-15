package io.github.alphabs.fishsync.core.files

data class SyncFileChecksum(
    val algorithmName: String,
    val checksumHexString: String,
)
