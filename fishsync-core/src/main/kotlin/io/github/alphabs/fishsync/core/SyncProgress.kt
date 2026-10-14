package io.github.alphabs.fishsync.core

data class SyncProgress(
    val path: String,
    val phase: String,
    val bytes: Long = 0,
    val total: Long? = null,
)
