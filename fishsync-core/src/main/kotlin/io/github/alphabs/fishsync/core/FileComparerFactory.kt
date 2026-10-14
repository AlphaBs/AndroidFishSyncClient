package io.github.alphabs.fishsync.core

fun interface FileComparerFactory {
    fun create(comparison: Comparison): FileComparer
}
