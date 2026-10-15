package io.github.alphabs.fishsync.core.filecomparers

import io.github.alphabs.fishsync.core.files.SyncFilePair
import io.github.alphabs.fishsync.core.internals.GlobPattern
import io.github.alphabs.fishsync.core.syncer.glob

class CompositeFileComparerWithGlob(
    private val errorMode: ComparerErrorHandlingModes = ComparerErrorHandlingModes.ThrowException,
) : IFileComparer {
    private val comparers = mutableListOf<Pair<GlobPattern, IFileComparer>>()

    fun add(
        pattern: String,
        comparer: IFileComparer,
    ) {
        comparers.add(glob(pattern) to comparer)
    }

    override suspend fun areEqual(pair: SyncFilePair): Boolean {
        for ((pattern, comparer) in comparers) {
            if (pattern.matches(pair.source.path.subPath)) return comparer.areEqual(pair)
        }
        return when (errorMode) {
            ComparerErrorHandlingModes.ReturnEqual -> true
            ComparerErrorHandlingModes.ReturnNotEqual -> false
            ComparerErrorHandlingModes.ThrowException -> throw FileComparerException(
                "No matching pattern: ${pair.source.path.subPath}",
            )
        }
    }
}
