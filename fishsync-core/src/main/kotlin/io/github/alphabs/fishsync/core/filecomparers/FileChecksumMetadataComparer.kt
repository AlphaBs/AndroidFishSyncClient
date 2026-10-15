package io.github.alphabs.fishsync.core.filecomparers

import io.github.alphabs.fishsync.core.files.SyncFilePair
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

open class FileChecksumMetadataComparer(
    private val errorMode: ComparerErrorHandlingModes = ComparerErrorHandlingModes.ThrowException,
) : IFileComparer {
    override suspend fun areEqual(pair: SyncFilePair): Boolean {
        currentCoroutineContext().ensureActive()
        val source =
            pair.source.metadata
                ?.checksum ?: return true
        ChecksumMetadataValidator.validate(source, "source")
        val target =
            pair.target.metadata
                ?.checksum ?: return cannotCompare()
        ChecksumMetadataValidator.validate(target, "target")
        if (source.algorithmName != target.algorithmName) return cannotCompare()
        return source.checksumHexString
            .equals(target.checksumHexString, ignoreCase = true)
    }

    private fun cannotCompare(): Boolean =
        when (errorMode) {
            ComparerErrorHandlingModes.ReturnEqual -> true
            ComparerErrorHandlingModes.ReturnNotEqual -> false
            ComparerErrorHandlingModes.ThrowException -> throw FileComparerException(
                "Cannot compare checksum",
            )
        }
}
