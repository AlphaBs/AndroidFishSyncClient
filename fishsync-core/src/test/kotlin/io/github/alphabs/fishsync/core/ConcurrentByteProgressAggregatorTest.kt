package io.github.alphabs.fishsync.core

import io.github.alphabs.fishsync.core.progress.ByteProgress
import io.github.alphabs.fishsync.core.progress.ConcurrentByteProgressAggregator
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConcurrentByteProgressAggregatorTest {
    @Test
    fun retainsAllReportsAfterWorkerThreadsExit() {
        val aggregator = ConcurrentByteProgressAggregator()
        val ready = CountDownLatch(4)
        val start = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(4)
        try {
            val tasks =
                (0 until 4).map {
                    workers.submit {
                        ready.countDown()
                        check(start.await(10, TimeUnit.SECONDS))
                        repeat(10_000) {
                            aggregator.report(ByteProgress(3, 7))
                        }
                    }
                }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            tasks.forEach { it.get(10, TimeUnit.SECONDS) }
            workers.shutdown()
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS))

            assertEquals(ByteProgress(120_000, 280_000), aggregator.aggregateProgress())
        } finally {
            start.countDown()
            workers.shutdownNow()
            aggregator.close()
        }
    }

    @Test
    fun clearRebindsAnExistingWorkerToFreshProgress() {
        val aggregator = ConcurrentByteProgressAggregator()
        val worker = Executors.newSingleThreadExecutor()
        try {
            worker
                .submit { aggregator.report(ByteProgress(100, 50)) }
                .get(10, TimeUnit.SECONDS)
            assertEquals(ByteProgress(100, 50), aggregator.aggregateProgress())

            aggregator.clear()
            assertEquals(ByteProgress(), aggregator.aggregateProgress())
            worker
                .submit { aggregator.report(ByteProgress(20, 10)) }
                .get(10, TimeUnit.SECONDS)
            assertEquals(ByteProgress(20, 10), aggregator.aggregateProgress())

            aggregator.clear()
            worker
                .submit { aggregator.report(ByteProgress(8, 4)) }
                .get(10, TimeUnit.SECONDS)
            assertEquals(ByteProgress(8, 4), aggregator.aggregateProgress())
        } finally {
            worker.shutdownNow()
            assertTrue(worker.awaitTermination(10, TimeUnit.SECONDS))
            aggregator.close()
        }
    }

    @Test
    fun closeReleasesProgressAndClearStartsANewStorage() {
        val aggregator = ConcurrentByteProgressAggregator()
        aggregator.report(ByteProgress(100, 50))
        aggregator.close()
        aggregator.close()

        assertFailsWith<IllegalStateException> { aggregator.report(ByteProgress(1, 1)) }
        assertFailsWith<IllegalStateException> { aggregator.aggregateProgress() }

        // 원본 Clear처럼 새 저장소를 만들면 이전 종료 상태와 누적값을 버린다.
        aggregator.clear()
        try {
            assertEquals(ByteProgress(), aggregator.aggregateProgress())
            aggregator.report(ByteProgress(8, 4))
            assertEquals(ByteProgress(8, 4), aggregator.aggregateProgress())
        } finally {
            aggregator.close()
        }
    }
}
