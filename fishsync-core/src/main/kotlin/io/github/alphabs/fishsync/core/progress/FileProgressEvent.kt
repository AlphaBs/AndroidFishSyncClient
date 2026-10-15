package io.github.alphabs.fishsync.core.progress

data class FileProgressEvent(
    val eventType: FileProgressEventType,
    val progressedFiles: Int,
    val totalFiles: Int,
    val currentFileName: String,
)
