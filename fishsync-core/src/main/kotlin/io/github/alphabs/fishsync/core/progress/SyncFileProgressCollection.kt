package io.github.alphabs.fishsync.core.progress

import io.github.alphabs.fishsync.core.files.ISyncFileCollection
import io.github.alphabs.fishsync.core.files.SyncFile

class SyncFileProgressCollection(
    files: Iterable<SyncFile> = emptyList(),
) : AbstractCollection<SyncFile>(),
    ISyncFileCollection {
    data class SyncFileProgressItem(
        val file: SyncFile,
        var eventType: FileProgressEventType = FileProgressEventType.None,
        var progress: ByteProgress = ByteProgress(),
    )

    private val collection = linkedMapOf<String, SyncFileProgressItem>()
    private val aggregator = ConcurrentByteProgressAggregator()

    init {
        for (file in files) add(file)
    }

    override val size get() = collection.size

    fun getItems(): List<SyncFileProgressItem> =
        collection.values
            .toList()

    fun findItemByPath(path: String): SyncFileProgressItem? = collection[path]

    override fun findFileByPath(path: String): SyncFile? = findItemByPath(path)?.file

    fun add(file: SyncFile) {
        collection[file.path.subPath] = SyncFileProgressItem(file)
    }

    fun setEventType(
        file: SyncFile,
        type: FileProgressEventType,
    ): Boolean = setEventType(file.path.subPath, type)

    fun setEventType(
        path: String,
        type: FileProgressEventType,
    ): Boolean {
        val item = collection[path] ?: return false
        item.eventType = type
        return true
    }

    fun addProgress(
        file: SyncFile,
        progress: ByteProgress,
    ): Boolean = addProgress(file.path.subPath, progress)

    fun addProgress(
        path: String,
        progress: ByteProgress,
    ): Boolean {
        val item = collection[path] ?: return false
        item.progress += progress
        aggregator.report(progress)
        return true
    }

    fun clearProgress() {
        aggregator.clear()
        for (item in collection.values) item.progress = ByteProgress()
    }

    fun aggregateProgress(): ByteProgress = aggregator.aggregateProgress()

    override fun iterator(): Iterator<SyncFile> =
        collection.values
            .map { it.file }
            .iterator()
}
