package io.github.alphabs.fishsync.core.filecomparers

import io.github.alphabs.fishsync.core.files.SyncFilePair

fun interface IFileComparer {
    suspend fun areEqual(pair: SyncFilePair): Boolean
}
