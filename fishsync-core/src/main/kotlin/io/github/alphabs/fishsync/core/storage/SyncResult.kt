package io.github.alphabs.fishsync.core.storage

data class SyncResult(
    val applied: List<String>,
    val deleted: List<String>,
)
