package io.github.alphabs.fishsync.android

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.alphabs.fishsync.core.filecomparers.LocalFileChecksumComparer
import io.github.alphabs.fishsync.core.storage.Checksum
import io.github.alphabs.fishsync.core.storage.SourceFile
import io.github.alphabs.fishsync.core.storage.StorageSyncer
import io.github.alphabs.fishsync.core.storage.SyncFailure
import io.github.alphabs.fishsync.core.storage.hash
import io.github.alphabs.fishsync.core.syncer.SyncAction
import io.github.alphabs.fishsync.core.syncer.SyncCondition
import io.github.alphabs.fishsync.core.syncer.SyncRule
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
        checksum: Checksum? = null,
    ) = SourceFile(
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
            val collectionSyncer = StorageSyncer()
            collectionSyncer.compareAndSyncFiles(
                listOf(source("a/file.txt", "123"), source("extra.txt", "extra")),
                storage,
                rules,
            )
            collectionSyncer.compareAndSyncFiles(
                listOf(source("a/file.txt", "new", Checksum("md5", hash("new".byteInputStream())))),
                storage,
                rules,
            )
            assertEquals(
                "new",
                storage
                    .read("a/file.txt")
                    .bufferedReader()
                    .use { it.readText() },
            )
            assertEquals(
                listOf("a/file.txt"),
                storage
                    .list()
                    .map { it.path },
            )
        }

    @Test
    fun safCorruptTransferPreservesOriginalAndSkipsDelete() =
        runBlocking<Unit> {
            val storage = storage()
            val collectionSyncer = StorageSyncer()
            collectionSyncer.compareAndSyncFiles(
                listOf(source("file", "old"), source("extra", "keep")),
                storage,
                rules,
            )
            try {
                collectionSyncer.compareAndSyncFiles(
                    listOf(source("file", "bad", Checksum("md5", "0".repeat(32)))),
                    storage,
                    rules,
                )
                fail("Expected failure")
            } catch (
                _: SyncFailure,
            ) {
            }
            assertEquals(
                "old",
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
    fun providerRenameFailurePreservesOriginal() =
        runBlocking<Unit> {
            val storage = storage()
            val collectionSyncer = StorageSyncer()
            collectionSyncer.compareAndSyncFiles(listOf(source("file", "old")), storage, rules)
            context.contentResolver
                .call(authority, "rejectRename", null, null)
            try {
                collectionSyncer.compareAndSyncFiles(
                    listOf(source("file", "new", Checksum("md5", hash("new".byteInputStream())))),
                    storage,
                    rules,
                )
                fail("Expected failure")
            } catch (
                _: SyncFailure,
            ) {
            }
            assertEquals(
                "old",
                storage
                    .read("file")
                    .bufferedReader()
                    .use { it.readText() },
            )
        }

    @Test
    fun providerListingFailureIsNotAnEmptyFolder() =
        runBlocking<Unit> {
            val storage = storage()
            val collectionSyncer = StorageSyncer()
            collectionSyncer.compareAndSyncFiles(listOf(source("file", "old")), storage, rules)
            context.contentResolver
                .call(authority, "rejectQuery", null, null)
            try {
                collectionSyncer.compareAndSyncFiles(emptyList(), storage, rules)
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
            StorageSyncer().compareAndSyncFiles(
                listOf(source("한글/file.txt", "123")),
                storage,
                rules,
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
