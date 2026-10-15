package io.github.alphabs.fishsync.core

import io.github.alphabs.fishsync.core.internals.OrdinalCase
import java.io.File
import java.nio.file.Files

object PathHelper {
    fun normalizeRoot(
        root: String,
        options: PathOptions,
    ): String {
        require(File(root).isAbsolute) { "Root must be absolute" }
        val normalized = normalizePath(root, options)
        return if (normalized.endsWith(options.pathSeparator)) {
            normalized
        } else {
            normalized +
                options.pathSeparator
        }
    }

    fun normalizePath(
        path: String,
        options: PathOptions,
    ): String {
        val result = StringBuilder(path.length)
        var index = -1
        while (index < path.length) {
            val character = if (index < 0) '/' else path[index]
            if (isPathSeparator(character, options) && index + 1 < path.length) {
                if (isPathSeparator(path[index + 1], options)) {
                    index++
                    continue
                }
                if ((index + 2 == path.length || isPathSeparator(path[index + 2], options)) &&
                    path[index + 1] == '.'
                ) {
                    index += 2
                    continue
                }
                if (index + 2 < path.length &&
                    (index + 3 == path.length || isPathSeparator(path[index + 3], options)) &&
                    path[index + 1] == '.' && path[index + 2] == '.'
                ) {
                    throw IllegalArgumentException("The path contains relative parent segment")
                }
            }
            if (index >= 0) {
                result.append(
                    if (character ==
                        options.altPathSeparator
                    ) {
                        options.pathSeparator
                    } else {
                        character
                    },
                )
            }
            index++
        }
        return result.toString()
    }

    fun isPathSeparator(
        character: Char,
        options: PathOptions,
    ): Boolean = character == options.pathSeparator || character == options.altPathSeparator

    fun getRelativePathFromDirectory(
        path: String,
        root: String,
        options: PathOptions,
    ): String {
        val normalized = normalizePath(path, options)
        if (root.isEmpty()) return normalized
        require(root.endsWith(options.pathSeparator)) { "Root is not a directory" }
        val normalizedRoot = normalizePath(root, options)
        require(
            isRootDirectory(normalizedRoot, normalized, options),
        ) { "Path must start with root" }
        return normalized.substring(normalizedRoot.length)
    }

    fun isRootDirectory(
        root: String,
        path: String,
        options: PathOptions,
    ): Boolean =
        OrdinalCase.regionMatches(path, 0, root, options.caseInsensitive) &&
            (
                root.last() == options.pathSeparator ||
                    path.getOrNull(root.length) == options.pathSeparator
            )

    fun createParentDirectory(path: String) {
        File(path)
            .parentFile
            ?.mkdirs()
    }
}
