package io.github.alphabs.fishsync.core.files

import io.github.alphabs.fishsync.core.PathHelper
import io.github.alphabs.fishsync.core.RootedPath
import io.github.alphabs.fishsync.core.internals.LocalPathGuard
import io.github.alphabs.fishsync.core.internals.StreamProgressHelper
import io.github.alphabs.fishsync.core.progress.ByteProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files

open class LocalSyncFile(
    path: RootedPath,
    metadata: SyncFileMetadata? = null,
) : SyncFile(path, metadata) {
    open val exists: Boolean get() {
        LocalPathGuard.ensureNoLinks(path.getFullPath())
        return File(path.getFullPath()).isFile
    }

    open val length: Long get() = Files.size(File(path.getFullPath()).toPath())
    override val isReadable get() = path.isRooted
    override val isWritable get() = path.isRooted

    override suspend fun openReadStream(): InputStream =
        withContext(Dispatchers.IO) {
            currentCoroutineContext().ensureActive()
            LocalPathGuard.ensureNoLinks(path.getFullPath())
            File(path.getFullPath()).inputStream()
        }

    override suspend fun openWriteStream(): OutputStream =
        withContext(Dispatchers.IO) {
            currentCoroutineContext().ensureActive()
            val fullPath = path.getFullPath()
            LocalPathGuard.ensureNoLinks(fullPath)
            PathHelper.createParentDirectory(fullPath)
            LocalPathGuard.ensureNoLinks(fullPath)
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
