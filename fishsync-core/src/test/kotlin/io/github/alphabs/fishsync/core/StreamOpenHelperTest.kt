package io.github.alphabs.fishsync.core

import io.github.alphabs.fishsync.core.internals.StreamOpenHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StreamOpenHelperTest {
    @Test
    fun cancellationBeforeReadStreamDeliveryClosesStream() {
        var closed = false
        val stream =
            object : ByteArrayInputStream(byteArrayOf(1)) {
                override fun close() {
                    closed = true
                }
            }

        cancelBeforeDelivery(stream)

        assertTrue(closed)
    }

    @Test
    fun cancellationBeforeWriteStreamDeliveryClosesStream() {
        var closed = false
        val stream =
            object : ByteArrayOutputStream() {
                override fun close() {
                    closed = true
                }
            }

        cancelBeforeDelivery(stream)

        assertTrue(closed)
    }

    @Test
    fun closeFailureDoesNotReplaceCancellation() {
        val closeFailure = IOException("close failed")
        val stream = Closeable { throw closeFailure }

        val failure = cancelBeforeDelivery(stream)

        assertSame(
            closeFailure,
            failure.suppressed
                .single(),
        )
    }

    @Test
    fun successfulDeliveryLeavesStreamOpen() =
        runBlocking<Unit> {
            val callerScheduler = TestCoroutineScheduler()
            val ioScheduler = TestCoroutineScheduler()
            val caller = StandardTestDispatcher(callerScheduler)
            val io = StandardTestDispatcher(ioScheduler)
            var closed = false
            val stream = Closeable { closed = true }
            val pending =
                async(caller) {
                    StreamOpenHelper.open(io) { stream }
                }

            callerScheduler.runCurrent()
            ioScheduler.runCurrent()
            callerScheduler.runCurrent()

            assertSame(stream, pending.await())
            assertFalse(closed)
            stream.close()
            assertTrue(closed)
        }

    @Test
    fun openingFailureIsPreserved() =
        runBlocking<Unit> {
            val failure = IOException("open failed")
            val actual =
                assertFailsWith<IOException> {
                    StreamOpenHelper.open { throw failure }
                }

            assertSame(failure, actual)
        }

    private fun cancelBeforeDelivery(stream: Closeable): CancellationException =
        runBlocking {
            val callerScheduler = TestCoroutineScheduler()
            val ioScheduler = TestCoroutineScheduler()
            val caller = StandardTestDispatcher(callerScheduler)
            val io = StandardTestDispatcher(ioScheduler)
            var opened = false
            val pending =
                async(caller) {
                    StreamOpenHelper.open(io) {
                        opened = true
                        stream
                    }
                }

            callerScheduler.runCurrent()
            ioScheduler.runCurrent()
            assertTrue(opened)
            assertFalse(pending.isCompleted)

            // 열기는 끝났지만 호출자에게 반환되기 전인 시점에 취소한다.
            pending.cancel()
            callerScheduler.runCurrent()

            val failure = assertFailsWith<CancellationException> { pending.await() }
            failure
        }
}
