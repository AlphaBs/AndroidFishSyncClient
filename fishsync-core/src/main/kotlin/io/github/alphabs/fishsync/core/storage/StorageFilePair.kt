package io.github.alphabs.fishsync.core.storage

import io.github.alphabs.fishsync.core.files.SyncFile

/** 새 파일이면 target은 적용할 경로와 알 수 없는 크기를 가진다. */
data class StorageFilePair(
    val source: SyncFile,
    val target: FileEntry,
)
