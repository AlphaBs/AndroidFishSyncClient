package io.github.alphabs.fishsync.core.files

import io.github.alphabs.fishsync.core.relativePath
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
