package io.github.alphabs.fishsync.core.syncer

import io.github.alphabs.fishsync.core.filecomparers.IFileComparer
import io.github.alphabs.fishsync.core.files.SyncFilePair
import io.github.alphabs.fishsync.core.progress.ByteProgress
import io.github.alphabs.fishsync.core.progress.FileProgressEvent
import io.github.alphabs.fishsync.core.progress.FileProgressEventType
import io.github.alphabs.fishsync.core.progress.SyncFileByteProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class ParallelSyncFilePairSyncer(
    private val maxDegreeOfParallelism: Int =
        Runtime.getRuntime().availableProcessors().coerceIn(
            1,
            4,
        ),
) : ISyncFilePairSyncer {
    init {
        require(maxDegreeOfParallelism >= 1)
    }

    override suspend fun compareFilePairs(
        pairs: Iterable<SyncFilePair>,
        comparer: IFileComparer,
        fileProgress: ((FileProgressEvent) -> Unit)?,
        byteProgress: ((SyncFileByteProgress) -> Unit)?,
    ): SyncFilePairCollectionCompareResult {
        val updated = ConcurrentLinkedQueue<SyncFilePair>()
        val identical = ConcurrentLinkedQueue<SyncFilePair>()
        process(pairs, fileProgress, byteProgress, true) { pair ->
            if (comparer.areEqual(pair)) identical.add(pair) else updated.add(pair)
        }
        return SyncFilePairCollectionCompareResult(updated.toList(), identical.toList())
    }

    override suspend fun syncFilePairs(
        pairs: Iterable<SyncFilePair>,
        fileProgress: ((FileProgressEvent) -> Unit)?,
        byteProgress: ((SyncFileByteProgress) -> Unit)?,
    ) {
        process(pairs, fileProgress, byteProgress, false) { it.syncContent(byteProgress) }
    }

    override suspend fun compareAndSyncFilePairs(
        pairs: Iterable<SyncFilePair>,
        comparer: IFileComparer,
        fileProgress: ((FileProgressEvent) -> Unit)?,
        byteProgress: ((SyncFileByteProgress) -> Unit)?,
    ): SyncFilePairCollectionCompareResult {
        val updated = ConcurrentLinkedQueue<SyncFilePair>()
        val identical = ConcurrentLinkedQueue<SyncFilePair>()
        process(pairs, fileProgress, byteProgress, false) { pair ->
            if (comparer.areEqual(pair)) {
                byteProgress?.invoke(
                    SyncFileByteProgress(
                        pair.source,
                        ByteProgress(
                            0,
                            pair.source.metadata
                                ?.size ?: 0,
                        ),
                    ),
                )
                identical.add(pair)
            } else {
                pair.syncContent(byteProgress)
                if (!comparer.areEqual(
                        pair,
                    )
                ) {
                    throw FileIntegrityException(
                        pair.target.path
                            .toString(),
                    )
                }
                updated.add(pair)
            }
        }
        return SyncFilePairCollectionCompareResult(updated.toList(), identical.toList())
    }

    private suspend fun process(
        pairs: Iterable<SyncFilePair>,
        fileProgress: ((FileProgressEvent) -> Unit)?,
        byteProgress: ((SyncFileByteProgress) -> Unit)?,
        compare: Boolean,
        operation: suspend (SyncFilePair) -> Unit,
    ) = coroutineScope {
        val semaphore = Semaphore(maxDegreeOfParallelism)
        val total = AtomicInteger()
        val progressed = AtomicInteger()
        for (pair in pairs) {
            currentCoroutineContext().ensureActive()
            val count = total.incrementAndGet()
            fileProgress?.invoke(
                FileProgressEvent(
                    FileProgressEventType.Queue,
                    progressed.get(),
                    count,
                    pair.source.path.subPath,
                ),
            )
            byteProgress?.invoke(
                SyncFileByteProgress(
                    pair.source,
                    ByteProgress(
                        pair.source.metadata
                            ?.size ?: 0,
                        0,
                    ),
                ),
            )
            launch(Dispatchers.IO) {
                semaphore.withPermit {
                    val start =
                        if (compare) {
                            FileProgressEventType.StartCompare
                        } else {
                            FileProgressEventType.StartSync
                        }
                    val done =
                        if (compare) {
                            FileProgressEventType.DoneCompare
                        } else {
                            FileProgressEventType.DoneSync
                        }
                    fileProgress?.invoke(
                        FileProgressEvent(
                            start,
                            progressed.get(),
                            total.get(),
                            pair.source.path.subPath,
                        ),
                    )
                    operation(pair)
                    val completed = progressed.incrementAndGet()
                    fileProgress?.invoke(
                        FileProgressEvent(done, completed, total.get(), pair.source.path.subPath),
                    )
                }
            }
        }
    }
}
