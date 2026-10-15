package io.github.alphabs.fishsync.core.syncer

import io.github.alphabs.fishsync.core.progress.FileProgressEvent
import io.github.alphabs.fishsync.core.progress.SyncFileByteProgress

data class SyncerOptions(
    val rules: List<SyncRule>? = null,
    val context: SyncContext? = SyncContext(),
    val fileProgress: ((FileProgressEvent) -> Unit)? = null,
    val byteProgress: ((SyncFileByteProgress) -> Unit)? = null,
)
