package io.github.alphabs.fishsync.core.files

data class SyncFileChecksum(
    val algorithmName: String? = null,
    val checksumHexString: String? = null,
)
