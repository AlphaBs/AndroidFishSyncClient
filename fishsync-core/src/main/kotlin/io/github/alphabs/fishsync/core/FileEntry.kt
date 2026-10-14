package io.github.alphabs.fishsync.core

data class FileEntry(
    val path: String,
    val size: Long?,
) {
    init {
        require(path == relativePath(path))
        require(size == null || size >= 0)
    }
}
