package io.github.alphabs.fishsync.core.filecomparers

interface IFileComparerFactory {
    fun createFullComparer(): IFileComparer

    fun createFastComparer(): IFileComparer
}
