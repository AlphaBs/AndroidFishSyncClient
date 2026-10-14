package io.github.alphabs.fishsync.core

interface VersionManager {
    fun current(): String?

    fun isNew(version: String?): Boolean

    fun update(version: String)
}
