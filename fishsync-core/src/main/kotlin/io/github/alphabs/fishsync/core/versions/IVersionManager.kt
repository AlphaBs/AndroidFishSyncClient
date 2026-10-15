package io.github.alphabs.fishsync.core.versions

interface IVersionManager {
    suspend fun checkNewVersion(sourceVersion: String?): Boolean

    suspend fun getCurrentVersion(): String?

    suspend fun updateVersion(newVersion: String)
}
