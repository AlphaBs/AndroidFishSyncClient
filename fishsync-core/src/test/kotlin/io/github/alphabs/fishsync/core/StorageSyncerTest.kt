package io.github.alphabs.fishsync.core

import io.github.alphabs.fishsync.core.filecomparers.LocalFileChecksumComparer
import io.github.alphabs.fishsync.core.files.SyncFileChecksum
import io.github.alphabs.fishsync.core.storage.FileSyncStorage
import io.github.alphabs.fishsync.core.storage.StorageSyncer
import io.github.alphabs.fishsync.core.syncer.SyncAction
import io.github.alphabs.fishsync.core.syncer.SyncCondition
import io.github.alphabs.fishsync.core.syncer.SyncRule
import io.github.alphabs.fishsync.core.syncer.SyncerOptions
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class StorageSyncerTest {
    @Test
    fun synchronizationReturnsDeletionCandidatesUntilExplicitDeletion() =
        runBlocking<Unit> {
            val directory =
                Files
                    .createTempDirectory("storage-original-")
                    .toFile()
            try {
                File(directory, "extra").writeText("keep")
                val storage = FileSyncStorage(directory)
                val syncer = StorageSyncer(storage)
                val source =
                    testSyncFile(
                        "folder/file",
                        3,
                        SyncFileChecksum("md5", "202cb962ac59075b964b07152d234b70"),
                    ) { "123".byteInputStream() }
                val options =
                    SyncerOptions(
                        listOf(
                            SyncRule(
                                SyncAction.FullSync,
                                SyncCondition.Always,
                                "**",
                                LocalFileChecksumComparer(),
                            ),
                        ),
                    )

                val result = syncer.compareAndSyncFiles(listOf(source), options)

                assertEquals("123", File(directory, "folder/file").readText())
                assertEquals(
                    listOf("extra"),
                    result.deletedFiles
                        .map { it.path.subPath },
                )
                assertEquals("keep", File(directory, "extra").readText())
                assertEquals(1, result.updatedFilePairs.size)
                syncer.deleteFiles(result.deletedFiles)
                assertFalse(File(directory, "extra").exists())
                assertEquals(
                    1,
                    syncer
                        .compareFiles(listOf(source), options)
                        .identicalFilePairs.size,
                )
            } finally {
                directory.deleteRecursively()
            }
        }

    @Test
    fun explicitDeletionRejectsFilesFromAnotherStorage() {
        val first =
            Files
                .createTempDirectory("storage-first-")
                .toFile()
        val second =
            Files
                .createTempDirectory("storage-second-")
                .toFile()
        try {
            File(second, "file").writeText("keep")
            val other = StorageSyncer(FileSyncStorage(second)).enumerateSyncFiles()
            assertFailsWith<IllegalArgumentException> {
                StorageSyncer(FileSyncStorage(first)).deleteFiles(other)
            }
            assertEquals("keep", File(second, "file").readText())
        } finally {
            first.deleteRecursively()
            second.deleteRecursively()
        }
    }
}
