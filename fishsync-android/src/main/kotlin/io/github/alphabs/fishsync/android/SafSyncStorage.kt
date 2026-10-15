package io.github.alphabs.fishsync.android

import android.content.Context
import android.net.Uri
import io.github.alphabs.fishsync.android.internals.SafFileAccess
import io.github.alphabs.fishsync.core.filecomparers.FileComparerException
import io.github.alphabs.fishsync.core.storage.FileEntry
import io.github.alphabs.fishsync.core.storage.WritableSyncStorage

/** SAF 조회·직접 쓰기·명시적 삭제만 저장소 인터페이스에 연결한다. */
class SafSyncStorage(
    context: Context,
    treeUri: Uri,
) : WritableSyncStorage {
    private val files = SafFileAccess(context, treeUri)
    override val identity = treeUri.toString()

    override fun list(): List<FileEntry> =
        files.listPaths().map { path ->
            val size =
                try {
                    files.length(path)
                } catch (_: FileComparerException) {
                    null
                }
            FileEntry(path, size)
        }

    override fun read(path: String) = files.openRead(path)

    override fun write(path: String) = files.openWrite(path)

    override fun delete(path: String) = files.delete(path)
}
