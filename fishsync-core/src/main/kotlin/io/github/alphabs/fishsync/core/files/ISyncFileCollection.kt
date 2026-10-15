package io.github.alphabs.fishsync.core.files

interface ISyncFileCollection : Collection<SyncFile> {
    fun findFileByPath(path: String): SyncFile?
}
