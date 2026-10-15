package io.github.alphabs.fishsync.core.syncer

import io.github.alphabs.fishsync.core.PathOptions
import io.github.alphabs.fishsync.core.files.SyncFile
import io.github.alphabs.fishsync.core.files.SyncFilePair

open class SyncPathComparer {
    fun comparePaths(
        sources: Iterable<SyncFile>,
        targets: Iterable<SyncFile>,
        options: PathOptions,
    ): SyncFilePathCompareResult {
        val sourceFiles = sources.toList()
        val targetFiles = targets.toList()
        val sourceIndex = index(sourceFiles, options)
        val targetIndex = index(targetFiles, options)
        val added = mutableListOf<SyncFile>()
        val duplicated = mutableListOf<SyncFilePair>()
        for (source in sourceFiles) {
            val target = targetIndex[source.path.subPath]
            if (target == null) {
                added.add(source)
            } else {
                duplicated.add(SyncFilePair(source, target))
            }
        }
        val deleted = targetFiles.filter { it.path.subPath !in sourceIndex }
        return SyncFilePathCompareResult(added, duplicated, deleted)
    }

    private fun index(
        files: List<SyncFile>,
        options: PathOptions,
    ): Map<String, SyncFile> {
        val comparator =
            if (options.caseInsensitive) String.CASE_INSENSITIVE_ORDER else naturalOrder<String>()
        val result = sortedMapOf<String, SyncFile>(comparator)
        for (file in files) {
            require(result.put(file.path.subPath, file) == null) {
                "Duplicate path: ${file.path.subPath}"
            }
        }
        return result
    }
}
