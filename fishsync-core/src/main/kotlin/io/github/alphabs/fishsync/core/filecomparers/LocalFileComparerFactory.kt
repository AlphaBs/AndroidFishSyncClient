package io.github.alphabs.fishsync.core.filecomparers

open class LocalFileComparerFactory : IFileComparerFactory {
    override fun createFullComparer(): IFileComparer = LocalFileChecksumComparer()

    override fun createFastComparer(): IFileComparer = LocalFileSizeComparer()
}
