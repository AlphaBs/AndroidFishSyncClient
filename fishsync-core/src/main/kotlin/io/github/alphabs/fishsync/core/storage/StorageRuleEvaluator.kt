package io.github.alphabs.fishsync.core.storage

import io.github.alphabs.fishsync.core.PathOptions
import io.github.alphabs.fishsync.core.syncer.SyncContext
import io.github.alphabs.fishsync.core.syncer.SyncRuleEvaluator
import io.github.alphabs.fishsync.core.syncer.SyncerOptions

internal class StorageRuleEvaluator(
    private val rules: List<StorageSyncRule>,
    context: SyncContext,
    caseInsensitive: Boolean,
) {
    private val original = rules.map { it.toSyncRule() }
    private val evaluator =
        SyncRuleEvaluator(
            SyncerOptions(original, context),
            PathOptions(caseInsensitive = caseInsensitive),
        )

    fun forPath(path: String): StorageSyncRule = rules[original.indexOf(evaluator.evaluate(path))]
}
