package io.github.alphabs.fishsync.core

fun relativePath(value: String): String {
    val path = value.replace('\\', '/')
    require(
        path.isNotEmpty() && !path.startsWith('/') && !path.contains(':') &&
            !path.contains('\u0000'),
    ) { "Invalid relative path" }
    require(
        path.split('/').none {
            it.isEmpty() || it == "." || it == ".."
        },
    ) { "Invalid relative path" }
    return path
}
