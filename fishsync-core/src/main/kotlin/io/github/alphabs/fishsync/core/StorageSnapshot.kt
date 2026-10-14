package io.github.alphabs.fishsync.core

internal suspend fun checkUnchanged(
    storage: ReadableSyncStorage,
    path: String,
    original: String?,
) {
    val current =
        storage
            .list()
            .firstOrNull { it.path == path }
    val digest =
        current?.let {
            storage
                .read(path)
                .use { hash(it) }
        }
    if (digest != original) {
        throw ChangedFileException(path)
    }
}
