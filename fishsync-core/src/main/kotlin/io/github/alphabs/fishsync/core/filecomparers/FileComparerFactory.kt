package io.github.alphabs.fishsync.core.filecomparers

import io.github.alphabs.fishsync.core.syncer.Comparison

fun interface FileComparerFactory {
    fun create(comparison: Comparison): FileComparer
}
