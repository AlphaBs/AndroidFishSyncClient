package io.github.alphabs.fishsync.core

class FileChecksumComparer : FileComparer {
    override suspend fun areEqual(
        pair: SyncFilePair,
        storage: ReadableSyncStorage,
    ): Boolean {
        val checksum = pair.source.checksum ?: return true
        val targetHash =
            storage
                .read(pair.target.path)
                .use { hash(it, checksum.algorithm) }
        return targetHash.equals(checksum.hex, true)
    }
}
