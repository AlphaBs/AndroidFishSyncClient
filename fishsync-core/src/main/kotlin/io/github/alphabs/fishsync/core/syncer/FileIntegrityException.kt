package io.github.alphabs.fishsync.core.syncer

open class FileIntegrityException(
    var file: String,
    message: String = "File integrity error: $file",
) : Exception(message)
