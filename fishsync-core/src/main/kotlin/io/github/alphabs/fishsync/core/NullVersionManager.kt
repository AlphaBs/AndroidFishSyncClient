package io.github.alphabs.fishsync.core

/** 버전을 저장하지 않고 원본처럼 항상 새 버전으로 취급한다. */
class NullVersionManager : VersionManager {
    override fun current(): String? = null

    override fun isNew(version: String?): Boolean = true

    override fun update(version: String) = Unit
}
