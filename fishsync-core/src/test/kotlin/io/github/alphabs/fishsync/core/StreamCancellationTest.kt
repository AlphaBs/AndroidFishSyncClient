package io.github.alphabs.fishsync.core

import io.github.alphabs.fishsync.core.filecomparers.FileComparerException
import io.github.alphabs.fishsync.core.filecomparers.LocalFileChecksumComparer
import io.github.alphabs.fishsync.core.files.*
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.*

class StreamCancellationTest {
    @Test
    fun cancellingInflightChecksumClosesBlockedRead() =
        runBlocking<Unit> {
            val file =
                Files
                    .createTempFile("checksum-cancel-", ".bin")
                    .toFile()
            val started = CountDownLatch(1)
            val closed = AtomicBoolean()
            val released = CountDownLatch(1)
            val stream =
                object : InputStream() {
                    override fun read(): Int = error("Use buffered read")

                    override fun read(
                        bytes: ByteArray,
                        offset: Int,
                        length: Int,
                    ): Int {
                        started.countDown()
                        released.await()
                        throw IOException("closed")
                    }

                    override fun close() {
                        closed.set(true)
                        released.countDown()
                    }
                }
            try {
                val target =
                    object : LocalSyncFile(
                        RootedPath.fromFullPath(file.parent, file.path, PathOptions()),
                    ) {
                        override suspend fun openReadStream(): InputStream = stream
                    }
                val source =
                    VirtualSyncFile(
                        RootedPath.fromSubPath("file", PathOptions()),
                        SyncFileMetadata(
                            checksum = SyncFileChecksum("md5", "202cb962ac59075b964b07152d234b70"),
                        ),
                    )
                val task =
                    launch { LocalFileChecksumComparer().areEqual(SyncFilePair(source, target)) }
                assertTrue(withContext(Dispatchers.IO) { started.await(5, TimeUnit.SECONDS) })
                withTimeout(5000) { task.cancelAndJoin() }
                assertTrue(closed.get())
            } finally {
                stream.close()
                file.delete()
            }
        }

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
