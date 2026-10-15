package io.github.alphabs.fishsync.core.syncer

import io.github.alphabs.fishsync.core.files.SyncFilePair

data class SyncFilePairCollectionCompareResult(
    val updatedFiles: List<SyncFilePair>,
    val identicalFiles: List<SyncFilePair>,
)
