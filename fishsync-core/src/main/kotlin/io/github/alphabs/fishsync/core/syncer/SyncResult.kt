package io.github.alphabs.fishsync.core.syncer

data class SyncResult(
    val plan: SyncPlan,
    val applied: List<String>,
    val deleted: List<String>,
)
