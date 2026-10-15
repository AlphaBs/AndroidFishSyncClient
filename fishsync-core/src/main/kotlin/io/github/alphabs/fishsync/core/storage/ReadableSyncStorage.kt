package io.github.alphabs.fishsync.core.storage

import java.io.InputStream

/** 파일 목록과 내용을 읽을 수 있는 저장소 계약이다. */
interface ReadableSyncStorage {
    val identity: String

    fun list(): List<FileEntry>

    fun read(path: String): InputStream
}
