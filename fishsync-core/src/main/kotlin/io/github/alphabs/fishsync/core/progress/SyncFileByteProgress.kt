package io.github.alphabs.fishsync.core.progress

import io.github.alphabs.fishsync.core.files.SyncFile

data class SyncFileByteProgress(
    val syncFile: SyncFile,
    val progress: ByteProgress,
)
