package io.github.alphabs.fishsync.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.alphabs.fishsync.core.storage.FileSyncStorage
import java.io.File

object AndroidStorage {
    fun appFiles(context: Context) = FileSyncStorage(File(context.filesDir, "sync"))

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
