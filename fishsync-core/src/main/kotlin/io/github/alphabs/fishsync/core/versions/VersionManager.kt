package io.github.alphabs.fishsync.core.versions

interface VersionManager {
    suspend fun checkNewVersion(sourceVersion: String?): Boolean

    suspend fun getCurrentVersion(): String?

    suspend fun updateVersion(newVersion: String)
}
