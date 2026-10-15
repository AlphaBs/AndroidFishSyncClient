package io.github.alphabs.fishsync.core

import io.github.alphabs.fishsync.core.internals.LocalPathGuard
import java.io.File
import java.io.IOException

class RootedPath private constructor(
    val root: String,
    val subPath: String,
    private val options: PathOptions,
) {
    val isRooted get() = root.isNotEmpty()
    val isDirectory get() = subPath.isEmpty() || subPath.endsWith(options.pathSeparator)

    fun withRoot(newRoot: String): RootedPath = create(newRoot, subPath, options)

    fun getFullPath(): String {
        check(isRooted) { "Root was not set" }
        return root + subPath
    }

    override fun toString(): String = if (isRooted) getFullPath() else subPath

    override fun equals(other: Any?): Boolean =
        other is RootedPath && toString() == other.toString()

    override fun hashCode(): Int = toString().hashCode()

    companion object {
        fun create(
            root: String,
            subPath: String,
            options: PathOptions,
        ): RootedPath {
            require(root.isNotEmpty() || subPath.isNotEmpty()) { "Empty unrooted path" }
            val normalizedRoot =
                if (root.isEmpty()) {
                    root
                } else {
                    PathHelper.normalizeRoot(
                        root,
                        options,
                    )
                }
            val normalized =
                PathHelper
                    .normalizePath(
                        subPath,
                        options,
                    ).trimStart(options.pathSeparator)
            return RootedPath(normalizedRoot, normalized, options)
        }

        fun fromFullPath(
            root: String,
            fullPath: String,
            options: PathOptions,
        ): RootedPath {
            val normalizedRoot = PathHelper.normalizeRoot(root, options)
            return create(
                normalizedRoot,
                PathHelper.getRelativePathFromDirectory(fullPath, normalizedRoot, options),
                options,
            )
        }

        fun fromSubPath(
            subPath: String,
            options: PathOptions,
        ): RootedPath = create("", subPath, options)

        fun fromDirectory(
            root: String,
            options: PathOptions,
        ): Sequence<RootedPath> =
            sequence {
                LocalPathGuard.ensureNoLinks(root)
                val directory = File(root)
                if (!directory.isDirectory) return@sequence
                val pending = ArrayDeque<File>()
                pending.add(directory)
                while (pending.isNotEmpty()) {
                    val current = pending.removeLast()
                    LocalPathGuard.ensureNoLinks(current.path)
                    val children =
                        current.listFiles() ?: throw IOException("Cannot enumerate $current")
                    for (child in children) {
                        LocalPathGuard.ensureNoLinks(child.path)
                        if (child.isDirectory) {
                            pending.add(child)
                        } else {
                            yield(fromFullPath(root, child.path, options))
                        }
                    }
                }
            }
    }
}
