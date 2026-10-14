package io.github.alphabs.fishsync.core

import java.io.InputStream

data class SourceFile(
    val path: String,
    val size: Long?,
    val checksum: Checksum?,
    val open: suspend () -> InputStream,
) {
    init {
        require(path == relativePath(path))
        require(size == null || size >= 0)
    }
}
