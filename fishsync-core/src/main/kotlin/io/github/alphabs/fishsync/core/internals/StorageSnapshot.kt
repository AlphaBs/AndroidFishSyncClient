package io.github.alphabs.fishsync.core.internals

import io.github.alphabs.fishsync.core.files.ReadableSyncStorage
import io.github.alphabs.fishsync.core.hash
import io.github.alphabs.fishsync.core.syncer.ChangedFileException

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
