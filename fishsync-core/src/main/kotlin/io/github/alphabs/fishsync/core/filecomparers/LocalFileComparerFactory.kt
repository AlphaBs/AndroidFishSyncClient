package io.github.alphabs.fishsync.core.filecomparers

open class LocalFileComparerFactory : FileComparerFactory {
    override fun createFullComparer(): FileComparer = LocalFileChecksumComparer()

    override fun createFastComparer(): FileComparer = LocalFileSizeComparer()
}
