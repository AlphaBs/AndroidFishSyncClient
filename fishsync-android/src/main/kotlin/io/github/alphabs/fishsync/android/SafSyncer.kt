package io.github.alphabs.fishsync.android

import android.content.Context
import android.net.Uri
import io.github.alphabs.fishsync.core.PathOptions
import io.github.alphabs.fishsync.core.files.SyncFile
import io.github.alphabs.fishsync.core.files.SyncFilePair
import io.github.alphabs.fishsync.core.syncer.ISyncFilePairSyncer
import io.github.alphabs.fishsync.core.syncer.ParallelSyncFilePairSyncer
import io.github.alphabs.fishsync.core.syncer.SyncFileCollectionComparerResult
import io.github.alphabs.fishsync.core.syncer.SyncFileCollectionSyncer
import io.github.alphabs.fishsync.core.syncer.SyncerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** LocalSyncer와 같은 조율 계약을 문서 제공자의 트리 URI에 적용한다. */
class SafSyncer(
    private val context: Context,
    private val treeUri: Uri,
    private val pathOptions: PathOptions = PathOptions(),
    pairSyncer: ISyncFilePairSyncer = ParallelSyncFilePairSyncer(),
) : SyncFileCollectionSyncer(pairSyncer, pathOptions) {
    private val storage = SafSyncStorage(context, treeUri)

    fun enumerateSyncFiles(): Iterable<SyncFile> =
        sequence {
            for (entry in storage.list()) {
                val logicalPath =
                    entry.path
                        .replace('/', pathOptions.pathSeparator)
                yield(SafSyncFile(context, treeUri, logicalPath, pathOptions))
            }
        }.asIterable()

    suspend fun compareFiles(
        sources: Iterable<SyncFile>,
        options: SyncerOptions?,
    ): SyncFileCollectionComparerResult =
        withContext(Dispatchers.IO) {
            compareFiles(sources, enumerateSyncFiles(), options)
        }

    suspend fun compareAndSyncFiles(
        sources: Iterable<SyncFile>,
        options: SyncerOptions?,
    ): SyncFileCollectionComparerResult =
        withContext(Dispatchers.IO) {
            compareAndSyncFiles(sources, enumerateSyncFiles(), options)
        }

    override fun createFilePairs(sources: Iterable<SyncFile>): Iterable<SyncFilePair> =
        sources
            .asSequence()
            .map {
                SyncFilePair(it, SafSyncFile(context, treeUri, it.path.subPath, pathOptions))
            }.asIterable()

    fun deleteSafFiles(files: Iterable<SyncFile>) {
        for (file in files) {
            require(file is SafSyncFile && file.treeUri == treeUri) { "Folder URI not matched" }
            if (file.exists) {
                storage.delete(
                    file.path.subPath
                        .replace(pathOptions.pathSeparator, '/'),
                )
            }
        }
    }
}
