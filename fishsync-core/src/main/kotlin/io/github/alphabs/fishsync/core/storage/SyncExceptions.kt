package io.github.alphabs.fishsync.core.storage

class SyncFailure(
    val applied: List<String>,
    val deleted: List<String>,
    cause: Throwable,
) : Exception("Sync failed; some changes may already be applied", cause)

class IntegrityException(
    path: String,
) : Exception("Content verification failed: $path")

class ChangedFileException(
    path: String,
) : Exception("File changed during synchronization: $path")
