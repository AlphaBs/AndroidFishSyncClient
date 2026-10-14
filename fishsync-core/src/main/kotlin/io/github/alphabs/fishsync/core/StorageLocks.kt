package io.github.alphabs.fishsync.core

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes library writes to the same root, including PULL and PUSH snapshots. */
object StorageLocks {
    private val locks = ConcurrentHashMap<String, Mutex>()
    suspend fun <T> run(storage: SyncStorage, block: suspend () -> T): T = locks.getOrPut(storage.identity) { Mutex() }.withLock { block() }
}
