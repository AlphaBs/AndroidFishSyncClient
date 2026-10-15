package io.github.alphabs.fishsync.core.storage

import io.github.alphabs.fishsync.core.hash
import io.github.alphabs.fishsync.core.storage.internals.checkUnchanged
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.InputStream
import java.io.OutputStream

class DefaultStorageFilePairSyncer : StorageFilePairSyncer {
    override suspend fun sync(
        pair: StorageFilePair,
        storage: WritableSyncStorage,
        expectedTargetChecksum: String?,
        onProgress: (SyncProgress) -> Unit,
        onApplied: (Long) -> Unit,
    ) {
        val source = pair.source
        val targetPath = pair.target.path

        storage.prepare(targetPath).use { pending ->
            val copied =
                source.open().use { input ->
                    pending.output().use { output ->
                        copySource(source, input, output, onProgress)
                    }
                }

            verifyTransfer(source, pending, copied)
            checkUnchanged(storage, targetPath, expectedTargetChecksum)
            currentCoroutineContext().ensureActive()
            pending.commit()
            onApplied(copied)
        }
    }

    private suspend fun copySource(
        source: SourceFile,
        input: InputStream,
        output: OutputStream,
        onProgress: (SyncProgress) -> Unit,
    ): Long {
        val buffer = ByteArray(65536)
        var copied = 0L

        while (true) {
            currentCoroutineContext().ensureActive()
            val bytesRead = input.read(buffer)
            if (bytesRead < 0) {
                break
            }

            output.write(buffer, 0, bytesRead)
            copied += bytesRead
            onProgress(SyncProgress(source.path, "transfer", copied, source.size))
        }

        return copied
    }

    private suspend fun verifyTransfer(
        source: SourceFile,
        pending: PendingWrite,
        copied: Long,
    ) {
        if (source.size != null && source.size != copied) {
            throw IntegrityException(source.path)
        }

        val expected = source.checksum ?: return
        val actual =
            pending
                .input()
                .use { hash(it, expected.algorithm) }
        if (!actual.equals(expected.hex, true)) {
            throw IntegrityException(source.path)
        }
    }
}
