package io.github.alphabs.fishsync.core.syncer

import io.github.alphabs.fishsync.core.PathHelper
import io.github.alphabs.fishsync.core.PathOptions
import io.github.alphabs.fishsync.core.RootedPath
import io.github.alphabs.fishsync.core.files.LocalSyncFile
import io.github.alphabs.fishsync.core.files.SyncFile
import io.github.alphabs.fishsync.core.files.SyncFilePair
import io.github.alphabs.fishsync.core.internals.LocalPathGuard
import io.github.alphabs.fishsync.core.internals.endsInNativeDirectorySeparator
import java.io.File
import java.io.IOException
import java.nio.file.Files

open class LocalSyncer(
    root: String,
    private val pathOptions: PathOptions,
    filePairSyncer: SyncFilePairSyncer,
) : SyncFileCollectionSyncer(filePairSyncer, pathOptions) {
    private val root = PathHelper.normalizeRoot(root, pathOptions)

    init {
        LocalPathGuard.ensureNoLinks(this.root)
    }

    suspend fun compareFiles(
        sources: Iterable<SyncFile>,
        options: SyncerOptions?,
    ): SyncFileCollectionComparerResult =
        compareFiles(sources, enumerateLocalSyncFiles(root, pathOptions), options)

    suspend fun compareAndSyncFiles(
        sources: Iterable<SyncFile>,
        options: SyncerOptions?,
    ): SyncFileCollectionComparerResult =
        compareAndSyncFiles(sources, enumerateLocalSyncFiles(root, pathOptions), options)

    override fun createFilePairs(sources: Iterable<SyncFile>): Iterable<SyncFilePair> =
        sources
            .asSequence()
            .map {
                SyncFilePair(
                    it,
                    LocalSyncFile(
                        it.path
                            .withRoot(root),
                    ),
                )
            }.asIterable()

    fun deleteLocalFiles(files: Iterable<SyncFile>) {
        for (file in files) {
            require(file.path.root == root) { "Root not matched" }
            val path =
                file.path
                    .getFullPath()
            LocalPathGuard.ensureNoLinks(path)
            if (endsInNativeDirectorySeparator(path)) throw IOException("Not a file: $path")
            val local = File(path)
            require(!local.isDirectory) { "Cannot delete a directory as a file" }
            Files.deleteIfExists(local.toPath())
        }
    }

    companion object {
        fun enumerateLocalSyncFiles(
            root: String,
            options: PathOptions,
        ): Iterable<SyncFile> =
            RootedPath
                .fromDirectory(root, options)
                .map { LocalSyncFile(it) }
                .asIterable()
    }
}
