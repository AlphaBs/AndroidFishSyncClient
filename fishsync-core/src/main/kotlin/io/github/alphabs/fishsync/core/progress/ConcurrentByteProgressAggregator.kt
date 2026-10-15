package io.github.alphabs.fishsync.core.progress

import java.util.concurrent.ConcurrentLinkedQueue

/** 스레드별로 진행률을 누적하고 집계할 때 전체 값을 합산한다. */
open class ConcurrentByteProgressAggregator : AutoCloseable {
    private class Slot {
        // 집계 스레드가 두 필드가 함께 갱신된 불변 값을 읽도록 공개한다.
        @Volatile
        var progress = ByteProgress()
    }

    private class ProgressStorage {
        // 종료된 스레드의 누적값도 집계할 수 있도록 슬롯을 보관한다.
        val slots = ConcurrentLinkedQueue<Slot>()
        val local =
            ThreadLocal.withInitial {
                Slot().also { slots.add(it) }
            }

        fun close() {
            local.remove()
            slots.clear()
        }
    }

    @Volatile
    private var storage: ProgressStorage? = ProgressStorage()

    fun report(value: ByteProgress) {
        val current = currentStorage()
        val slot =
            current.local
                .get()
        slot.progress += value
    }

    fun clear() {
        val previous = storage
        storage = ProgressStorage()
        previous?.close()
    }

    fun aggregateProgress(): ByteProgress {
        val current = currentStorage()
        var total = 0L
        var progressed = 0L
        for (slot in current.slots) {
            val value = slot.progress
            total += value.totalBytes
            progressed += value.progressedBytes
        }
        return ByteProgress(total, progressed)
    }

    override fun close() {
        val previous = storage
        storage = null
        previous?.close()
    }

    private fun currentStorage(): ProgressStorage =
        checkNotNull(storage) { "Progress aggregator is closed" }
}
