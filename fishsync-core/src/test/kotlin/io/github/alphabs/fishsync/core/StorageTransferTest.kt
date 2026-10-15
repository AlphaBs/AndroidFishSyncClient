package io.github.alphabs.fishsync.core

import io.github.alphabs.fishsync.core.filecomparers.FileComparer
import io.github.alphabs.fishsync.core.files.SyncFileChecksum
import io.github.alphabs.fishsync.core.storage.FileSyncStorage
import io.github.alphabs.fishsync.core.storage.StorageSyncer
import io.github.alphabs.fishsync.core.syncer.ParallelSyncFilePairSyncer
import io.github.alphabs.fishsync.core.syncer.SyncAction
import io.github.alphabs.fishsync.core.syncer.SyncCondition
import io.github.alphabs.fishsync.core.syncer.SyncRule
import io.github.alphabs.fishsync.core.syncer.SyncerOptions
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import kotlin.test.*

class StorageTransferTest {
    @Test
    fun transferVerificationUsesOnlyTheConfiguredComparer() =
        runBlocking<Unit> {
            val directory =
                Files
                    .createTempDirectory("storage-comparer-")
                    .toFile()
            try {
                val target = File(directory, "file")
                target.writeText("old")
                var comparisons = 0
                val comparer =
                    FileComparer { pair ->
                        comparisons++
                        pair.target
                            .openReadStream()
                            .bufferedReader()
                            .use { it.readText() == "new" }
                    }
                // 규칙이 메타데이터 검증을 요구하지 않으면 별도 크기·체크섬 검사를 하지 않는다.
                val source =
                    testSyncFile("file", 99, SyncFileChecksum("md5", "0".repeat(32))) {
                        "new".byteInputStream()
                    }
                val options =
                    SyncerOptions(
                        listOf(
                            SyncRule(SyncAction.FullSync, SyncCondition.Always, "**", comparer),
                        ),
                    )
                StorageSyncer(
                    FileSyncStorage(directory),
                    pairSyncer = ParallelSyncFilePairSyncer(1),
                ).compareAndSyncFiles(listOf(source), options)

                assertEquals(2, comparisons)
                assertEquals("new", target.readText())
                assertEquals(
                    listOf("file"),
                    directory
                        .list()!!
                        .toList(),
                )
            } finally {
                directory.deleteRecursively()
            }
        }

    @Test
    fun failedDirectWritePropagatesOriginalExceptionAndKeepsDeletionCandidates() =
        runBlocking<Unit> {
            val directory =
                Files
                    .createTempDirectory("storage-failure-")
                    .toFile()
            try {
                val target = File(directory, "file")
                target.writeText("original")
                File(directory, "extra").writeText("keep")
                val failure = IOException("Source read failed")
                val source =
                    testSyncFile("file", 3, null) {
                        object : InputStream() {
                            private var first = true

                            override fun read(): Int = throw failure

                            override fun read(
                                bytes: ByteArray,
                                offset: Int,
                                length: Int,
                            ): Int {
                                if (!first) throw failure
                                first = false
                                "new".toByteArray().copyInto(bytes, offset)
                                return 3
                            }
                        }
                    }
                val options =
                    SyncerOptions(
                        listOf(
                            SyncRule(
                                SyncAction.FullSync,
                                SyncCondition.Always,
                                "**",
                                FileComparer { false },
                            ),
                        ),
                    )
                val actual =
                    assertFailsWith<IOException> {
                        StorageSyncer(
                            FileSyncStorage(directory),
                        ).compareAndSyncFiles(listOf(source), options)
                    }

                assertEquals(failure.message, actual.message)
                assertEquals("new", target.readText())
                assertEquals("keep", File(directory, "extra").readText())
                assertEquals(
                    listOf("extra", "file"),
                    directory
                        .list()!!
                        .sorted(),
                )
            } finally {
                directory.deleteRecursively()
            }
        }
}
