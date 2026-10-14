package io.github.alphabs.fishsync.core

/** 임시 파일 적용과 삭제를 지원하는 저장소 계약이다. */
interface WritableSyncStorage : ReadableSyncStorage {
    fun prepare(path: String): PendingWrite

    fun delete(path: String)
}
