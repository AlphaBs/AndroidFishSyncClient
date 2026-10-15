package io.github.alphabs.fishsync.core.syncer

import io.github.alphabs.fishsync.core.PathOptions
import io.github.alphabs.fishsync.core.filecomparers.IFileComparer
import io.github.alphabs.fishsync.core.files.SyncFile
import io.github.alphabs.fishsync.core.files.SyncFilePair
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

open class SyncFileCollectionSyncer(
    private val filePairSyncer: ISyncFilePairSyncer,
    private val pathOptions: PathOptions,
) {
    suspend fun compareFiles(
        sources: Iterable<SyncFile>,
        targets: Iterable<SyncFile>,
        options: SyncerOptions?,
    ): SyncFileCollectionComparerResult {
        requireNotNull(options) { "Explicit sync options are required" }
        val evaluator = SyncRuleEvaluator(options, pathOptions)
        val paths = selectFiles(sources, targets, evaluator)
        val compared =
            filePairSyncer.compareFilePairs(
                paths.duplicatedFiles,
                ruleComparer(evaluator),
                options.fileProgress,
                options.byteProgress,
            )
        return SyncFileCollectionComparerResult(
            paths.addedFiles,
            compared.updatedFiles,
            compared.identicalFiles,
            paths.deletedFiles,
        )
    }

    suspend fun compareAndSyncFiles(
        sources: Iterable<SyncFile>,
        targets: Iterable<SyncFile>,
        options: SyncerOptions?,
    ): SyncFileCollectionComparerResult {
        requireNotNull(options) { "Explicit sync options are required" }
        val evaluator = SyncRuleEvaluator(options, pathOptions)
        val paths = selectFiles(sources, targets, evaluator)
        val compared =
            filePairSyncer.compareAndSyncFilePairs(
                createFilePairs(paths.addedFiles) + paths.duplicatedFiles,
                ruleComparer(evaluator),
                options.fileProgress,
                options.byteProgress,
            )
        return SyncFileCollectionComparerResult(
            paths.addedFiles,
            compared.updatedFiles,
            compared.identicalFiles,
            paths.deletedFiles,
        )
    }

    protected open fun createFilePairs(sources: Iterable<SyncFile>): List<SyncFilePair> =
        emptyList()

    private suspend fun selectFiles(
        sources: Iterable<SyncFile>,
        targets: Iterable<SyncFile>,
        evaluator: SyncRuleEvaluator,
    ): SyncFilePathCompareResult {
        currentCoroutineContext().ensureActive()
        val paths = SyncPathComparer().comparePaths(sources, targets, pathOptions)
        return SyncFilePathCompareResult(
            paths.addedFiles.filter {
                evaluator
                    .evaluate(it.path.subPath)
                    .action !=
                    SyncAction.Exclude
            },
            paths.duplicatedFiles.filter {
                evaluator
                    .evaluate(it.source.path.subPath)
                    .action in
                    listOf(SyncAction.FullSync, SyncAction.UpdateOnly)
            },
            paths.deletedFiles.filter {
                evaluator
                    .evaluate(it.path.subPath)
                    .action ==
                    SyncAction.FullSync
            },
        )
    }

    private fun ruleComparer(evaluator: SyncRuleEvaluator): IFileComparer =
        IFileComparer { pair ->
            evaluator
                .evaluate(pair.source.path.subPath)
                .comparer!!
                .areEqual(pair)
        }
}
