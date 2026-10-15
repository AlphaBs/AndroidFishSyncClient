package io.github.alphabs.fishsync.core.syncer

import io.github.alphabs.fishsync.core.files.SyncFile
import io.github.alphabs.fishsync.core.files.SyncFilePair

data class SyncFilePathCompareResult(
    val addedFiles: List<SyncFile>,
    val duplicatedFiles: List<SyncFilePair>,
    val deletedFiles: List<SyncFile>,
)
