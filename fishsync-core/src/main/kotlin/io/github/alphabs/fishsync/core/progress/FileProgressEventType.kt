package io.github.alphabs.fishsync.core.progress

enum class FileProgressEventType {
    None,
    Queue,
    StartCompare,
    DoneCompare,
    StartSync,
    DoneSync,
}
