package io.github.alphabs.fishsync.core

data class PathOptions(
    val pathSeparator: Char = '/',
    val altPathSeparator: Char = '\\',
    val caseInsensitive: Boolean = true,
)
