package io.github.alphabs.fishsync.core.storage

import io.github.alphabs.fishsync.core.PathOptions
import io.github.alphabs.fishsync.core.files.SyncFile
import io.github.alphabs.fishsync.core.files.SyncFilePair
import io.github.alphabs.fishsync.core.storage.relativePath
import io.github.alphabs.fishsync.core.syncer.ParallelSyncFilePairSyncer
import io.github.alphabs.fishsync.core.syncer.SyncContext
import io.github.alphabs.fishsync.core.syncer.SyncFileCollectionComparerResult
import io.github.alphabs.fishsync.core.syncer.SyncFileCollectionSyncer
import io.github.alphabs.fishsync.core.syncer.SyncRule
import io.github.alphabs.fishsync.core.syncer.SyncRuleEvaluator
import io.github.alphabs.fishsync.core.syncer.SyncerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

class StorageSyncer(
    val caseInsensitive: Boolean = true,
    private val filePairSyncer: StorageFilePairSyncer = DefaultStorageFilePairSyncer(),
) {
    private val events = MutableSharedFlow<SyncProgress>(extraBufferCapacity = 64)
    val progress = events.asSharedFlow()

    suspend fun compareFiles(
        sources: List<SyncFile>,
        storage: ReadableSyncStorage,
        rules: List<SyncRule>,
        context: SyncContext = SyncContext(),
    ): SyncFileCollectionComparerResult =
        withContext(Dispatchers.IO) {
            compareInternal(sources, storage, rules, context)
        }

    suspend fun compareAndSyncFiles(
        sources: List<SyncFile>,
        storage: WritableSyncStorage,
        rules: List<SyncRule>,
        context: SyncContext = SyncContext(),
    ): SyncResult =
        withContext(Dispatchers.IO) {
            val compared = compareInternal(sources, storage, rules, context)
            syncFiles(compared, storage)
        }

    private val pathComparator =
        if (caseInsensitive) String.CASE_INSENSITIVE_ORDER else naturalOrder<String>()

    private fun <T> indexByPath(
        items: List<T>,
        path: (T) -> String,
    ): Map<String, T> {
        val result = sortedMapOf<String, T>(pathComparator)

        for (item in items) {
            val key = relativePath(path(item))
            require(result.put(key, item) == null) { "Duplicate path" }
        }

        return result
    }

    private suspend fun compareInternal(
        sources: List<SyncFile>,
        storage: ReadableSyncStorage,
        rules: List<SyncRule>,
        context: SyncContext,
    ): SyncFileCollectionComparerResult {
        val evaluator =
            SyncRuleEvaluator(
                SyncerOptions(rules, context),
                PathOptions(caseInsensitive = caseInsensitive),
            )
        val sourceIndex = indexByPath(sources) { it.path.subPath }
        val targets = indexByPath(storage.list()) { it.path }
        validateInventory(sourceIndex.keys, targets.keys, evaluator)

        val original =
            SyncFileCollectionSyncer(
                ParallelSyncFilePairSyncer(1),
                PathOptions(caseInsensitive = caseInsensitive),
            )
        return original.compareFiles(
            sources,
            targets.values
                .map { StorageLocalFile(storage, it) },
            SyncerOptions(rules, context),
        )
    }

    private fun validateInventory(
        sourcePaths: Set<String>,
        targetPaths: Set<String>,
        evaluator: SyncRuleEvaluator,
    ) {
        // 전송을 시작하기 전에 전체 경로와 규칙을 검증한다.
        for (path in sourcePaths + targetPaths) {
            var parent = path.substringBeforeLast('/', "")

            while (parent.isNotEmpty()) {
                require(parent !in sourcePaths && parent !in targetPaths) {
                    "File/directory path collision"
                }
                parent = parent.substringBeforeLast('/', "")
            }

            evaluator.evaluate(path)
        }
    }

    private suspend fun syncFiles(
        compared: SyncFileCollectionComparerResult,
        storage: WritableSyncStorage,
    ): SyncResult {
        val addedPairs =
            compared.addedFiles.map { source ->
                val target = FileEntry(source.path.subPath, null)
                SyncFilePair(source, StorageLocalFile(storage, target))
            }
        val applied = mutableListOf<String>()
        val deleted = mutableListOf<String>()

        try {
            for (pair in addedPairs + compared.updatedFilePairs) {
                currentCoroutineContext().ensureActive()
                val path = pair.source.path.subPath
                filePairSyncer.sync(
                    pair = pair,
                    storage = storage,
                    onProgress = { events.tryEmit(it) },
                    onApplied = { copied ->
                        applied.add(path)
                        events.tryEmit(SyncProgress(path, "applied", copied, copied))
                    },
                )
            }

            // 전송 실패가 있으면 이 삭제 단계에는 도달하지 않는다.
            for (file in compared.deletedFiles) {
                currentCoroutineContext().ensureActive()
                val path = file.path.subPath
                storage.delete(path)
                deleted.add(path)
                events.tryEmit(SyncProgress(path, "deleted"))
            }

            return SyncResult(applied.toList(), deleted.toList())
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (failure: Exception) {
            throw SyncFailure(applied.toList(), deleted.toList(), failure)
        }
    }
}
