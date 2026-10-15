package io.github.alphabs.fishsync.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.alphabs.fishsync.core.PathOptions
import io.github.alphabs.fishsync.core.storage.FileSyncStorage
import io.github.alphabs.fishsync.core.syncer.ISyncFilePairSyncer
import io.github.alphabs.fishsync.core.syncer.LocalSyncer
import io.github.alphabs.fishsync.core.syncer.ParallelSyncFilePairSyncer
import java.io.File

object AndroidStorage {
    fun appFiles(context: Context) = FileSyncStorage(File(context.filesDir, "sync"))

    /** 신뢰하는 앱 전용 경로의 Android 별칭만 먼저 해석한다. */
    fun appLocalSyncer(
        context: Context,
        options: PathOptions = PathOptions(),
        pairSyncer: ISyncFilePairSyncer = ParallelSyncFilePairSyncer(),
    ): LocalSyncer = LocalSyncer(File(context.filesDir, "sync").canonicalPath, options, pairSyncer)

    fun retainFolder(
        context: Context,
        uri: Uri,
        grantedFlags: Int,
    ) {
        val requiredFlags =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val flags = grantedFlags and requiredFlags
        require(flags == requiredFlags) { "Read/write folder permission is required" }

        context.contentResolver
            .takePersistableUriPermission(uri, flags)
    }
}
