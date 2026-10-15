package io.github.alphabs.fishsync.core.files

import io.github.alphabs.fishsync.core.progress.SyncFileByteProgress
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class SyncFilePair(
    val source: SyncFile,
    val target: SyncFile,
) {
    suspend fun syncContent(progress: ((SyncFileByteProgress) -> Unit)? = null) {
        currentCoroutineContext().ensureActive()
        target.openWriteStream().use { output ->
            source.copyTo(output) { progress?.invoke(SyncFileByteProgress(source, it)) }
        }
    }
}
