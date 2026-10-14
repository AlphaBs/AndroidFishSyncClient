package io.github.alphabs.fishsync.core

/** true는 비교 정책상 전송이 불필요하다는 뜻이며 무결성 검증을 대신하지 않는다. */
fun interface FileComparer {
    suspend fun areEqual(
        pair: SyncFilePair,
        storage: ReadableSyncStorage,
    ): Boolean
}
