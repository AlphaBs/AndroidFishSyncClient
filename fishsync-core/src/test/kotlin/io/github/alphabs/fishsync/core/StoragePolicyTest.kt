package io.github.alphabs.fishsync.core

import io.github.alphabs.fishsync.core.filecomparers.LocalFileChecksumComparer
import io.github.alphabs.fishsync.core.filecomparers.LocalFileSizeComparer
import io.github.alphabs.fishsync.core.files.SyncFileChecksum
import io.github.alphabs.fishsync.core.storage.*
import io.github.alphabs.fishsync.core.syncer.SyncAction
import io.github.alphabs.fishsync.core.syncer.SyncCondition
import io.github.alphabs.fishsync.core.syncer.SyncRule
import io.github.alphabs.fishsync.core.syncer.SyncerOptions
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class StoragePolicyTest {
    @Test
    fun excludedAndSizeOnlyFilesAreNotOpenedForComparison() =
        runBlocking<Unit> {
            val storage =
                object : ReadableSyncStorage {
                    override val identity = "unreadable"

                    override fun list() =
                        listOf(FileEntry("excluded", 3), FileEntry("size-only", 3))

                    override fun read(path: String): java.io.InputStream =
                        error("Must not read $path")
                }
            val sources =
                listOf(testSyncFile("size-only", 3, null) { error("Must not open source") })
            val rules =
                listOf(
                    SyncRule(
                        action = SyncAction.Exclude,
                        condition = SyncCondition.Always,
                        pattern = "excluded",
                        comparer = LocalFileChecksumComparer(),
                    ),
                    SyncRule(
                        action = SyncAction.FullSync,
                        condition = SyncCondition.Always,
                        pattern = "**",
                        comparer = LocalFileSizeComparer(),
                    ),
                )
            val plan = StorageSyncer(storage).compareFiles(sources, SyncerOptions(rules))
            assertEquals(
                listOf("size-only"),
                plan.identicalFilePairs
                    .map { it.source.path.subPath }
                    .sorted(),
            )
            assertTrue(
                plan.addedFiles
                    .isEmpty(),
            )
            assertTrue(
                plan.updatedFilePairs
                    .isEmpty(),
            )
            assertTrue(
                plan.deletedFiles
                    .isEmpty(),
            )
        }

    @Test
    fun caseInsensitiveUpdatesKeepTargetSpelling() =
        runBlocking<Unit> {
            val directory =
                Files
                    .createTempDirectory("case-update-")
                    .toFile()
            try {
                val target = File(directory, "file")
                target.writeText("old")
                val storage = FileSyncStorage(directory)
                val source =
                    testSyncFile(
                        "FILE",
                        3,
                        SyncFileChecksum("md5", "202cb962ac59075b964b07152d234b70"),
                    ) {
                        "123".byteInputStream()
                    }
                val rules =
                    listOf(
                        SyncRule(
                            action = SyncAction.FullSync,
                            condition = SyncCondition.Always,
                            pattern = "**",
                            comparer = LocalFileChecksumComparer(),
                        ),
                    )
                val syncer = StorageSyncer(storage)
                syncer.compareAndSyncFiles(listOf(source), SyncerOptions(rules))
                assertEquals("123", target.readText())
                assertEquals(
                    listOf("file"),
                    storage
                        .list()
                        .map { it.path },
                )
            } finally {
                directory.deleteRecursively()
            }
        }

    @Test
    fun unicodeUpdateUsesStandardComparisonAndKeepsExistingFileName() =
        runBlocking<Unit> {
            val directory =
                Files
                    .createTempDirectory("unicode-update-")
                    .toFile()
            try {
                val target = File(directory, "ı.txt")
                target.writeText("old")
                val source =
                    testSyncFile(
                        "I.txt",
                        3,
                        SyncFileChecksum("md5", "202cb962ac59075b964b07152d234b70"),
                    ) { "123".byteInputStream() }
                val rules =
                    listOf(
                        SyncRule(
                            SyncAction.FullSync,
                            SyncCondition.Always,
                            "**",
                            LocalFileChecksumComparer(),
                        ),
                    )
                val storage = FileSyncStorage(directory)
                val syncer = StorageSyncer(storage)
                val plan = syncer.compareFiles(listOf(source), SyncerOptions(rules))
                assertEquals(
                    listOf("I.txt"),
                    plan.updatedFilePairs
                        .map { it.source.path.subPath }
                        .sorted(),
                )
                assertTrue(
                    plan.addedFiles
                        .map { it.path.subPath }
                        .sorted()
                        .isEmpty(),
                )
                assertTrue(
                    plan.deletedFiles
                        .map { it.path.subPath }
                        .sorted()
                        .isEmpty(),
                )
                syncer.compareAndSyncFiles(listOf(source), SyncerOptions(rules))
                assertEquals("123", target.readText())
                assertEquals(
                    listOf("ı.txt"),
                    storage
                        .list()
                        .map { it.path },
                )
            } finally {
                directory.deleteRecursively()
            }
        }
}
