package io.github.alphabs.fishsync.core.files

interface SyncFileCollection : Collection<SyncFile> {
    fun findFileByPath(path: String): SyncFile?
}
