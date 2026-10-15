package io.github.alphabs.fishsync.core.syncer

import io.github.alphabs.fishsync.core.PathOptions
import io.github.alphabs.fishsync.core.filecomparers.FileComparer
import io.github.alphabs.fishsync.core.files.SyncFile
import io.github.alphabs.fishsync.core.files.SyncFilePair
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

open class SyncFileCollectionSyncer(
    private val filePairSyncer: SyncFilePairSyncer,
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
        val pairs =
            createFilePairs(paths.addedFiles)
                .asSequence()
                .plus(
                    paths.duplicatedFiles
                        .asSequence(),
                ).asIterable()
        val compared =
            filePairSyncer.compareAndSyncFilePairs(
                pairs,
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

    protected open fun createFilePairs(sources: Iterable<SyncFile>): Iterable<SyncFilePair> =
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
                val rule = evaluator.evaluate(it.path.subPath)
                rule.action != SyncAction.Exclude
            },
            paths.duplicatedFiles.filter {
                val rule = evaluator.evaluate(it.source.path.subPath)
                rule.action == SyncAction.FullSync || rule.action == SyncAction.UpdateOnly
            },
            paths.deletedFiles.filter {
                val rule = evaluator.evaluate(it.path.subPath)
                rule.action == SyncAction.FullSync
            },
        )
    }

    private fun ruleComparer(evaluator: SyncRuleEvaluator): FileComparer =
        FileComparer { pair ->
            evaluator
                .evaluate(pair.source.path.subPath)
                .comparer!!
                .areEqual(pair)
        }
}
