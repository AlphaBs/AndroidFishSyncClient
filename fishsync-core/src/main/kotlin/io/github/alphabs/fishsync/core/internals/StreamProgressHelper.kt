package io.github.alphabs.fishsync.core.internals

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicLong

internal object StreamProgressHelper {
    const val DEFAULT_BUFFER_LENGTH = 65536

    suspend fun copyStreamWithProgressPerBuffer(
        source: InputStream,
        destination: OutputStream,
        bufferSize: Int,
        progress: ((Long) -> Unit)?,
    ) = withContext(Dispatchers.IO) {
        val buffer = ByteArray(bufferSize)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = source.read(buffer)
            if (count < 0) break
            destination.write(buffer, 0, count)
            progress?.invoke(count.toLong())
        }
    }

    suspend fun copyStreamWithPeriodicProgress(
        source: InputStream,
        destination: OutputStream,
        interval: Long,
        progress: (Long) -> Unit,
    ) = coroutineScope {
        val position = AtomicLong()
        val copy =
            async(Dispatchers.IO) {
                copyStreamWithProgressPerBuffer(source, destination, DEFAULT_BUFFER_LENGTH) {
                    position.addAndGet(it)
                }
            }
        try {
            monitorStreamPosition(copy, { position.get() }, 0, interval, progress)
        } catch (failure: Throwable) {
            try {
                copy.await()
            } catch (_: Throwable) {
                // 복사 작업의 실패를 관찰한 뒤 먼저 발생한 오류를 유지한다.
            }
            throw failure
        }
    }

    suspend fun monitorStreamPosition(
        task: Deferred<Unit>,
        position: () -> Long,
        initialPosition: Long,
        interval: Long,
        delta: (Long) -> Unit,
    ) {
        var previous = initialPosition
        while (!task.isCompleted) {
            val current = position()
            delta(current - previous)
            previous = current
            withTimeoutOrNull(interval) { task.await() }
        }
        task.await()
        val remaining = position() - previous
        if (remaining != 0L) delta(remaining)
    }

    fun getBufferSize(size: Long): Int =
        size
            .coerceIn(128, DEFAULT_BUFFER_LENGTH.toLong())
            .toInt()
}
