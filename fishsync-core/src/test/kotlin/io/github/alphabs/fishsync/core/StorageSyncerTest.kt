package io.github.alphabs.fishsync.core

import io.github.alphabs.fishsync.core.filecomparers.ChecksumMetadataValidator
import io.github.alphabs.fishsync.core.filecomparers.FileComparerException
import io.github.alphabs.fishsync.core.filecomparers.LocalFileChecksumComparer
import io.github.alphabs.fishsync.core.filecomparers.LocalFileSizeComparer
import io.github.alphabs.fishsync.core.files.SyncFileChecksum
import io.github.alphabs.fishsync.core.internals.GlobPattern
import io.github.alphabs.fishsync.core.storage.FileSyncStorage
import io.github.alphabs.fishsync.core.storage.IntegrityException
import io.github.alphabs.fishsync.core.storage.StorageSyncer
import io.github.alphabs.fishsync.core.storage.SyncFailure
import io.github.alphabs.fishsync.core.storage.relativePath
import io.github.alphabs.fishsync.core.syncer.SyncAction
import io.github.alphabs.fishsync.core.syncer.SyncCondition
import io.github.alphabs.fishsync.core.syncer.SyncContext
import io.github.alphabs.fishsync.core.syncer.SyncRule
import kotlinx.coroutines.*
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class StorageSyncerTest {
    private fun root() =
        Files
            .createTempDirectory("fishsync-test-")
            .toFile()

    private fun source(
        path: String,
        text: String,
        checksum: SyncFileChecksum? = null,
    ) = testSyncFile(
        path,
        text
            .toByteArray()
            .size
            .toLong(),
        checksum,
    ) {
        text.byteInputStream()
    }

    private val full =
        listOf(
            SyncRule(
                action = SyncAction.FullSync,
                condition = SyncCondition.Always,
                pattern = "**",
                comparer = LocalFileChecksumComparer(),
            ),
        )

    @Test
    fun checksumContract() =
        runBlocking<Unit> {
            assertEquals(
                "202cb962ac59075b964b07152d234b70",
                ChecksumAlgorithms.computeHashAsync("md5", "123".byteInputStream()),
            )
            assertEquals(
                "40bd001563085fc35165329ea1ff5c5ecbdbbeef",
                ChecksumAlgorithms.computeHashAsync("sha1", "123".byteInputStream()),
            )
            for (invalid in listOf(
                "",
                "g".repeat(32),
                "Ａ".repeat(32),
                "a".repeat(31),
            )) {
                assertFailsWith<FileComparerException> {
                    ChecksumMetadataValidator.validate(SyncFileChecksum("md5", invalid), "source")
                }
            }
            assertFailsWith<FileComparerException> {
                ChecksumMetadataValidator.validate(
                    SyncFileChecksum("MD5", "a".repeat(32)),
                    "source",
                )
            }
        }

    @Test
    fun pathsCannotEscape() {
        for (path in listOf(
            "",
            "/tmp/file",
            "../file",
            "a/../b",
            "a//b",
            "a/./b",
            "C:/file",
            "a/",
        )) {
            assertFailsWith<IllegalArgumentException> {
                relativePath(path)
            }
        }
        assertEquals("folder/file", relativePath("folder\\file"))
    }

    @Test
    fun globContract() {
        assertTrue(GlobPattern("**/*.txt").matches("file.txt"))
        assertTrue(GlobPattern("**/*.txt").matches("a/b/file.txt"))
        assertFalse(GlobPattern("*.txt").matches("a/file.txt"))
        assertTrue(GlobPattern("file??.txt").matches("file2x.txt"))
        assertTrue(GlobPattern("**/config.*", true).matches("A/CONFIG.JSON"))
        assertFalse(GlobPattern("[broken").matches("b"))
    }

    @Test
    fun orderedRulesAndConditions() =
        runBlocking<Unit> {
            val dir = root()
            try {
                val storage = FileSyncStorage(dir)
                File(dir, "keep.txt").writeText("old")
                File(dir, "delete.txt").writeText("old")
                val rules =
                    listOf(
                        SyncRule(
                            action = SyncAction.Exclude,
                            condition = SyncCondition.OnNewVersion,
                            pattern = "keep.*",
                            comparer = LocalFileChecksumComparer(),
                        ),
                        SyncRule(
                            action = SyncAction.InstallOnly,
                            condition = SyncCondition.Always,
                            pattern = "keep.*",
                            comparer = LocalFileChecksumComparer(),
                        ),
                        SyncRule(
                            action = SyncAction.FullSync,
                            condition = SyncCondition.Always,
                            pattern = "**",
                            comparer = LocalFileChecksumComparer(),
                        ),
                    )
                val sources = listOf(source("keep.txt", "new"), source("add.txt", "new"))
                val plan = StorageSyncer().compareFiles(sources, storage, rules)
                assertEquals(listOf("add.txt"), plan.added)
                assertTrue(
                    plan.identical
                        .isEmpty(),
                )
                assertEquals(listOf("delete.txt"), plan.deleted)
                assertTrue(
                    StorageSyncer()
                        .compareFiles(
                            sources,
                            storage,
                            rules,
                            SyncContext(isForced = true),
                        ).identical
                        .isEmpty(),
                )
            } finally {
                dir.deleteRecursively()
            }
        }

    @Test
    fun updateOnlyPreservesTargetOnlyFiles() =
        runBlocking<Unit> {
            val dir = root()
            try {
                File(dir, "local.txt").writeText("keep")
                val result =
                    StorageSyncer().compareAndSyncFiles(
                        listOf(source("new.txt", "new")),
                        FileSyncStorage(dir),
                        listOf(
                            SyncRule(
                                action = SyncAction.UpdateOnly,
                                condition = SyncCondition.Always,
                                pattern = "**",
                                comparer = LocalFileChecksumComparer(),
                            ),
                        ),
                    )
                assertTrue(
                    result.deleted
                        .isEmpty(),
                )
                assertEquals("keep", File(dir, "local.txt").readText())
            } finally {
                dir.deleteRecursively()
            }
        }

    @Test
    fun invalidRulesFailBeforeWrites() =
        runBlocking<Unit> {
            val dir = root()
            try {
                assertFails {
                    StorageSyncer().compareAndSyncFiles(
                        listOf(source("x", "x")),
                        FileSyncStorage(dir),
                        listOf(
                            SyncRule(
                                action = SyncAction.FullSync,
                                condition = SyncCondition.Always,
                                pattern = "other",
                                comparer = LocalFileChecksumComparer(),
                            ),
                        ),
                    )
                }
                assertTrue(
                    dir
                        .listFiles()!!
                        .isEmpty(),
                )
            } finally {
                dir.deleteRecursively()
            }
        }

    @Test
    fun failedVerificationPreservesExistingAndSkipsDeletion() =
        runBlocking<Unit> {
            val dir = root()
            try {
                File(dir, "target.txt").writeText("original")
                File(dir, "extra.txt").writeText("preserve")
                val failure =
                    assertFailsWith<SyncFailure> {
                        StorageSyncer().compareAndSyncFiles(
                            listOf(
                                source(
                                    "target.txt",
                                    "bad",
                                    SyncFileChecksum("md5", "0".repeat(32)),
                                ),
                            ),
                            FileSyncStorage(dir),
                            full,
                        )
                    }
                assertTrue(failure.cause is IntegrityException)
                assertTrue(
                    failure.applied
                        .isEmpty(),
                )
                assertEquals("original", File(dir, "target.txt").readText())
                assertEquals("preserve", File(dir, "extra.txt").readText())
            } finally {
                dir.deleteRecursively()
            }
        }

    @Test
    fun cancellationPreservesExisting() =
        runBlocking<Unit> {
            val dir = root()
            try {
                File(dir, "target").writeText("original")
                val source =
                    testSyncFile("target", 99, null) { throw CancellationException("cancel") }
                assertFailsWith<CancellationException> {
                    StorageSyncer().compareAndSyncFiles(
                        listOf(source),
                        FileSyncStorage(dir),
                        listOf(
                            SyncRule(
                                action = SyncAction.FullSync,
                                condition = SyncCondition.Always,
                                pattern = "**",
                                comparer = LocalFileSizeComparer(),
                            ),
                        ),
                    )
                }
                assertEquals("original", File(dir, "target").readText())
            } finally {
                dir.deleteRecursively()
            }
        }

    @Test
    fun checkedCopyAndDelete() =
        runBlocking<Unit> {
            val dir = root()
            try {
                File(dir, "remove").writeText("old")
                val source =
                    source(
                        "a/b.txt",
                        "123",
                        SyncFileChecksum("md5", "202CB962AC59075B964B07152D234B70"),
                    )
                val result =
                    StorageSyncer().compareAndSyncFiles(
                        listOf(source),
                        FileSyncStorage(dir),
                        full,
                    )
                assertEquals(listOf("a/b.txt"), result.applied)
                assertEquals(listOf("remove"), result.deleted)
                assertEquals("123", File(dir, "a/b.txt").readText())
            } finally {
                dir.deleteRecursively()
            }
        }

    @Test
    fun missingChecksumPreservesExistingInChecksumMode() =
        runBlocking<Unit> {
            val dir = root()
            try {
                File(dir, "file").writeText("existing")
                val plan =
                    StorageSyncer().compareFiles(
                        listOf(source("file", "x")),
                        FileSyncStorage(dir),
                        listOf(
                            SyncRule(
                                action = SyncAction.FullSync,
                                condition = SyncCondition.Always,
                                pattern = "**",
                                comparer = LocalFileChecksumComparer(),
                            ),
                        ),
                    )
                assertEquals(listOf("file"), plan.identical)
            } finally {
                dir.deleteRecursively()
            }
        }

    @Test
    fun duplicateAndLinkRejection() =
        runBlocking<Unit> {
            val dir = root()
            val outside = root()
            try {
                assertFails {
                    StorageSyncer(
                        caseInsensitive = true,
                    ).compareFiles(
                        listOf(source("A", "x"), source("a", "y")),
                        FileSyncStorage(dir),
                        full,
                    )
                }
                Files.createSymbolicLink(File(dir, "link").toPath(), outside.toPath())
                assertFails { FileSyncStorage(dir).list() }
            } finally {
                File(dir, "link").delete()
                dir.deleteRecursively()
                outside.deleteRecursively()
            }
        }

    @Test
    fun modifiedContentInvalidatesPreview() =
        runBlocking<Unit> {
            val dir = root()
            try {
                File(dir, "file").writeText("old")
                val storage = FileSyncStorage(dir)
                val sources =
                    listOf(
                        source(
                            "file",
                            "123",
                            SyncFileChecksum("md5", "202cb962ac59075b964b07152d234b70"),
                        ),
                    )
                val preview = StorageSyncer().compareFiles(sources, storage, full)
                File(dir, "file").writeText("new")
                assertFailsWith<IllegalArgumentException> {
                    StorageSyncer().compareAndSyncFiles(
                        sources,
                        storage,
                        full,
                        expectedPlan = preview,
                    )
                }
                assertEquals("new", File(dir, "file").readText())
            } finally {
                dir.deleteRecursively()
            }
        }
}
