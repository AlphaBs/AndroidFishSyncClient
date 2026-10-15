package io.github.alphabs.fishsync.core.internals

import java.io.File
import java.io.IOException
import java.nio.file.Files

internal object LocalPathGuard {
    fun ensureNoLinks(path: String) {
        val absolute = File(path)
        require(absolute.isAbsolute) { "Local paths must be absolute" }
        val normalized =
            absolute
                .toPath()
                .normalize()
        var current = normalized.root
        if (Files.isSymbolicLink(current)) throw IOException("Links are not allowed: $current")
        for (part in normalized) {
            current = current.resolve(part)
            if (Files.isSymbolicLink(current)) throw IOException("Links are not allowed: $current")
        }
    }
}
