package io.github.alphabs.fishsync.core.filecomparers

interface FileComparerFactory {
    fun createFullComparer(): FileComparer

    fun createFastComparer(): FileComparer
}
