package io.github.alphabs.fishsync.core.syncer

import io.github.alphabs.fishsync.core.filecomparers.IFileComparer
import io.github.alphabs.fishsync.core.files.SyncFilePair
import io.github.alphabs.fishsync.core.progress.FileProgressEvent
import io.github.alphabs.fishsync.core.progress.SyncFileByteProgress

interface ISyncFilePairSyncer {
    suspend fun compareFilePairs(
        pairs: Iterable<SyncFilePair>,
        comparer: IFileComparer,
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
        comparer: IFileComparer,
        fileProgress: ((FileProgressEvent) -> Unit)? = null,
        byteProgress: ((SyncFileByteProgress) -> Unit)? = null,
    ): SyncFilePairCollectionCompareResult
}
