package io.github.alphabs.fishsync.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/** 같은 루트의 PULL 적용과 PUSH 스냅샷 생성을 직렬화한다. */
object StorageLocks {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> run(
        storage: ReadableSyncStorage,
        block: suspend () -> T,
    ): T {
        val lock = locks.getOrPut(storage.identity) { Mutex() }
        return lock.withLock { block() }
    }
}
