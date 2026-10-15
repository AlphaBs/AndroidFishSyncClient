package io.github.alphabs.fishsync.core

import io.github.alphabs.fishsync.core.internals.StreamProgressHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class StreamContractTest {
    @Test
    fun checksumCancellationPreservesBorrowedStream() =
        runBlocking<Unit> {
            val task = Job()
            var closed = false
            val input =
                object : ByteArrayInputStream(byteArrayOf(1, 2, 3)) {
                    override fun read(
                        bytes: ByteArray,
                        offset: Int,
                        length: Int,
                    ): Int {
                        val count = super.read(bytes, offset, length)
                        task.cancel()
                        return count
                    }

                    override fun close() {
                        closed = true
                        super.close()
                    }
                }
            try {
                assertFailsWith<CancellationException> {
                    withContext(task) { ChecksumAlgorithms.computeHashAsync("md5", input) }
                }
                assertFalse(closed)
            } finally {
                input.close()
                task.cancel()
            }
        }

    @Test
    fun copyChecksCancellationBeforeWritingReadBytes() =
        runBlocking<Unit> {
            val task = Job()
            val input =
                object : ByteArrayInputStream(byteArrayOf(1, 2, 3)) {
                    override fun read(
                        bytes: ByteArray,
                        offset: Int,
                        length: Int,
                    ): Int {
                        val count = super.read(bytes, offset, length)
                        task.cancel()
                        return count
                    }
                }
            input.use {
                val output = ByteArrayOutputStream()
                assertFailsWith<CancellationException> {
                    withContext(task) {
                        StreamProgressHelper.copyStreamWithProgressPerBuffer(
                            input,
                            output,
                            16,
                            null,
                        )
                    }
                }
                assertEquals(0, output.size())
            }
            task.cancel()
        }
}
