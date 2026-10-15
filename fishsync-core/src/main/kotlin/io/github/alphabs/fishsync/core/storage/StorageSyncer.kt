package io.github.alphabs.fishsync.core.storage

import io.github.alphabs.fishsync.core.PathOptions
import io.github.alphabs.fishsync.core.files.SyncFile
import io.github.alphabs.fishsync.core.files.SyncFilePair
import io.github.alphabs.fishsync.core.syncer.ParallelSyncFilePairSyncer
import io.github.alphabs.fishsync.core.syncer.SyncFileCollectionComparerResult
import io.github.alphabs.fishsync.core.syncer.SyncFileCollectionSyncer
import io.github.alphabs.fishsync.core.syncer.SyncFilePairSyncer
import io.github.alphabs.fishsync.core.syncer.SyncerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 저장소 접근을 원본 동기화기에 연결한다. 별도 전송·검증·삭제 정책은 두지 않는다. */
class StorageSyncer(
    private val storage: ReadableSyncStorage,
    caseInsensitive: Boolean = true,
    pairSyncer: SyncFilePairSyncer = ParallelSyncFilePairSyncer(),
) : SyncFileCollectionSyncer(pairSyncer, PathOptions(caseInsensitive = caseInsensitive)) {
    fun enumerateSyncFiles(): Iterable<SyncFile> =
        sequence {
            for (entry in storage.list()) {
                yield(StorageLocalFile(storage, entry))
            }
        }.asIterable()

    suspend fun compareFiles(
        sources: Iterable<SyncFile>,
        options: SyncerOptions,
    ): SyncFileCollectionComparerResult =
        withContext(Dispatchers.IO) {
            compareFiles(sources, enumerateSyncFiles(), options)
        }

    suspend fun compareAndSyncFiles(
        sources: Iterable<SyncFile>,
        options: SyncerOptions,
    ): SyncFileCollectionComparerResult =
        withContext(Dispatchers.IO) {
            compareAndSyncFiles(sources, enumerateSyncFiles(), options)
        }

    override fun createFilePairs(sources: Iterable<SyncFile>): Iterable<SyncFilePair> =
        sources
            .asSequence()
            .map { source ->
                val target = StorageLocalFile(storage, FileEntry(source.path.subPath, null))
                SyncFilePair(source, target)
            }.asIterable()

    fun deleteFiles(files: Iterable<SyncFile>) {
        val writable =
            storage as? WritableSyncStorage
                ?: throw UnsupportedOperationException("Read-only storage")
        for (file in files) {
            require(file is StorageLocalFile && file.storage.identity == storage.identity) {
                "Storage not matched"
            }
            writable.delete(file.path.subPath)
        }
    }
}
