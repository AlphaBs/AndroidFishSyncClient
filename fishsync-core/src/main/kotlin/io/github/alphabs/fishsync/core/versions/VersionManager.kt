package io.github.alphabs.fishsync.core.versions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class VersionManager(
    private val versionPath: String,
) : IVersionManager {
    override suspend fun getCurrentVersion(): String? =
        withContext(Dispatchers.IO) {
            try {
                File(versionPath).inputStream().use { input ->
                    val bytes = ByteArray(FILE_SIZE_LIMIT + 1)
                    var used = 0
                    while (used < bytes.size) {
                        val count = input.read(bytes, used, bytes.size - used)
                        if (count < 0) break
                        used += count
                    }
                    if (used >
                        FILE_SIZE_LIMIT
                    ) {
                        null
                    } else {
                        String(bytes, 0, used, Charsets.UTF_8).trim()
                    }
                }
            } catch (_: Exception) {
                null
            }
        }

    override suspend fun checkNewVersion(sourceVersion: String?): Boolean =
        getCurrentVersion() != sourceVersion?.trim()

    override suspend fun updateVersion(newVersion: String) =
        withContext(Dispatchers.IO) {
            val bytes =
                newVersion
                    .trim()
                    .toByteArray(Charsets.UTF_8)
            require(bytes.size <= FILE_SIZE_LIMIT)
            File(versionPath)
                .outputStream()
                .use { it.write(bytes) }
        }

    companion object {
        const val FILE_SIZE_LIMIT = 128
    }
}
