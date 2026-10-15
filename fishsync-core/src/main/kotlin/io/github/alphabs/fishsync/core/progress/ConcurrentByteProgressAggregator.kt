package io.github.alphabs.fishsync.core.progress

import java.util.concurrent.atomic.AtomicReference

/** 코루틴은 스레드를 이동하므로 합계 값을 원자적으로 갱신한다. */
class ConcurrentByteProgressAggregator : AutoCloseable {
    private val progress = AtomicReference(ByteProgress())

    fun report(value: ByteProgress) {
        progress.updateAndGet { it + value }
    }

    fun clear() {
        progress.set(ByteProgress())
    }

    fun aggregateProgress(): ByteProgress = progress.get()

    override fun close() = Unit
}
