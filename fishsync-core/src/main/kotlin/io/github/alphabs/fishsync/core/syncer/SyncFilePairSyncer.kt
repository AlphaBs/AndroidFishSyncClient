package io.github.alphabs.fishsync.core.syncer

import io.github.alphabs.fishsync.core.filecomparers.FileComparer
import io.github.alphabs.fishsync.core.files.SyncFilePair
import io.github.alphabs.fishsync.core.progress.FileProgressEvent
import io.github.alphabs.fishsync.core.progress.SyncFileByteProgress

interface SyncFilePairSyncer {
    suspend fun compareFilePairs(
        pairs: Iterable<SyncFilePair>,
        comparer: FileComparer,
        fileProgress: ((FileProgressEvent) -> Unit)? = null,
        byteProgress: ((SyncFileByteProgress) -> Unit)? = null,
    ): SyncFilePairCollectionCompareResult

    suspend fun syncFilePairs(
        pairs: Iterable<SyncFilePair>,
        fileProgress: ((FileProgressEvent) -> Unit)? = null,
        byteProgress: ((SyncFileByteProgress) -> Unit)? = null,
    )

    suspend fun compareAndSyncFilePairs(
        pairs: Iterable<SyncFilePair>,
        comparer: FileComparer,
        fileProgress: ((FileProgressEvent) -> Unit)? = null,
        byteProgress: ((SyncFileByteProgress) -> Unit)? = null,
    ): SyncFilePairCollectionCompareResult
}
