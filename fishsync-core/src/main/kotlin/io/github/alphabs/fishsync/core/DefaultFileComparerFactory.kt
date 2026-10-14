package io.github.alphabs.fishsync.core

class DefaultFileComparerFactory : FileComparerFactory {
    override fun create(comparison: Comparison): FileComparer =
        when (comparison) {
            Comparison.Size -> FileSizeComparer()
            Comparison.Checksum -> FileChecksumComparer()
            Comparison.SizeAndChecksum ->
                CompositeFileComparer(
                    listOf(FileSizeComparer(), FileChecksumComparer()),
                )
        }
}
