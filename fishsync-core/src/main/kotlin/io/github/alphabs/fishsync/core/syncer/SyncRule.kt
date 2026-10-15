package io.github.alphabs.fishsync.core.syncer

import io.github.alphabs.fishsync.core.filecomparers.IFileComparer

data class SyncRule(
    val action: SyncAction,
    val condition: SyncCondition,
    val pattern: String,
    val comparer: IFileComparer?,
)
