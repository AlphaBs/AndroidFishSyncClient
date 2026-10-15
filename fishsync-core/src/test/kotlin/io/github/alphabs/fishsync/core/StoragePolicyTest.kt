package io.github.alphabs.fishsync.core

import io.github.alphabs.fishsync.core.storage.*
import io.github.alphabs.fishsync.core.syncer.SyncAction
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class StoragePolicyTest {
    @Test
    fun excludedAndSizeOnlyFilesAreNeverOpenedForFingerprint() =
        runBlocking<Unit> {
            val storage =
                object : ReadableSyncStorage {
                    override val identity = "unreadable"

                    override fun list() =
                        listOf(FileEntry("excluded", 3), FileEntry("size-only", 3))

                    override fun read(path: String): java.io.InputStream =
                        error("Must not read $path")
                }
            val sources = listOf(SourceFile("size-only", 3, null) { error("Must not open source") })
            val rules =
                listOf(
                    StorageSyncRule(SyncAction.Exclude, "excluded"),
                    StorageSyncRule(SyncAction.FullSync, comparison = Comparison.Size),
                )
            val plan = StorageSyncer().compareFiles(sources, storage, rules)
            assertEquals(listOf("size-only"), plan.identical)
            assertFalse(plan.hasChanges)
        }

    @Test
    fun caseInsensitiveUpdatesProtectPreviewAndKeepTargetSpelling() =
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
                    SourceFile("FILE", 3, Checksum("md5", "202cb962ac59075b964b07152d234b70")) {
                        "123".byteInputStream()
                    }
                val rules = listOf(StorageSyncRule(SyncAction.FullSync))
                val syncer = StorageSyncer()
                val preview = syncer.compareFiles(listOf(source), storage, rules)
                target.writeText("changed")
                assertFailsWith<IllegalArgumentException> {
                    syncer.compareAndSyncFiles(
                        listOf(source),
                        storage,
                        rules,
                        expectedPlan = preview,
                    )
                }
                syncer.compareAndSyncFiles(listOf(source), storage, rules)
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
}
