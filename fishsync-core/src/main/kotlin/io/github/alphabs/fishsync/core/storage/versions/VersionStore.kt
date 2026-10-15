package io.github.alphabs.fishsync.core.storage.versions

interface VersionStore {
    fun current(): String?

    fun isNew(version: String?): Boolean

    fun update(version: String)
}
