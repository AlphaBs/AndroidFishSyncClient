package io.github.alphabs.fishsync.core.syncer

data class SyncContext(
    val isNewVersion: Boolean = false,
    val isForced: Boolean = false,
)
