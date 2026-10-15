package io.github.alphabs.fishsync.core.filecomparers

import io.github.alphabs.fishsync.core.ChecksumAlgorithms
import io.github.alphabs.fishsync.core.files.LocalSyncFile
import io.github.alphabs.fishsync.core.files.SyncFilePair
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

open class LocalFileChecksumComparer : FileComparer {
    override suspend fun areEqual(pair: SyncFilePair): Boolean {
        currentCoroutineContext().ensureActive()
        val target =
            pair.target as? LocalSyncFile
                ?: throw FileComparerException("Target should be LocalSyncFile")
        val checksum =
            pair.source.metadata
                ?.checksum
        if (checksum != null) ChecksumMetadataValidator.validate(checksum, "source")
        if (!target.exists) return false
        if (checksum == null) return true
        val actual =
            target.openReadStream().use {
                ChecksumAlgorithms.computeHashAsync(requireNotNull(checksum.algorithmName), it)
            }
        return actual.equals(checksum.checksumHexString, ignoreCase = true)
    }
}
