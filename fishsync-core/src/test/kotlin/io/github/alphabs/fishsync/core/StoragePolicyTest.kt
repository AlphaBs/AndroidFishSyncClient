package io.github.alphabs.fishsync.core

import io.github.alphabs.fishsync.core.storage.*
import io.github.alphabs.fishsync.core.syncer.SyncAction
import kotlinx.coroutines.runBlocking
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
}
