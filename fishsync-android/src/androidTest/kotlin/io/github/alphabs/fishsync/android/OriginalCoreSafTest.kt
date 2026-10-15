package io.github.alphabs.fishsync.android

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.alphabs.fishsync.core.PathOptions
import io.github.alphabs.fishsync.core.RootedPath
import io.github.alphabs.fishsync.core.filecomparers.LocalFileChecksumComparer
import io.github.alphabs.fishsync.core.files.SyncFile
import io.github.alphabs.fishsync.core.files.SyncFileChecksum
import io.github.alphabs.fishsync.core.files.SyncFileMetadata
import io.github.alphabs.fishsync.core.progress.ByteProgress
import io.github.alphabs.fishsync.core.syncer.SyncAction
import io.github.alphabs.fishsync.core.syncer.SyncCondition
import io.github.alphabs.fishsync.core.syncer.SyncRule
import io.github.alphabs.fishsync.core.syncer.SyncerOptions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.OutputStream

@RunWith(AndroidJUnit4::class)
class OriginalCoreSafTest {
    private val context get() =
        InstrumentationRegistry
            .getInstrumentation()
            .context
    private val authority = Uri.parse("content://io.github.alphabs.fishsync.test.documents")
    private val tree = Uri.parse("content://io.github.alphabs.fishsync.test.documents/tree/root")

    @Before
    fun reset() {
        context.contentResolver
            .call(authority, "resetFixture", null, null)
    }

    @Test
    fun safFilesUseOriginalComparerAndKeepUrisOutOfFilesystemPaths() =
        runBlocking<Unit> {
            val source =
                object : SyncFile(RootedPath.fromSubPath("folder/한글.txt", PathOptions())) {
                    override val isReadable = true
                    override val isWritable = false

                    override suspend fun openReadStream() = "123".byteInputStream()

                    override suspend fun openWriteStream(): OutputStream = error("read only")

                    override suspend fun copyTo(
                        destination: OutputStream,
                        progress: (
                            (ByteProgress) -> Unit
                        )?,
                    ) {
                        destination.write("123".toByteArray())
                        progress?.invoke(ByteProgress(0, 3))
                    }
                }
            source.metadata =
                SyncFileMetadata(3, SyncFileChecksum("md5", "202cb962ac59075b964b07152d234b70"))
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
            val syncer = SafSyncer(context, tree)
            val initial = syncer.compareAndSyncFiles(listOf(source), options)
            assertEquals(1, initial.addedFiles.size)
            val file =
                syncer
                    .enumerateSyncFiles()
                    .single() as SafSyncFile
            assertFalse(file.path.isRooted)
            assertEquals(tree, file.treeUri)
            assertEquals(
                "123",
                file
                    .openReadStream()
                    .bufferedReader()
                    .use { it.readText() },
            )
            assertEquals(
                1,
                syncer
                    .compareFiles(listOf(source), options)
                    .identicalFilePairs.size,
            )
            val deleted =
                syncer
                    .compareFiles(emptyList(), options)
                    .deletedFiles
            assertTrue(file.exists)
            syncer.deleteSafFiles(deleted)
            assertFalse(file.exists)
        }
}
