package io.github.alphabs.fishsync.core.storage

import java.io.OutputStream

/** 대상 파일에 직접 쓰거나 명시적으로 삭제하는 저장소 계약이다. */
interface WritableSyncStorage : ReadableSyncStorage {
    fun write(path: String): OutputStream

    fun delete(path: String)
}
