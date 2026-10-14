package io.github.alphabs.fishsync.core

class FileSizeComparer : FileComparer {
    override suspend fun areEqual(
        pair: SyncFilePair,
        storage: ReadableSyncStorage,
    ): Boolean {
        val sourceSize = pair.source.size ?: return true
        return sourceSize == pair.target.size
    }
}
