package io.github.alphabs.fishsync.core.filecomparers

import io.github.alphabs.fishsync.core.ChecksumAlgorithms
import io.github.alphabs.fishsync.core.files.SyncFileChecksum

internal object ChecksumMetadataValidator {
    fun validate(
        checksum: SyncFileChecksum,
        role: String,
    ) {
        val expected =
            try {
                ChecksumAlgorithms
                    .createHashAlgorithmFromName(
                        checksum.algorithmName ?: throw NoSuchElementException(),
                    ).digestLength * 2
            } catch (_: NoSuchElementException) {
                throw FileComparerException(
                    "Unsupported $role checksum algorithm: ${checksum.algorithmName}",
                )
            }
        val hex = checksum.checksumHexString
        if (hex == null || hex.length != expected ||
            hex.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }
        ) {
            throw FileComparerException("Invalid $role checksum")
        }
    }
}
