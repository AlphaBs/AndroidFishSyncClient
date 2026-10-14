package io.github.alphabs.fishsync.core

import io.github.alphabs.fishsync.core.files.Checksum
import io.github.alphabs.fishsync.core.files.FileSyncStorage
import io.github.alphabs.fishsync.core.files.SourceFile
import io.github.alphabs.fishsync.core.syncer.Comparison
import io.github.alphabs.fishsync.core.syncer.IntegrityException
import io.github.alphabs.fishsync.core.syncer.SyncAction
import io.github.alphabs.fishsync.core.syncer.SyncCondition
import io.github.alphabs.fishsync.core.syncer.SyncContext
import io.github.alphabs.fishsync.core.syncer.SyncEngine
import io.github.alphabs.fishsync.core.syncer.SyncFailure
import io.github.alphabs.fishsync.core.syncer.SyncRule
import io.github.alphabs.fishsync.core.syncer.glob
import io.github.alphabs.fishsync.core.versions.VersionState
import kotlinx.coroutines.*
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class CoreTest {
    private fun root() =
        Files
            .createTempDirectory("fishsync-test-")
            .toFile()

    private fun source(
        path: String,
        text: String,
        checksum: Checksum? = null,
    ) = SourceFile(
        path,
        text
            .toByteArray()
            .size
            .toLong(),
        checksum,
    ) {
        text.byteInputStream()
    }

    private val full = listOf(SyncRule(SyncAction.FullSync))

    @Test
    fun checksumContract() =
        runBlocking<Unit> {
            assertEquals("202cb962ac59075b964b07152d234b70", hash("123".byteInputStream()))
            assertEquals(
                "40bd001563085fc35165329ea1ff5c5ecbdbbeef",
                hash("123".byteInputStream(), "sha1"),
            )
            for (invalid in listOf(
                "",
                "g".repeat(32),
                "Ａ".repeat(32),
                "a".repeat(31),
            )) {
                assertFailsWith<IllegalArgumentException> {
                    Checksum("md5", invalid)
                }
            }
            assertFailsWith<IllegalStateException> { Checksum("MD5", "a".repeat(32)) }
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
        assertTrue(glob("**/*.txt").matches("file.txt"))
        assertTrue(glob("**/*.txt").matches("a/b/file.txt"))
        assertFalse(glob("*.txt").matches("a/file.txt"))
        assertTrue(glob("file[0-9]?.txt").matches("file2x.txt"))
        assertTrue(glob("**/config.*", true).matches("A/CONFIG.JSON"))
        assertFails { glob("[broken") }
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
                        SyncRule(SyncAction.Exclude, "keep.*", SyncCondition.OnNewVersion),
                        SyncRule(SyncAction.InstallOnly, "keep.*"),
                        SyncRule(SyncAction.FullSync),
                    )
                val sources = listOf(source("keep.txt", "new"), source("add.txt", "new"))
                val plan = SyncEngine().compare(sources, storage, rules)
                assertEquals(listOf("add.txt"), plan.added)
                assertTrue(
                    plan.identical
                        .isEmpty(),
                )
                assertEquals(listOf("delete.txt"), plan.deleted)
                assertTrue(
                    SyncEngine()
                        .compare(
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
                    SyncEngine().pull(
                        listOf(source("new.txt", "new")),
                        FileSyncStorage(dir),
                        listOf(SyncRule(SyncAction.UpdateOnly)),
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
                    SyncEngine().pull(
                        listOf(source("x", "x")),
                        FileSyncStorage(dir),
                        listOf(SyncRule(SyncAction.FullSync, "other")),
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
                        SyncEngine().pull(
                            listOf(source("target.txt", "bad", Checksum("md5", "0".repeat(32)))),
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
                    SourceFile("target", 99, null) { throw CancellationException("cancel") }
                assertFailsWith<CancellationException> {
                    SyncEngine().pull(
                        listOf(source),
                        FileSyncStorage(dir),
                        listOf(SyncRule(SyncAction.FullSync, comparison = Comparison.Size)),
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
                    source("a/b.txt", "123", Checksum("md5", "202CB962AC59075B964B07152D234B70"))
                val result = SyncEngine().pull(listOf(source), FileSyncStorage(dir), full)
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
                    SyncEngine().compare(
                        listOf(source("file", "x")),
                        FileSyncStorage(dir),
                        listOf(SyncRule(SyncAction.FullSync, comparison = Comparison.Checksum)),
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
                    SyncEngine(
                        caseInsensitive = true,
                    ).compare(
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
                        source("file", "123", Checksum("md5", "202cb962ac59075b964b07152d234b70")),
                    )
                val preview = SyncEngine().compare(sources, storage, full)
                File(dir, "file").writeText("new")
                assertFailsWith<IllegalArgumentException> {
                    SyncEngine().pull(sources, storage, full, expectedPlan = preview)
                }
                assertEquals("new", File(dir, "file").readText())
            } finally {
                dir.deleteRecursively()
            }
        }

    @Test
    fun versionUtf8Limit() {
        val dir = root()
        try {
            val state = VersionState(File(dir, "state"))
            assertNull(state.current())
            state.update(" v1 ")
            assertEquals("v1", state.current())
            assertFalse(state.isNew(" v1 "))
            assertFails { state.update("가".repeat(43)) }
            assertEquals("v1", state.current())
        } finally {
            dir.deleteRecursively()
        }
    }
}
