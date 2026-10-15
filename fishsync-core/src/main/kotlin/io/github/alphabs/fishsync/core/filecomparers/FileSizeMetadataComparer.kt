package io.github.alphabs.fishsync.core.filecomparers

import io.github.alphabs.fishsync.core.files.SyncFile
import io.github.alphabs.fishsync.core.files.SyncFilePair

open class FileSizeMetadataComparer(
    private val errorMode: ComparerErrorHandlingModes = ComparerErrorHandlingModes.ThrowException,
) : FileComparer {
    override suspend fun areEqual(pair: SyncFilePair): Boolean =
        try {
            size(pair.source) == size(pair.target)
        } catch (failure: FileComparerException) {
            when (errorMode) {
                ComparerErrorHandlingModes.ReturnEqual -> true
                ComparerErrorHandlingModes.ReturnNotEqual -> false
                ComparerErrorHandlingModes.ThrowException -> throw failure
            }
        }

    private fun size(file: SyncFile): Long {
        val size =
            file.metadata
                ?.size ?: -1
        if (size < 0) throw FileComparerException("Cannot get file size: ${file.path}")
        return size
    }
}
