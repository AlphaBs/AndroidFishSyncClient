package io.github.alphabs.fishsync.core

class CompositeFileComparer(
    private val comparers: List<FileComparer>,
) : FileComparer {
    init {
        require(comparers.isNotEmpty())
    }

    override suspend fun areEqual(
        pair: SyncFilePair,
        storage: ReadableSyncStorage,
    ): Boolean {
        for (comparer in comparers) {
            if (!comparer.areEqual(pair, storage)) {
                return false
            }
        }

        return true
    }
}
