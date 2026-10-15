package io.github.alphabs.fishsync.core

import io.github.alphabs.fishsync.core.filecomparers.FileComparerException
import io.github.alphabs.fishsync.core.filecomparers.LocalFileChecksumComparer
import io.github.alphabs.fishsync.core.files.LocalSyncFile
import io.github.alphabs.fishsync.core.files.SyncFileChecksum
import io.github.alphabs.fishsync.core.files.SyncFileMetadata
import io.github.alphabs.fishsync.core.files.SyncFilePair
import io.github.alphabs.fishsync.core.files.VirtualSyncFile
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ChecksumMetadataTest {
    @Test
    fun defaultChecksumValueIsValidatedByComparerBeforeMissingTarget() =
        runBlocking<Unit> {
            val directory =
                Files
                    .createTempDirectory("default-checksum-")
                    .toFile()
            try {
                val source =
                    VirtualSyncFile(
                        RootedPath.fromSubPath("missing", PathOptions()),
                        SyncFileMetadata(checksum = SyncFileChecksum()),
                    )
                val target =
                    LocalSyncFile(RootedPath.create(directory.path, "missing", PathOptions()))
                assertFailsWith<FileComparerException> {
                    LocalFileChecksumComparer().areEqual(SyncFilePair(source, target))
                }
            } finally {
                directory.deleteRecursively()
            }
        }
}
