package io.github.alphabs.fishsync.core

import io.github.alphabs.fishsync.core.storage.Checksum
import io.github.alphabs.fishsync.core.storage.Comparison
import io.github.alphabs.fishsync.core.storage.DefaultStorageFilePairSyncer
import io.github.alphabs.fishsync.core.storage.FileEntry
import io.github.alphabs.fishsync.core.storage.FileSyncStorage
import io.github.alphabs.fishsync.core.storage.PendingWrite
import io.github.alphabs.fishsync.core.storage.ReadableSyncStorage
import io.github.alphabs.fishsync.core.storage.SourceFile
import io.github.alphabs.fishsync.core.storage.StorageFilePair
import io.github.alphabs.fishsync.core.storage.StorageFilePairSyncer
import io.github.alphabs.fishsync.core.storage.StorageSyncRule
import io.github.alphabs.fishsync.core.storage.StorageSyncer
import io.github.alphabs.fishsync.core.storage.SyncFailure
import io.github.alphabs.fishsync.core.storage.SyncProgress
import io.github.alphabs.fishsync.core.storage.SyncStorage
import io.github.alphabs.fishsync.core.storage.WritableSyncStorage
import io.github.alphabs.fishsync.core.storage.versions.VersionState
import io.github.alphabs.fishsync.core.storage.versions.VersionStore
import io.github.alphabs.fishsync.core.syncer.SyncAction
import io.github.alphabs.fishsync.core.versions.IVersionManager
import io.github.alphabs.fishsync.core.versions.NullVersionManager
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
    private val rules = listOf(StorageSyncRule(SyncAction.FullSync))

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

    @Test
    fun versionManagerImplementationsFollowTheirPolicies() =
        runBlocking<Unit> {
            val directory =
                Files
                    .createTempDirectory("version-manager-")
                    .toFile()
            try {
                val manager: VersionStore = VersionState(directory.resolve("version"))
                assertTrue(manager.isNew("v1"))
                manager.update("v1")
                assertEquals("v1", manager.current())
                assertFalse(manager.isNew("v1"))

                val disabled: io.github.alphabs.fishsync.core.versions.IVersionManager =
                    NullVersionManager()
                disabled.updateVersion("v1")
                assertNull(disabled.getCurrentVersion())
                assertTrue(disabled.checkNewVersion("v1"))
                assertTrue(disabled.checkNewVersion(null))
            } finally {
                directory.deleteRecursively()
            }
        }

    private fun source(): SourceFile =
        SourceFile("file.txt", 3, null) {
            "123".byteInputStream()
        }
}
