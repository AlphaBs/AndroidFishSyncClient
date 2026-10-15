package io.github.alphabs.fishsync.core.files

import io.github.alphabs.fishsync.core.PathHelper
import io.github.alphabs.fishsync.core.RootedPath
import io.github.alphabs.fishsync.core.internals.LocalPathGuard
import io.github.alphabs.fishsync.core.internals.StreamOpenHelper
import io.github.alphabs.fishsync.core.internals.StreamProgressHelper
import io.github.alphabs.fishsync.core.internals.endsInNativeDirectorySeparator
import io.github.alphabs.fishsync.core.progress.ByteProgress
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files

open class LocalSyncFile(
    path: RootedPath,
    metadata: SyncFileMetadata? = null,
) : SyncFile(path, metadata) {
    open val exists: Boolean get() {
        val fullPath = path.getFullPath()
        LocalPathGuard.ensureNoLinks(fullPath)
        if (endsInNativeDirectorySeparator(fullPath)) return false
        val local = File(fullPath).toPath()
        return Files.exists(local) && !Files.isDirectory(local)
    }

    open val length: Long get() {
        val fullPath = path.getFullPath()
        if (endsInNativeDirectorySeparator(fullPath)) throw IOException("Not a file: $fullPath")
        val local = File(fullPath).toPath()
        if (Files.isDirectory(local)) throw IOException("Not a file: $fullPath")
        return Files.size(local)
    }
    override val isReadable get() = path.isRooted
    override val isWritable get() = path.isRooted

    override suspend fun openReadStream(): InputStream =
        StreamOpenHelper.open {
            val fullPath = path.getFullPath()
            LocalPathGuard.ensureNoLinks(fullPath)
            if (endsInNativeDirectorySeparator(fullPath)) throw IOException("Not a file: $fullPath")
            File(fullPath).inputStream()
        }

    override suspend fun openWriteStream(): OutputStream =
        StreamOpenHelper.open {
            val fullPath = path.getFullPath()
            LocalPathGuard.ensureNoLinks(fullPath)
            PathHelper.createParentDirectory(fullPath)
            LocalPathGuard.ensureNoLinks(fullPath)
            if (endsInNativeDirectorySeparator(fullPath)) throw IOException("Not a file: $fullPath")
            File(fullPath).outputStream()
        }

    override suspend fun copyTo(
        destination: OutputStream,
        progress: ((ByteProgress) -> Unit)?,
    ) {
        openReadStream().use { source ->
            StreamProgressHelper.copyStreamWithPeriodicProgress(source, destination, 100) {
                progress?.invoke(ByteProgress(0, it))
            }
        }
    }
}
