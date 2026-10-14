package io.github.alphabs.fishsync.core.files

import io.github.alphabs.fishsync.core.relativePath

data class FileEntry(
    val path: String,
    val size: Long?,
) {
    init {
        require(path == relativePath(path))
        require(size == null || size >= 0)
    }
}
