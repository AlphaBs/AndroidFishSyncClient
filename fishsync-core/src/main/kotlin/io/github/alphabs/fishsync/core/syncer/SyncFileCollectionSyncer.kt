package io.github.alphabs.fishsync.core.syncer

import io.github.alphabs.fishsync.core.filecomparers.DefaultFileComparerFactory
import io.github.alphabs.fishsync.core.filecomparers.FileComparerFactory
import io.github.alphabs.fishsync.core.files.FileEntry
import io.github.alphabs.fishsync.core.files.ReadableSyncStorage
import io.github.alphabs.fishsync.core.files.SourceFile
import io.github.alphabs.fishsync.core.files.SyncFilePair
import io.github.alphabs.fishsync.core.files.WritableSyncStorage
import io.github.alphabs.fishsync.core.hash
import io.github.alphabs.fishsync.core.internals.StorageLocks
import io.github.alphabs.fishsync.core.internals.checkUnchanged
import io.github.alphabs.fishsync.core.progress.SyncProgress
import io.github.alphabs.fishsync.core.relativePath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.util.Locale

class SyncFileCollectionSyncer(
    val caseInsensitive: Boolean = true,
    private val comparerFactory: FileComparerFactory = DefaultFileComparerFactory(),
    private val filePairSyncer: SyncFilePairSyncer = DefaultSyncFilePairSyncer(),
) {
    private val events = MutableSharedFlow<SyncProgress>(extraBufferCapacity = 64)
    val progress = events.asSharedFlow()

    suspend fun compareFiles(
        sources: List<SourceFile>,
        storage: ReadableSyncStorage,
        rules: List<SyncRule>,
        context: SyncContext = SyncContext(),
    ): SyncPlan =
        withContext(Dispatchers.IO) {
            compareInternal(sources, storage, rules, context)
        }

    suspend fun compareAndSyncFiles(
        sources: List<SourceFile>,
        storage: WritableSyncStorage,
        rules: List<SyncRule>,
        context: SyncContext = SyncContext(),
        expectedPlan: SyncPlan? = null,
    ): SyncResult =
        withContext(Dispatchers.IO) {
            StorageLocks.run(storage) {
                val plan = compareInternal(sources, storage, rules, context)
                require(expectedPlan == null || expectedPlan == plan) {
                    "Changes differ from the preview; compare again"
                }

                applyPlan(plan, sources, storage)
            }
        }

    private fun pathKey(path: String): String =
        if (caseInsensitive) path.lowercase(Locale.ROOT) else path

    private fun <T> indexByPath(
        items: List<T>,
        path: (T) -> String,
    ): Map<String, T> {
        val result = linkedMapOf<String, T>()

        for (item in items) {
            val key = pathKey(relativePath(path(item)))
            require(result.put(key, item) == null) { "Duplicate path" }
        }

        return result
    }

    private suspend fun compareInternal(
        sources: List<SourceFile>,
        storage: ReadableSyncStorage,
        rules: List<SyncRule>,
        context: SyncContext,
    ): SyncPlan {
        val evaluator = SyncRuleEvaluator(rules, context, caseInsensitive)
        val sourceIndex = indexByPath(sources) { it.path }
        val targets = indexByPath(storage.list()) { it.path }
        validateInventory(sourceIndex.keys, targets.keys, evaluator)

        val added = mutableListOf<String>()
        val updated = mutableListOf<String>()
        val identical = mutableListOf<String>()

        for (source in sources) {
            currentCoroutineContext().ensureActive()
            val rule = evaluator.forPath(source.path)
            if (rule.action == SyncAction.Exclude) {
                continue
            }

            val target = targets[pathKey(source.path)]
            if (target == null) {
                added.add(source.path)
            } else if (rule.action == SyncAction.InstallOnly) {
                continue
            } else {
                val pair = SyncFilePair(source, target)
                val comparer = comparerFactory.create(rule.comparison)
                if (comparer.areEqual(pair, storage)) {
                    identical.add(source.path)
                } else {
                    updated.add(source.path)
                }
            }
        }

        val deleted = findDeletedPaths(sourceIndex.keys, targets, evaluator)
        val fingerprint = createFingerprint(sources, targets.values, storage)

        return SyncPlan(
            added = added.sorted(),
            updated = updated.sorted(),
            deleted = deleted.sorted(),
            identical = identical.sorted(),
            fingerprint = fingerprint,
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

            evaluator.forPath(path)
        }
    }

    private fun findDeletedPaths(
        sourcePaths: Set<String>,
        targets: Map<String, FileEntry>,
        evaluator: SyncRuleEvaluator,
    ): List<String> {
        val deleted = mutableListOf<String>()

        for ((key, target) in targets) {
            val rule = evaluator.forPath(target.path)
            if (key !in sourcePaths && rule.action == SyncAction.FullSync) {
                deleted.add(target.path)
            }
        }

        return deleted
    }

    private suspend fun createFingerprint(
        sources: List<SourceFile>,
        targets: Collection<FileEntry>,
        storage: ReadableSyncStorage,
    ): String {
        val signature = StringBuilder()

        for (source in sources.sortedBy { it.path }) {
            signature.append(source.path)
            signature.append('\u0000')
            signature.append(source.size)
            signature.append('\u0000')
            signature.append(source.checksum)
            signature.append('\n')
        }

        for (target in targets.sortedBy { it.path }) {
            val checksum =
                storage
                    .read(target.path)
                    .use { hash(it) }
            signature.append(target.path)
            signature.append('\u0000')
            signature.append(checksum)
            signature.append('\n')
        }

        return signature
            .toString()
            .byteInputStream()
            .use { hash(it, "sha1") }
    }

    private suspend fun applyPlan(
        plan: SyncPlan,
        sources: List<SourceFile>,
        storage: WritableSyncStorage,
    ): SyncResult {
        val original =
            storage.list().associate { entry ->
                entry.path to
                    storage
                        .read(entry.path)
                        .use { hash(it) }
            }
        val targets =
            storage
                .list()
                .associateBy { pathKey(it.path) }
        val applied = mutableListOf<String>()
        val deleted = mutableListOf<String>()

        try {
            for (path in plan.added + plan.updated) {
                currentCoroutineContext().ensureActive()
                val source = sources.first { it.path == path }
                val targetPath = targets[pathKey(path)]?.path ?: path

                val target = targets[pathKey(path)] ?: FileEntry(targetPath, null)
                val pair = SyncFilePair(source, target)
                filePairSyncer.sync(
                    pair = pair,
                    storage = storage,
                    expectedTargetChecksum = original[targetPath],
                    onProgress = { events.tryEmit(it) },
                    onApplied = { copied ->
                        applied.add(path)
                        events.tryEmit(SyncProgress(path, "applied", copied, copied))
                    },
                )
            }

            // 전송 실패가 있으면 이 삭제 단계에는 도달하지 않는다.
            for (path in plan.deleted) {
                currentCoroutineContext().ensureActive()
                checkUnchanged(storage, path, original[path])
                storage.delete(path)
                deleted.add(path)
                events.tryEmit(SyncProgress(path, "deleted"))
            }

            return SyncResult(plan, applied.toList(), deleted.toList())
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (failure: Exception) {
            throw SyncFailure(applied.toList(), deleted.toList(), failure)
        }
    }
}
