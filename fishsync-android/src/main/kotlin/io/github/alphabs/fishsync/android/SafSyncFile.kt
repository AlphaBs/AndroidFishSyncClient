package io.github.alphabs.fishsync.android

import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import io.github.alphabs.fishsync.android.internals.SafFileAccess
import io.github.alphabs.fishsync.core.PathOptions
import io.github.alphabs.fishsync.core.RootedPath
import io.github.alphabs.fishsync.core.files.LocalSyncFile
import io.github.alphabs.fishsync.core.files.SyncFileMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.resumeWithException

/** URI는 별도로 보관하며 path는 파일 시스템 루트가 없는 논리 상대 경로다. */
class SafSyncFile(
    context: Context,
    val treeUri: Uri,
    relativePath: String,
    pathOptions: PathOptions = PathOptions(),
    metadata: SyncFileMetadata? = null,
) : LocalSyncFile(RootedPath.fromSubPath(relativePath, pathOptions), metadata) {
    private val files = SafFileAccess(context, treeUri)
    private val documentPath =
        path.subPath
            .replace(pathOptions.pathSeparator, '/')
    override val exists get() = files.exists(documentPath)
    override val length get() = files.length(documentPath)
    override val isReadable = true
    override val isWritable = true

    override suspend fun openReadStream(): InputStream = open { files.openRead(documentPath, it) }

    override suspend fun openWriteStream(): OutputStream =
        open { files.openWrite(documentPath, it) }

    private suspend fun <T : Closeable> open(operation: (CancellationSignal) -> T): T =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            CoroutineScope(continuation.context).launch(Dispatchers.IO) {
                try {
                    val stream = operation(signal)
                    continuation.resume(stream) { _, value, _ -> value.close() }
                } catch (failure: Throwable) {
                    if (continuation.isActive) continuation.resumeWithException(failure)
                }
            }
        }
}
