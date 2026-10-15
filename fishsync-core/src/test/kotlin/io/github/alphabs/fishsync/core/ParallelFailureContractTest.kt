package io.github.alphabs.fishsync.core

import io.github.alphabs.fishsync.core.files.SyncFile
import io.github.alphabs.fishsync.core.files.SyncFilePair
import io.github.alphabs.fishsync.core.progress.ByteProgress
import io.github.alphabs.fishsync.core.progress.FileProgressEventType
import io.github.alphabs.fishsync.core.syncer.ParallelSyncFilePairSyncer
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class ParallelFailureContractTest {
    @Test
    fun failureAwaitsCopiesThatIgnoreCancellation() =
        runBlocking<Unit> {
            for (mode in listOf("enumeration", "progress", "cancellation")) {
                supervisorScope {
                    val started = CountDownLatch(1)
                    val triggered = CompletableDeferred<Unit>()
                    val release = CompletableDeferred<Unit>()
                    val completed = AtomicInteger()
                    val path = RootedPath.fromSubPath("file", PathOptions())
                    val source =
                        object : SyncFile(path) {
                            override val isReadable = true
                            override val isWritable = false

                            override suspend fun openReadStream(): InputStream = error("unused")

                            override suspend fun openWriteStream(): OutputStream = error("unused")

                            override suspend fun copyTo(
                                destination: OutputStream,
                                progress: (
                                    (ByteProgress) -> Unit
                                )?,
                            ) {
                                started.countDown()
                                withContext(NonCancellable) {
                                    release.await()
                                    destination.write(byteArrayOf(1, 2, 3))
                                    completed.incrementAndGet()
                                }
                            }
                        }
                    val target =
                        object : SyncFile(path) {
                            override val isReadable = false
                            override val isWritable = true

                            override suspend fun openReadStream(): InputStream = error("unused")

                            override suspend fun openWriteStream(): OutputStream =
                                ByteArrayOutputStream()

                            override suspend fun copyTo(
                                destination: OutputStream,
                                progress: (
                                    (ByteProgress) -> Unit
                                )?,
                            ) = error("unused")
                        }
                    val pair = SyncFilePair(source, target)
                    lateinit var operation: Deferred<Unit>
                    val pairs =
                        sequence {
                            yield(pair)
                            check(started.await(5, TimeUnit.SECONDS))
                            if (mode == "enumeration") {
                                triggered.complete(Unit)
                                throw IllegalStateException("enumeration failed")
                            }
                            if (mode == "cancellation") {
                                operation.cancel()
                                triggered.complete(Unit)
                            }
                            yield(pair)
                        }.asIterable()
                    operation =
                        async(Dispatchers.Default, start = CoroutineStart.LAZY) {
                            ParallelSyncFilePairSyncer(1).syncFilePairs(pairs, fileProgress = {
                                if (mode == "progress" &&
                                    it.eventType == FileProgressEventType.Queue &&
                                    it.totalFiles == 2
                                ) {
                                    triggered.complete(Unit)
                                    throw IllegalStateException("progress failed")
                                }
                            })
                        }
                    operation.start()
                    try {
                        withTimeout(5000) { triggered.await() }
                        delay(100)
                        assertFalse(operation.isCompleted, mode)
                    } finally {
                        release.complete(Unit)
                    }
                    if (mode == "cancellation") {
                        assertFailsWith<CancellationException> {
                            withTimeout(
                                5000,
                            ) { operation.await() }
                        }
                    } else {
                        val failure =
                            assertFailsWith<IllegalStateException> {
                                withTimeout(
                                    5000,
                                ) { operation.await() }
                            }
                        assertEquals("$mode failed", failure.message)
                    }
                    assertEquals(1, completed.get(), mode)
                }
            }
        }
}
