package io.github.alphabs.fishsync.core.filecomparers

import io.github.alphabs.fishsync.core.files.Checksum
import io.github.alphabs.fishsync.core.syncer.Comparison

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
