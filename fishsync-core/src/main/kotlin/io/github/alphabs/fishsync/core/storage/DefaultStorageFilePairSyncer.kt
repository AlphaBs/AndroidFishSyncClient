package io.github.alphabs.fishsync.core.storage

import io.github.alphabs.fishsync.core.ChecksumAlgorithms
import io.github.alphabs.fishsync.core.filecomparers.ChecksumMetadataValidator
import io.github.alphabs.fishsync.core.files.SyncFile
import io.github.alphabs.fishsync.core.files.SyncFilePair
import io.github.alphabs.fishsync.core.internals.StreamProgressHelper
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.InputStream
import java.io.OutputStream

class DefaultStorageFilePairSyncer : StorageFilePairSyncer {
    override suspend fun sync(
        pair: SyncFilePair,
        storage: WritableSyncStorage,
        onProgress: (SyncProgress) -> Unit,
        onApplied: (Long) -> Unit,
    ) {
        val source = pair.source
        val targetPath = pair.target.path.subPath
        source.metadata
            ?.checksum
            ?.let { ChecksumMetadataValidator.validate(it, "source") }

        storage.prepare(targetPath).use { pending ->
            val copied =
                source.openReadStream().use { input ->
                    pending.output().use { output ->
                        copySource(source, input, output, onProgress)
                    }
                }

            verifyTransfer(source, pending, copied)
            currentCoroutineContext().ensureActive()
            pending.commit()
            onApplied(copied)
        }
    }

    private suspend fun copySource(
        source: SyncFile,
        input: InputStream,
        output: OutputStream,
        onProgress: (SyncProgress) -> Unit,
    ): Long {
        var copied = 0L
        val total =
            source.metadata
                ?.size
                ?.takeIf { it >= 0 }

        StreamProgressHelper.copyStreamWithProgressPerBuffer(input, output, 65536) { count ->
            copied += count
            onProgress(
                SyncProgress(
                    source.path.subPath,
                    "transfer",
                    copied,
                    total,
                ),
            )
        }

        return copied
    }

    private suspend fun verifyTransfer(
        source: SyncFile,
        pending: PendingWrite,
        copied: Long,
    ) {
        val size =
            source.metadata
                ?.size
                ?.takeIf { it >= 0 }
        if (size != null && size != copied) {
            throw IntegrityException(source.path.subPath)
        }

        val expected =
            source.metadata
                ?.checksum ?: return
        val actual =
            pending
                .input()
                .use {
                    ChecksumAlgorithms.computeHashAsync(
                        requireNotNull(expected.algorithmName),
                        it,
                    )
                }
        if (!actual.equals(expected.checksumHexString, true)) {
            throw IntegrityException(source.path.subPath)
        }
    }
}
