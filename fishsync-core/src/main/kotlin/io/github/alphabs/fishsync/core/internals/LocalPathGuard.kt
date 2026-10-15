package io.github.alphabs.fishsync.core.internals

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

internal object LocalPathGuard {
    fun ensureNoLinks(path: String) {
        val absolute = File(path)
        require(absolute.isAbsolute) { "Local paths must be absolute" }
        val normalized =
            absolute
                .toPath()
                .normalize()
        var current = normalized.root
        checkEntry(current)
        for (part in normalized) {
            current = current.resolve(part)
            checkEntry(current)
        }
    }

    private fun checkEntry(path: Path) {
        val attributes =
            try {
                Files.readAttributes(
                    path,
                    BasicFileAttributes::class.java,
                    LinkOption.NOFOLLOW_LINKS,
                )
            } catch (_: NoSuchFileException) {
                return
            } catch (_: NotDirectoryException) {
                return
            }
        if (attributes.isSymbolicLink) throw IOException("Links are not allowed: $path")
    }
}
