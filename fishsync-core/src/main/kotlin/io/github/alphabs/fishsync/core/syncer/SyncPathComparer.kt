package io.github.alphabs.fishsync.core.syncer

import io.github.alphabs.fishsync.core.PathOptions
import io.github.alphabs.fishsync.core.files.SyncFile
import io.github.alphabs.fishsync.core.files.SyncFilePair

class SyncPathComparer {
    fun comparePaths(
        sources: Iterable<SyncFile>,
        targets: Iterable<SyncFile>,
        options: PathOptions,
    ): SyncFilePathCompareResult {
        val sourceIndex = index(sources, options)
        val targetIndex = index(targets, options)
        val duplicated = mutableListOf<SyncFilePair>()
        for ((key, source) in sourceIndex) {
            val target = targetIndex[key] ?: continue
            duplicated.add(SyncFilePair(source, target))
        }
        for (pair in duplicated) {
            sourceIndex.remove(key(pair.source.path.subPath, options))
            targetIndex.remove(key(pair.target.path.subPath, options))
        }
        return SyncFilePathCompareResult(
            sourceIndex.values
                .toList(),
            duplicated,
            targetIndex.values
                .toList(),
        )
    }

    private fun index(
        files: Iterable<SyncFile>,
        options: PathOptions,
    ): LinkedHashMap<String, SyncFile> {
        val result = linkedMapOf<String, SyncFile>()
        for (file in files) {
            require(result.put(key(file.path.subPath, options), file) == null) {
                "Duplicate path: ${file.path.subPath}"
            }
        }
        return result
    }

    private fun key(
        path: String,
        options: PathOptions,
    ): String {
        if (!options.caseInsensitive) return path
        val result = StringBuilder()
        val points =
            path
                .codePoints()
                .iterator()
        while (points.hasNext()) result.appendCodePoint(Character.toUpperCase(points.nextInt()))
        return result.toString()
    }
}
