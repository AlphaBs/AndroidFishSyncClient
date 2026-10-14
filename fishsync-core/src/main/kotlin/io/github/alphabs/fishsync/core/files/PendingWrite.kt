package io.github.alphabs.fishsync.core.files

import java.io.InputStream
import java.io.OutputStream

/** commit 전에는 원본을 보호하며, close 시 미적용 임시 파일을 정리한다. */
interface PendingWrite : AutoCloseable {
    fun output(): OutputStream

    fun input(): InputStream

    fun commit()
}
