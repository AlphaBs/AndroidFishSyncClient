package io.github.alphabs.fishsync.core.filecomparers

import io.github.alphabs.fishsync.core.files.LocalSyncFile
import io.github.alphabs.fishsync.core.files.SyncFilePair

open class LocalFileSizeComparer : IFileComparer {
    override suspend fun areEqual(pair: SyncFilePair): Boolean {
        val target =
            pair.target as? LocalSyncFile
                ?: throw FileComparerException("Target should be LocalSyncFile")
        if (!target.exists) return false
        val size =
            pair.source.metadata
                ?.size ?: -1
        if (size < 0) throw FileComparerException("Cannot get source size")
        return size == target.length
    }
}
