package io.github.alphabs.fishsync.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

class SyncEngine(
    val caseInsensitive: Boolean = true,
) {
    private val events = MutableSharedFlow<SyncProgress>(extraBufferCapacity = 64)
    val progress = events.asSharedFlow()

    suspend fun compare(
        sources: List<SourceFile>,
        storage: SyncStorage,
        rules: List<SyncRule>,
        context: SyncContext = SyncContext(),
    ): SyncPlan =
        withContext(Dispatchers.IO) {
            compareInternal(sources, storage, rules, context)
        }

    suspend fun pull(
        sources: List<SourceFile>,
        storage: SyncStorage,
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

    private suspend fun areEqual(
        source: SourceFile,
        target: FileEntry,
        storage: SyncStorage,
        comparison: Comparison,
    ): Boolean {
        val compareSize = comparison != Comparison.Checksum && source.size != null
        if (compareSize && source.size != target.size) {
            return false
        }

        if (comparison == Comparison.Size) {
            return true
        }

        val checksum = source.checksum ?: return true
        val targetHash =
            storage
                .read(target.path)
                .use { hash(it, checksum.algorithm) }

        return targetHash.equals(checksum.hex, true)
    }

    private suspend fun compareInternal(
        sources: List<SourceFile>,
        storage: SyncStorage,
        rules: List<SyncRule>,
        context: SyncContext,
    ): SyncPlan {
        val evaluator = RuleSet(rules, context, caseInsensitive)
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
            } else if (areEqual(source, target, storage, rule.comparison)) {
                identical.add(source.path)
            } else {
                updated.add(source.path)
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
        evaluator: RuleSet,
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
        evaluator: RuleSet,
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
        storage: SyncStorage,
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
        storage: SyncStorage,
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

                applySource(source, targetPath, storage, original[targetPath], applied)
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

    private suspend fun applySource(
        source: SourceFile,
        targetPath: String,
        storage: SyncStorage,
        originalHash: String?,
        applied: MutableList<String>,
    ) {
        storage.prepare(targetPath).use { pending ->
            val copied =
                source.open().use { input ->
                    pending
                        .output()
                        .use { output -> copySource(source, input, output) }
                }

            verifyTransfer(source, pending, copied)
            checkUnchanged(storage, targetPath, originalHash)
            currentCoroutineContext().ensureActive()

            pending.commit()
            applied.add(source.path)
            events.tryEmit(SyncProgress(source.path, "applied", copied, copied))
        }
    }

    private suspend fun copySource(
        source: SourceFile,
        input: InputStream,
        output: OutputStream,
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
            events.tryEmit(SyncProgress(source.path, "transfer", copied, source.size))
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

    private suspend fun checkUnchanged(
        storage: SyncStorage,
        path: String,
        original: String?,
    ) {
        val current =
            storage
                .list()
                .firstOrNull { it.path == path }
        val digest =
            current?.let {
                storage
                    .read(path)
                    .use { hash(it) }
            }
        if (digest != original) {
            throw ChangedFileException(path)
        }
    }
}
