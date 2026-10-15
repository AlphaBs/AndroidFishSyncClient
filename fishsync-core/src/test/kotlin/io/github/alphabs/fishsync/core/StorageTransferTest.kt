package io.github.alphabs.fishsync.core

import io.github.alphabs.fishsync.core.filecomparers.LocalFileChecksumComparer
import io.github.alphabs.fishsync.core.files.SyncFile
import io.github.alphabs.fishsync.core.storage.DefaultStorageFilePairSyncer
import io.github.alphabs.fishsync.core.storage.FileEntry
import io.github.alphabs.fishsync.core.storage.FileSyncStorage
import io.github.alphabs.fishsync.core.storage.PendingWrite
import io.github.alphabs.fishsync.core.storage.ReadableSyncStorage
import io.github.alphabs.fishsync.core.storage.StorageFilePair
import io.github.alphabs.fishsync.core.storage.StorageFilePairSyncer
import io.github.alphabs.fishsync.core.storage.StorageSyncer
import io.github.alphabs.fishsync.core.storage.SyncFailure
import io.github.alphabs.fishsync.core.storage.SyncProgress
import io.github.alphabs.fishsync.core.storage.WritableSyncStorage
import io.github.alphabs.fishsync.core.syncer.SyncAction
import io.github.alphabs.fishsync.core.syncer.SyncCondition
import io.github.alphabs.fishsync.core.syncer.SyncRule
import io.github.alphabs.fishsync.core.versions.IVersionManager
import io.github.alphabs.fishsync.core.versions.NullVersionManager
import io.github.alphabs.fishsync.core.versions.VersionManager
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StorageTransferTest {
    private val rules =
        listOf(
            SyncRule(
                action = SyncAction.FullSync,
                condition = SyncCondition.Always,
                pattern = "**",
                comparer = LocalFileChecksumComparer(),
            ),
        )

    @Test
    fun injectedPairSyncerReceivesPlannedDestination() =
        runBlocking<Unit> {
            val directory =
                Files
                    .createTempDirectory("pair-syncer-")
                    .toFile()
            try {
                val storage = FileSyncStorage(directory)
                val synchronizedPaths = mutableListOf<String>()
                val delegate = DefaultStorageFilePairSyncer()
                val syncer =
                    object : StorageFilePairSyncer {
                        override suspend fun sync(
                            pair: StorageFilePair,
                            storage: WritableSyncStorage,
                            expectedTargetChecksum: String?,
                            onProgress: (SyncProgress) -> Unit,
                            onApplied: (Long) -> Unit,
                        ) {
                            synchronizedPaths.add(pair.target.path)
                            delegate.sync(
                                pair,
                                storage,
                                expectedTargetChecksum,
                                onProgress,
                                onApplied,
                            )
                        }
                    }
                val collectionSyncer = StorageSyncer(filePairSyncer = syncer)
                val result = collectionSyncer.compareAndSyncFiles(listOf(source()), storage, rules)

                assertEquals(listOf("file.txt"), synchronizedPaths)
                assertEquals(listOf("file.txt"), result.applied)
                assertEquals(
                    "123",
                    storage
                        .read("file.txt")
                        .bufferedReader()
                        .use { it.readText() },
                )
            } finally {
                directory.deleteRecursively()
            }
        }

    @Test
    fun committedFileIsReportedWhenClosingFails() =
        runBlocking<Unit> {
            val directory =
                Files
                    .createTempDirectory("close-failure-")
                    .toFile()
            try {
                val delegate = FileSyncStorage(directory)
                val storage =
                    object : WritableSyncStorage by delegate {
                        override fun prepare(path: String): PendingWrite {
                            val pending = delegate.prepare(path)
                            return object : PendingWrite by pending {
                                override fun close() {
                                    pending.close()
                                    throw IOException("Cannot close committed document")
                                }
                            }
                        }
                    }
                val failure =
                    assertFailsWith<SyncFailure> {
                        StorageSyncer().compareAndSyncFiles(
                            listOf(source()),
                            storage,
                            rules,
                        )
                    }

                assertEquals(listOf("file.txt"), failure.applied)
                assertEquals(
                    "123",
                    delegate
                        .read("file.txt")
                        .bufferedReader()
                        .use { it.readText() },
                )
            } finally {
                directory.deleteRecursively()
            }
        }

    private fun source(): SyncFile =
        testSyncFile("file.txt", 3, null) {
            "123".byteInputStream()
        }
}
