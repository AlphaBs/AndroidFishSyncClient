package io.github.alphabs.fishsync.core.storage

/** 저장소 클라이언트의 간편 설정용 확장이다. 원본 규칙은 comparer를 직접 받는다. */
enum class Comparison { Size, Checksum, SizeAndChecksum }
