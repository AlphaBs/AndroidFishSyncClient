package io.github.alphabs.fishsync.core.filecomparers

import io.github.alphabs.fishsync.core.files.SyncFilePair
import io.github.alphabs.fishsync.core.internals.AntPattern

open class CompositeFileComparerWithGlob(
    private val errorMode: ComparerErrorHandlingModes = ComparerErrorHandlingModes.ThrowException,
) : IFileComparer {
    private val comparers = mutableListOf<Pair<AntPattern, IFileComparer>>()

    fun add(
        pattern: String,
        comparer: IFileComparer,
    ) {
        comparers.add(AntPattern(pattern) to comparer)
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
