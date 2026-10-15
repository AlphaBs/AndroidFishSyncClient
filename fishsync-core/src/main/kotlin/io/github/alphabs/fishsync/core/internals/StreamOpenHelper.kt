package io.github.alphabs.fishsync.core.internals

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.Closeable

internal object StreamOpenHelper {
    suspend fun <T : Closeable> open(
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        openStream: () -> T,
    ): T {
        var opened: T? = null
        try {
            return withContext(dispatcher) {
                currentCoroutineContext().ensureActive()
                val stream = openStream()
                opened = stream
                stream
            }
        } catch (failure: Throwable) {
            // 호출자에게 소유권을 전달하지 못한 스트림만 정리한다.
            try {
                opened?.close()
            } catch (closeFailure: Throwable) {
                if (closeFailure !== failure) {
                    failure.addSuppressed(closeFailure)
                }
            }
            throw failure
        }
    }
}
