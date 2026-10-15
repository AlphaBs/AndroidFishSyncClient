package io.github.alphabs.fishsync.core.syncer

import io.github.alphabs.fishsync.core.files.SyncFile
import io.github.alphabs.fishsync.core.files.SyncFilePair

data class SyncFileCollectionComparerResult(
    val addedFiles: List<SyncFile>,
    val updatedFilePairs: List<SyncFilePair>,
    val identicalFilePairs: List<SyncFilePair>,
    val deletedFiles: List<SyncFile>,
)
