package io.github.alphabs.fishsync.core.syncer

class FileIntegrityException(
    var file: String,
    message: String = "File integrity error: $file",
) : Exception(message)
