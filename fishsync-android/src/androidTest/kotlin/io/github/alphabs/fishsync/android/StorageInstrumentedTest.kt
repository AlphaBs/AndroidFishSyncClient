package io.github.alphabs.fishsync.android

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.alphabs.fishsync.core.ChecksumAlgorithms
import io.github.alphabs.fishsync.core.filecomparers.LocalFileChecksumComparer
import io.github.alphabs.fishsync.core.files.SyncFileChecksum
import io.github.alphabs.fishsync.core.storage.StorageSyncer
import io.github.alphabs.fishsync.core.syncer.FileIntegrityException
import io.github.alphabs.fishsync.core.syncer.SyncAction
import io.github.alphabs.fishsync.core.syncer.SyncCondition
import io.github.alphabs.fishsync.core.syncer.SyncRule
import io.github.alphabs.fishsync.core.syncer.SyncerOptions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageInstrumentedTest {
    private val context get() =
        InstrumentationRegistry
            .getInstrumentation()
            .context
    private val authority = Uri.parse("content://io.github.alphabs.fishsync.test.documents")

    private fun storage() =
        SafSyncStorage(
            context,
            Uri.parse("content://io.github.alphabs.fishsync.test.documents/tree/root"),
        )

    private val rules =
        listOf(
            SyncRule(
                action = SyncAction.FullSync,
                condition = SyncCondition.Always,
                pattern = "**",
                comparer = LocalFileChecksumComparer(),
            ),
        )

    private fun source(
        path: String,
        text: String,
        checksum: SyncFileChecksum? = null,
    ) = testSyncFile(
        path,
        text.length
            .toLong(),
        checksum,
    ) {
        text.byteInputStream()
    }

    @Before fun reset() {
        context.contentResolver
            .call(authority, "resetFixture", null, null)
        context.contentResolver.call(
            authority,
            "grantFixture",
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext.packageName,
            null,
        )
    }

    @Test
    fun safCreateReplaceAndDelete() =
        runBlocking<Unit> {
            val storage = storage()
            val collectionSyncer = StorageSyncer(storage)
            collectionSyncer.compareAndSyncFiles(
                listOf(source("a/file.txt", "123"), source("extra.txt", "extra")),
                SyncerOptions(rules),
            )
            val result =
                collectionSyncer.compareAndSyncFiles(
                    listOf(
                        source(
                            "a/file.txt",
                            "new",
                            SyncFileChecksum(
                                "md5",
                                ChecksumAlgorithms.computeHashAsync("md5", "new".byteInputStream()),
                            ),
                        ),
                    ),
                    SyncerOptions(rules),
                )
            assertEquals(
                "new",
                storage
                    .read("a/file.txt")
                    .bufferedReader()
                    .use { it.readText() },
            )
            assertEquals(
                listOf("extra.txt"),
                result.deletedFiles
                    .map { it.path.subPath },
            )
            assertTrue(
                storage
                    .list()
                    .any { it.path == "extra.txt" },
            )
            collectionSyncer.deleteFiles(result.deletedFiles)
            assertEquals(
                listOf("a/file.txt"),
                storage
                    .list()
                    .map { it.path },
            )
        }

    @Test
    fun safCorruptTransferUsesDirectWriteAndOriginalIntegrityException() =
        runBlocking<Unit> {
            val storage = storage()
            val collectionSyncer = StorageSyncer(storage)
            collectionSyncer.compareAndSyncFiles(
                listOf(source("file", "old"), source("extra", "keep")),
                SyncerOptions(rules),
            )
            try {
                collectionSyncer.compareAndSyncFiles(
                    listOf(source("file", "bad", SyncFileChecksum("md5", "0".repeat(32)))),
                    SyncerOptions(rules),
                )
                fail("Expected failure")
            } catch (
                _: FileIntegrityException,
            ) {
            }
            assertEquals(
                "bad",
                storage
                    .read("file")
                    .bufferedReader()
                    .use { it.readText() },
            )
            assertEquals(
                2,
                storage
                    .list()
                    .size,
            )
        }

    @Test
    fun directWriteDoesNotRequireProviderRename() =
        runBlocking<Unit> {
            val storage = storage()
            val syncer = StorageSyncer(storage)
            syncer.compareAndSyncFiles(listOf(source("file", "old")), SyncerOptions(rules))
            context.contentResolver
                .call(authority, "rejectRename", null, null)
            val changed =
                source(
                    "file",
                    "new",
                    SyncFileChecksum(
                        "md5",
                        ChecksumAlgorithms.computeHashAsync("md5", "new".byteInputStream()),
                    ),
                )

            syncer.compareAndSyncFiles(listOf(changed), SyncerOptions(rules))

            assertEquals(
                "new",
                storage
                    .read("file")
                    .bufferedReader()
                    .use { it.readText() },
            )
            assertEquals(
                listOf("file"),
                storage
                    .list()
                    .map { it.path },
            )
        }

    @Test
    fun providerListingFailureIsNotAnEmptyFolder() =
        runBlocking<Unit> {
            val storage = storage()
            val collectionSyncer = StorageSyncer(storage)
            collectionSyncer.compareAndSyncFiles(
                listOf(source("file", "old")),
                SyncerOptions(rules),
            )
            context.contentResolver
                .call(authority, "rejectQuery", null, null)
            try {
                collectionSyncer.compareAndSyncFiles(emptyList(), SyncerOptions(rules))
                fail("Expected listing failure")
            } catch (
                _: IllegalStateException,
            ) {
            }
            context.contentResolver
                .call(authority, "allowQuery", null, null)
            assertEquals(
                "old",
                storage
                    .read("file")
                    .bufferedReader()
                    .use { it.readText() },
            )
        }

    @Test
    fun appPrivateStorage() =
        runBlocking<Unit> {
            val target =
                InstrumentationRegistry
                    .getInstrumentation()
                    .targetContext
            val storage = AndroidStorage.appFiles(target)
            StorageSyncer(storage).compareAndSyncFiles(
                listOf(source("한글/file.txt", "123")),
                SyncerOptions(rules),
            )
            assertEquals(
                "123",
                storage
                    .read("한글/file.txt")
                    .bufferedReader()
                    .use { it.readText() },
            )
        }
}
