package io.github.alphabs.fishsync.core.syncer

import io.github.alphabs.fishsync.core.files.SourceFile
import io.github.alphabs.fishsync.core.files.WritableSyncStorage

/** 일반 파일 또는 SAF 대상을 고정하고 컬렉션 동기화기에 연결한다. */
class LocalSyncer(
    private val storage: WritableSyncStorage,
    private val collectionSyncer: SyncFileCollectionSyncer = SyncFileCollectionSyncer(),
) {
    val progress get() = collectionSyncer.progress

    suspend fun compareFiles(
        sources: List<SourceFile>,
        rules: List<SyncRule>,
        context: SyncContext = SyncContext(),
    ): SyncPlan = collectionSyncer.compareFiles(sources, storage, rules, context)

    suspend fun compareAndSyncFiles(
        sources: List<SourceFile>,
        rules: List<SyncRule>,
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
