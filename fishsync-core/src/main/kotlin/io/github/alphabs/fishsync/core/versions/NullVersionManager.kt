package io.github.alphabs.fishsync.core.versions

open class NullVersionManager : VersionManager {
    override suspend fun checkNewVersion(sourceVersion: String?): Boolean = true

    override suspend fun getCurrentVersion(): String? = null

    override suspend fun updateVersion(newVersion: String) = Unit
}
