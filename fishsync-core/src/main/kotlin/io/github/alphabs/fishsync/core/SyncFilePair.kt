package io.github.alphabs.fishsync.core

/** 새 파일이면 target은 적용할 경로와 알 수 없는 크기를 가진다. */
data class SyncFilePair(
    val source: SourceFile,
    val target: FileEntry,
)
