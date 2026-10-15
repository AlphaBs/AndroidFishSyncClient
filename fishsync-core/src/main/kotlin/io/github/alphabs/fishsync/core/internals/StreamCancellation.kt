package io.github.alphabs.fishsync.core.internals

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.io.InputStream
import java.io.OutputStream

/** Java 스트림에는 읽기 취소 토큰이 없으므로 취소 시 스트림을 닫아 차단된 I/O를 해제한다. */
internal suspend fun <T> withStreamCancellation(
    input: InputStream,
    output: OutputStream? = null,
    operation: () -> T,
): T =
    coroutineScope {
        val context = currentCoroutineContext()
        val watcher =
            launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    if (!context.isActive) {
                        runCatching { input.close() }
                        runCatching { output?.close() }
                    }
                }
            }
        try {
            runInterruptible(Dispatchers.IO, operation)
        } catch (failure: Throwable) {
            context.ensureActive()
            throw failure
        } finally {
            watcher.cancel()
        }
    }
