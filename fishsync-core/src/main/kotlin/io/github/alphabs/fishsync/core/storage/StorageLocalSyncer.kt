package io.github.alphabs.fishsync.core.storage

import io.github.alphabs.fishsync.core.syncer.SyncContext

/** 일반 파일 또는 SAF 대상을 고정하고 컬렉션 동기화기에 연결한다. */
class StorageLocalSyncer(
    private val storage: WritableSyncStorage,
    private val collectionSyncer: StorageSyncer = StorageSyncer(),
) {
    val progress get() = collectionSyncer.progress

    suspend fun compareFiles(
        sources: List<SourceFile>,
        rules: List<StorageSyncRule>,
        context: SyncContext = SyncContext(),
    ): SyncPlan = collectionSyncer.compareFiles(sources, storage, rules, context)

    suspend fun compareAndSyncFiles(
        sources: List<SourceFile>,
        rules: List<StorageSyncRule>,
        context: SyncContext = SyncContext(),
        expectedPlan: SyncPlan? = null,
    ): SyncResult =
        collectionSyncer.compareAndSyncFiles(
            sources,
            storage,
            rules,
            context,
            expectedPlan,
        )
}
