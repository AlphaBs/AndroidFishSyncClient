package io.github.alphabs.fishsync.core.files

class SyncFileCollection(
    files: Iterable<SyncFile>,
) : AbstractCollection<SyncFile>(),
    ISyncFileCollection {
    private val collection = linkedMapOf<String, SyncFile>()

    init {
        for (file in files) collection[file.path.subPath] = file
    }

    override val size get() = collection.size

    override fun findFileByPath(path: String): SyncFile? = collection[path]

    override fun iterator(): Iterator<SyncFile> =
        collection.values
            .iterator()
}
