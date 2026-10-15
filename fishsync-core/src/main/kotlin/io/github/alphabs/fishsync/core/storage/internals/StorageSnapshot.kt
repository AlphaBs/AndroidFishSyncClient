package io.github.alphabs.fishsync.core.storage.internals

import io.github.alphabs.fishsync.core.ChecksumAlgorithms
import io.github.alphabs.fishsync.core.storage.ChangedFileException
import io.github.alphabs.fishsync.core.storage.ReadableSyncStorage

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
                .use { ChecksumAlgorithms.computeHashAsync("md5", it) }
        }
    if (digest != original) {
        throw ChangedFileException(path)
    }
}
