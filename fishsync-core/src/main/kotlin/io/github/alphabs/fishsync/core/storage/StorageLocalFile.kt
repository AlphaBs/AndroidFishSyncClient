package io.github.alphabs.fishsync.core.storage

import io.github.alphabs.fishsync.core.PathOptions
import io.github.alphabs.fishsync.core.RootedPath
import io.github.alphabs.fishsync.core.filecomparers.FileComparerException
import io.github.alphabs.fishsync.core.files.LocalSyncFile
import java.io.InputStream
import java.io.OutputStream

/** SAF 등 파일 시스템 경로가 없는 로컬 대상을 원본 비교기에 연결한다. */
internal class StorageLocalFile(
    private val storage: ReadableSyncStorage,
    private val entry: FileEntry,
) : LocalSyncFile(RootedPath.fromSubPath(entry.path, PathOptions())) {
    override val exists get() =
        storage
            .list()
            .any { it.path == entry.path }
    override val length get() =
        entry.size
            ?: throw FileComparerException("Provider does not report file size")
    override val isReadable = true
    override val isWritable = false

    override suspend fun openReadStream(): InputStream = storage.read(entry.path)

    override suspend fun openWriteStream(): OutputStream =
        throw UnsupportedOperationException("Use pending writes")
}
