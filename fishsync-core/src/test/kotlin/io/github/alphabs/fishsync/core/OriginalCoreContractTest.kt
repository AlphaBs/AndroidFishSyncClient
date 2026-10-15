package io.github.alphabs.fishsync.core

import io.github.alphabs.fishsync.core.filecomparers.*
import io.github.alphabs.fishsync.core.files.*
import io.github.alphabs.fishsync.core.progress.*
import io.github.alphabs.fishsync.core.syncer.*
import io.github.alphabs.fishsync.core.versions.*
import kotlinx.coroutines.*
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class OriginalCoreContractTest {
    private val paths = PathOptions()

    @Test
    fun rootedPathsPreserveOriginalNormalization() {
        val path = RootedPath.create("/tmp/root", "a//./b", paths)
        assertEquals("/tmp/root/", path.root)
        assertEquals("a/b", path.subPath)
        assertEquals(
            "/tmp/other/a/b",
            path
                .withRoot("/tmp/other")
                .getFullPath(),
        )
        assertTrue(
            RootedPath
                .create("/tmp/root", "", paths)
                .isDirectory,
        )
        assertFalse(
            RootedPath
                .fromSubPath("a", paths)
                .isRooted,
        )
        assertFailsWith<IllegalArgumentException> { RootedPath.fromSubPath("a/../b", paths) }
        assertEquals("a|b", PathHelper.normalizePath("a::.|b", PathOptions('|', ':')))
    }

    @Test
    fun pathComparerRejectsDuplicatesAndBuildsFilePairs() {
        val source = virtual("A", 3)
        val target = virtual("a", 3)
        val result = SyncPathComparer().comparePaths(listOf(source), listOf(target), paths)
        assertEquals(listOf(SyncFilePair(source, target)), result.duplicatedFiles)
        assertFailsWith<IllegalArgumentException> {
            SyncPathComparer().comparePaths(listOf(source, virtual("a", 4)), emptyList(), paths)
        }
        assertEquals(source, SyncFileCollection(listOf(source)).findFileByPath("A"))
    }

    @Test
    fun metadataComparersKeepErrorPoliciesAndValidateChecksums() =
        runBlocking<Unit> {
            val source = virtual("file", 3)
            val target = virtual("file", 3)
            assertTrue(FileSizeMetadataComparer().areEqual(SyncFilePair(source, target)))
            target.metadata = null
            assertFailsWith<FileComparerException> {
                FileSizeMetadataComparer().areEqual(SyncFilePair(source, target))
            }
            assertFalse(
                FileSizeMetadataComparer(
                    ComparerErrorHandlingModes.ReturnNotEqual,
                ).areEqual(SyncFilePair(source, target)),
            )
            assertTrue(FileChecksumMetadataComparer().areEqual(SyncFilePair(source, target)))
            source.metadata = SyncFileMetadata(3, SyncFileChecksum("md5", "invalid"))
            assertFailsWith<FileComparerException> {
                FileChecksumMetadataComparer(
                    ComparerErrorHandlingModes.ReturnEqual,
                ).areEqual(SyncFilePair(source, target))
            }
        }

    @Test
    fun globComparerSelectsFirstMatchInsteadOfCombiningComparers() =
        runBlocking<Unit> {
            val comparer = CompositeFileComparerWithGlob()
            comparer.add("*.txt", IFileComparer { false })
            comparer.add("**", IFileComparer { true })
            assertFalse(
                comparer.areEqual(SyncFilePair(virtual("file.txt", 1), virtual("file.txt", 1))),
            )
            assertTrue(comparer.areEqual(SyncFilePair(virtual("other", 1), virtual("other", 1))))
        }

    @Test
    fun rulesAcceptDifferentComparerObjects() =
        runBlocking<Unit> {
            val syncer = SyncFileCollectionSyncer(ParallelSyncFilePairSyncer(2), paths)
            val options =
                SyncerOptions(
                    listOf(
                        SyncRule(
                            SyncAction.FullSync,
                            SyncCondition.Always,
                            "*.txt",
                            IFileComparer { false },
                        ),
                        SyncRule(
                            SyncAction.FullSync,
                            SyncCondition.Always,
                            "**",
                            IFileComparer { true },
                        ),
                    ),
                )
            val result =
                syncer.compareFiles(
                    listOf(virtual("a.txt", 3), virtual("b", 3)),
                    listOf(virtual("a.txt", 3), virtual("b", 3)),
                    options,
                )
            assertEquals(
                listOf("a.txt"),
                result.updatedFilePairs
                    .map { it.source.path.subPath },
            )
            assertEquals(
                listOf("b"),
                result.identicalFilePairs
                    .map { it.source.path.subPath },
            )
            assertFailsWith<IllegalArgumentException> {
                syncer.compareFiles(
                    emptyList(),
                    emptyList(),
                    null,
                )
            }
        }

    @Test
    fun localSyncerUsesInheritanceAndRequiresExplicitDeletion() =
        runBlocking<Unit> {
            val directory =
                Files
                    .createTempDirectory("original-local-")
                    .toFile()
            try {
                File(directory, "extra").writeText("keep until deleted")
                val source =
                    object : SyncFile(RootedPath.fromSubPath("file", paths)) {
                        override val isReadable = true
                        override val isWritable = false

                        override suspend fun openReadStream() = "123".byteInputStream()

                        override suspend fun openWriteStream(): java.io.OutputStream =
                            error("read only")

                        override suspend fun copyTo(
                            destination: java.io.OutputStream,
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
                val syncer = LocalSyncer(directory.path, paths, ParallelSyncFilePairSyncer())
                val result =
                    syncer.compareAndSyncFiles(
                        listOf(source),
                        SyncerOptions(listOf(fullRule())),
                    )
                assertEquals("123", File(directory, "file").readText())
                assertTrue(File(directory, "extra").exists())
                syncer.deleteLocalFiles(result.deletedFiles)
                assertFalse(File(directory, "extra").exists())
            } finally {
                directory.deleteRecursively()
            }
        }

    @Test
    fun parallelismIsBoundedAndFailureWaitsForWorkers() =
        runBlocking<Unit> {
            val active = AtomicInteger()
            val peak = AtomicInteger()
            val calls = AtomicInteger()
            val comparer =
                IFileComparer {
                    val count = active.incrementAndGet()
                    peak.updateAndGet { previous -> maxOf(previous, count) }
                    try {
                        delay(20)
                        calls.incrementAndGet()
                        true
                    } finally {
                        active.decrementAndGet()
                    }
                }
            val pairs = (0 until 8).map { SyncFilePair(virtual("$it", 1), virtual("$it", 1)) }
            val syncer = ParallelSyncFilePairSyncer(2)
            assertEquals(
                8,
                syncer
                    .compareFilePairs(pairs, comparer)
                    .identicalFiles.size,
            )
            assertEquals(2, peak.get())
            assertEquals(0, active.get())
            assertFailsWith<IllegalStateException> {
                syncer.compareFilePairs(
                    pairs,
                    IFileComparer { throw IllegalStateException("worker failure") },
                )
            }
            assertEquals(8, calls.get())
        }

    @Test
    fun typedProgressRetainsTotalsAndFileState() {
        val file = virtual("file", 3)
        val progress = SyncFileProgressCollection(listOf(file))
        assertTrue(progress.setEventType(file, FileProgressEventType.StartSync))
        progress.addProgress(file, ByteProgress(3, 1))
        progress.addProgress(file, ByteProgress(0, 2))
        assertEquals(ByteProgress(3, 3), progress.aggregateProgress())
        assertEquals(
            1.0,
            progress
                .aggregateProgress()
                .getRatio(),
        )
        progress.clearProgress()
        assertEquals(ByteProgress(), progress.aggregateProgress())
        assertEquals(
            FileProgressEventType.StartSync,
            progress
                .findItemByPath("file")
                ?.eventType,
        )
    }

    @Test
    fun versionManagerNamesAndAsyncContractMatchOriginal() =
        runBlocking<Unit> {
            val directory =
                Files
                    .createTempDirectory("original-version-")
                    .toFile()
            try {
                val manager: IVersionManager = VersionManager(File(directory, "version").path)
                assertTrue(manager.checkNewVersion(" v1 "))
                manager.updateVersion(" v1 ")
                assertEquals("v1", manager.getCurrentVersion())
                assertFalse(manager.checkNewVersion("v1"))
                assertFailsWith<IllegalArgumentException> { manager.updateVersion("가".repeat(43)) }
                val disabled: IVersionManager = NullVersionManager()
                assertTrue(disabled.checkNewVersion(null))
                assertNull(disabled.getCurrentVersion())
            } finally {
                directory.deleteRecursively()
            }
        }

    private fun fullRule() =
        SyncRule(SyncAction.FullSync, SyncCondition.Always, "**", LocalFileChecksumComparer())

    private fun virtual(
        path: String,
        size: Long,
    ): SyncFile =
        VirtualSyncFile(RootedPath.fromSubPath(path, paths)).apply {
            metadata = SyncFileMetadata(size)
        }
}
