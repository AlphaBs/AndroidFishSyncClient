package io.github.alphabs.fishsync.core

import io.github.alphabs.fishsync.core.filecomparers.CompositeFileComparer
import io.github.alphabs.fishsync.core.filecomparers.FileComparer
import io.github.alphabs.fishsync.core.filecomparers.FileComparerFactory
import io.github.alphabs.fishsync.core.files.Checksum
import io.github.alphabs.fishsync.core.files.FileEntry
import io.github.alphabs.fishsync.core.files.FileSyncStorage
import io.github.alphabs.fishsync.core.files.PendingWrite
import io.github.alphabs.fishsync.core.files.ReadableSyncStorage
import io.github.alphabs.fishsync.core.files.SourceFile
import io.github.alphabs.fishsync.core.files.SyncFilePair
import io.github.alphabs.fishsync.core.files.SyncStorage
import io.github.alphabs.fishsync.core.files.WritableSyncStorage
import io.github.alphabs.fishsync.core.progress.SyncProgress
import io.github.alphabs.fishsync.core.syncer.Comparison
import io.github.alphabs.fishsync.core.syncer.DefaultSyncFilePairSyncer
import io.github.alphabs.fishsync.core.syncer.SyncAction
import io.github.alphabs.fishsync.core.syncer.SyncFailure
import io.github.alphabs.fishsync.core.syncer.SyncFileCollectionSyncer
import io.github.alphabs.fishsync.core.syncer.SyncFilePairSyncer
import io.github.alphabs.fishsync.core.syncer.SyncRule
import io.github.alphabs.fishsync.core.versions.NullVersionManager
import io.github.alphabs.fishsync.core.versions.VersionManager
import io.github.alphabs.fishsync.core.versions.VersionState
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreStructureTest {
    private val rules = listOf(SyncRule(SyncAction.FullSync))

    @Test
    fun readOnlyStorageAndInjectedComparer() =
        runBlocking<Unit> {
            val storage =
                object : ReadableSyncStorage {
                    override val identity = "read-only"

                    override fun list() = listOf(FileEntry("file.txt", 3))

                    override fun read(path: String) = "old".byteInputStream()
                }
            val selectedComparisons = mutableListOf<Comparison>()
            val factory =
                FileComparerFactory { comparison ->
                    selectedComparisons.add(comparison)
                    FileComparer { pair, target ->
                        assertEquals("file.txt", pair.source.path)
                        assertEquals(
                            "old",
                            target
                                .read(pair.target.path)
                                .bufferedReader()
                                .use { it.readText() },
                        )
                        false
                    }
                }
            val collectionSyncer = SyncFileCollectionSyncer(comparerFactory = factory)
            val plan = collectionSyncer.compareFiles(listOf(source()), storage, rules)

            assertEquals(listOf("file.txt"), plan.updated)
            assertEquals(listOf(Comparison.Checksum), selectedComparisons)
        }

    @Test
    fun compositeComparerStopsAtFirstDifference() =
        runBlocking<Unit> {
            val directory =
                Files
                    .createTempDirectory("comparer-")
                    .toFile()
            try {
                val storage = FileSyncStorage(directory)
                val comparer =
                    CompositeFileComparer(
                        listOf(
                            FileComparer { _, _ -> false },
                            FileComparer { _, _ -> error("Must not evaluate after a difference") },
                        ),
                    )

                assertFalse(
                    comparer.areEqual(SyncFilePair(source(), FileEntry("file.txt", 3)), storage),
                )
            } finally {
                directory.deleteRecursively()
            }
        }

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
                val delegate = DefaultSyncFilePairSyncer()
                val syncer =
                    object : SyncFilePairSyncer {
                        override suspend fun sync(
                            pair: SyncFilePair,
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
                val collectionSyncer = SyncFileCollectionSyncer(filePairSyncer = syncer)
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
                    object : SyncStorage by delegate {
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
                        SyncFileCollectionSyncer().compareAndSyncFiles(
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

    @Test
    fun versionManagerImplementationsFollowTheirPolicies() {
        val directory =
            Files
                .createTempDirectory("version-manager-")
                .toFile()
        try {
            val manager: VersionManager = VersionState(directory.resolve("version"))
            assertTrue(manager.isNew("v1"))
            manager.update("v1")
            assertEquals("v1", manager.current())
            assertFalse(manager.isNew("v1"))

            val disabled: VersionManager = NullVersionManager()
            disabled.update("v1")
            assertNull(disabled.current())
            assertTrue(disabled.isNew("v1"))
            assertTrue(disabled.isNew(null))
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun source(): SourceFile =
        SourceFile("file.txt", 3, null) {
            "123".byteInputStream()
        }
}
