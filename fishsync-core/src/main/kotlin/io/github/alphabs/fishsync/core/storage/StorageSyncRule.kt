package io.github.alphabs.fishsync.core.storage

import io.github.alphabs.fishsync.core.filecomparers.IFileComparer
import io.github.alphabs.fishsync.core.filecomparers.LocalFileChecksumComparer
import io.github.alphabs.fishsync.core.filecomparers.LocalFileSizeComparer
import io.github.alphabs.fishsync.core.syncer.SyncAction
import io.github.alphabs.fishsync.core.syncer.SyncCondition
import io.github.alphabs.fishsync.core.syncer.SyncRule

data class StorageSyncRule(
    val action: SyncAction,
    val pattern: String = "**",
    val condition: SyncCondition = SyncCondition.Always,
    val comparison: Comparison = Comparison.Checksum,
    val comparer: IFileComparer? = null,
) {
    fun toSyncRule(): SyncRule =
        SyncRule(
            action,
            condition,
            pattern,
            comparer ?: when (comparison) {
                Comparison.Size -> LocalFileSizeComparer()
                Comparison.Checksum -> LocalFileChecksumComparer()
                Comparison.SizeAndChecksum ->
                    IFileComparer { pair ->
                        LocalFileSizeComparer().areEqual(pair) &&
                            LocalFileChecksumComparer().areEqual(pair)
                    }
            },
        )
}
