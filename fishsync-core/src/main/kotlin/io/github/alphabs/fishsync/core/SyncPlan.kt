package io.github.alphabs.fishsync.core

data class SyncPlan(
    val added: List<String>,
    val updated: List<String>,
    val deleted: List<String>,
    val identical: List<String>,
    val fingerprint: String = "",
) {
    val hasChanges: Boolean get() =
        added.isNotEmpty() || updated.isNotEmpty() ||
            deleted.isNotEmpty()
}
