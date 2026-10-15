package io.github.alphabs.fishsync.core.filecomparers

import io.github.alphabs.fishsync.core.files.SyncFilePair
import io.github.alphabs.fishsync.core.internals.GlobPattern

open class CompositeFileComparerWithGlob(
    private val errorMode: ComparerErrorHandlingModes = ComparerErrorHandlingModes.ThrowException,
) : FileComparer {
    private val comparers = mutableListOf<Pair<GlobPattern, FileComparer>>()

    fun add(
        pattern: String,
        comparer: FileComparer,
    ) {
        comparers.add(GlobPattern(pattern) to comparer)
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
