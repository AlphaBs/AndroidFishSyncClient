package io.github.alphabs.fishsync.core

import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class SyncEngine(val caseInsensitive: Boolean = true) {
    private val events = MutableSharedFlow<SyncProgress>(extraBufferCapacity = 64)
    val progress = events.asSharedFlow()
    private fun key(path: String) = if (caseInsensitive) path.lowercase(Locale.ROOT) else path
    private fun <T> indexed(items: List<T>, path: (T) -> String): Map<String, T> {
        val result = linkedMapOf<String, T>()
        for (item in items) require(result.put(key(relativePath(path(item))), item) == null) { "Duplicate path" }
        return result
    }
    private suspend fun equal(source: SourceFile, target: FileEntry, storage: SyncStorage, comparison: Comparison): Boolean {
        if (comparison != Comparison.Checksum && source.size != null && source.size != target.size) return false
        if (comparison == Comparison.Size) return true
        val checksum = source.checksum ?: return true
        return storage.read(target.path).use { hash(it, checksum.algorithm) }.equals(checksum.hex, true)
    }
    suspend fun compare(sources: List<SourceFile>, storage: SyncStorage, rules: List<SyncRule>, context: SyncContext = SyncContext()): SyncPlan = withContext(Dispatchers.IO) {
        compareInternal(sources, storage, rules, context)
    }
    private suspend fun compareInternal(sources: List<SourceFile>, storage: SyncStorage, rules: List<SyncRule>, context: SyncContext): SyncPlan {
        val evaluator = RuleSet(rules, context, caseInsensitive)
        val src = indexed(sources) { it.path }
        val dst = indexed(storage.list()) { it.path }
        val added = mutableListOf<String>(); val updated = mutableListOf<String>()
        val deleted = mutableListOf<String>(); val identical = mutableListOf<String>()
        // Validate the entire inventory before beginning any transfer.
        for (p in (src.keys + dst.keys)) {
            var parent = p.substringBeforeLast('/', "")
            while (parent.isNotEmpty()) { require(parent !in src && parent !in dst) { "File/directory path collision" }; parent = parent.substringBeforeLast('/', "") }
            evaluator.forPath(p)
        }
        for (s in sources) {
            currentCoroutineContext().ensureActive()
            val rule = evaluator.forPath(s.path)
            if (rule.action == SyncAction.Exclude) continue
            val target = dst[key(s.path)]
            if (target == null) added.add(s.path)
            else if (rule.action == SyncAction.InstallOnly) continue
            else if (equal(s, target, storage, rule.comparison)) identical.add(s.path)
            else updated.add(s.path)
        }
        for ((p, t) in dst) if (p !in src && evaluator.forPath(t.path).action == SyncAction.FullSync) deleted.add(t.path)
        val signature = buildString {
            sources.sortedBy { it.path }.forEach { append(it.path).append('\u0000').append(it.size).append('\u0000').append(it.checksum).append('\n') }
            dst.values.sortedBy { it.path }.forEach { append(it.path).append('\u0000').append(storage.read(it.path).use { stream -> hash(stream) }).append('\n') }
        }.byteInputStream().use { hash(it, "sha1") }
        return SyncPlan(added.sorted(), updated.sorted(), deleted.sorted(), identical.sorted(), signature)
    }
    suspend fun pull(sources: List<SourceFile>, storage: SyncStorage, rules: List<SyncRule>, context: SyncContext = SyncContext(), expectedPlan: SyncPlan? = null): SyncResult = withContext(Dispatchers.IO) {
        StorageLocks.run(storage) {
            val plan = compareInternal(sources, storage, rules, context)
            require(expectedPlan == null || expectedPlan == plan) { "Changes differ from the preview; compare again" }
            val original = storage.list().associate { entry -> entry.path to storage.read(entry.path).use { hash(it) } }
            val targets = storage.list().associateBy { key(it.path) }
            val applied = mutableListOf<String>(); val deleted = mutableListOf<String>()
            try {
                for (path in plan.added + plan.updated) {
                    currentCoroutineContext().ensureActive()
                    val source = sources.first { it.path == path }
                    val targetPath = targets[key(path)]?.path ?: path
                    storage.prepare(targetPath).use { pending ->
                        var copied = 0L
                        source.open().use { input -> pending.output().use { output ->
                            val buffer = ByteArray(65536)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val n = input.read(buffer); if (n < 0) break
                                output.write(buffer, 0, n); copied += n
                                events.tryEmit(SyncProgress(path, "transfer", copied, source.size))
                            }
                        } }
                        if (source.size != null && source.size != copied) throw IntegrityException(path)
                        source.checksum?.let { checksum ->
                            if (!pending.input().use { hash(it, checksum.algorithm) }.equals(checksum.hex, true)) throw IntegrityException(path)
                        }
                        checkUnchanged(storage, targetPath, original[targetPath])
                        currentCoroutineContext().ensureActive()
                        pending.commit(); applied.add(path)
                        events.tryEmit(SyncProgress(path, "applied", copied, copied))
                    }
                }
                // Any transfer failure skips the deletion phase.
                for (path in plan.deleted) {
                    currentCoroutineContext().ensureActive()
                    checkUnchanged(storage, path, original[path])
                    storage.delete(path); deleted.add(path)
                    events.tryEmit(SyncProgress(path, "deleted"))
                }
                SyncResult(plan, applied.toList(), deleted.toList())
            } catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { throw SyncFailure(applied.toList(), deleted.toList(), failure) }
        }
    }
    private suspend fun checkUnchanged(storage: SyncStorage, path: String, original: String?) {
        val current = storage.list().firstOrNull { it.path == path }
        val digest = current?.let { storage.read(path).use { hash(it) } }
        if (digest != original) throw ChangedFileException(path)
    }
}
