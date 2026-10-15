package io.github.alphabs.fishsync.core.storage

import io.github.alphabs.fishsync.core.storage.relativePath
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption

class FileSyncStorage(
    root: File,
) : WritableSyncStorage {
    private val root =
        root.canonicalFile
            .toPath()
            .also { Files.createDirectories(it) }
    override val identity: String =
        this.root
            .toString()

    private fun checked(path: String): java.nio.file.Path {
        val target =
            root
                .resolve(relativePath(path))
                .normalize()
        require(target.startsWith(root))
        var current = root
        for (part in root.relativize(target)) {
            current = current.resolve(part)
            require(!Files.isSymbolicLink(current)) { "Symbolic links are not supported" }
        }
        return target
    }

    override fun list(): List<FileEntry> {
        val entries = mutableListOf<FileEntry>()

        Files.walk(root).use { paths ->
            paths.forEach { path ->
                if (path == root) {
                    return@forEach
                }

                val relative =
                    root
                        .relativize(path)
                        .toString()
                        .replace(File.separatorChar, '/')
                checked(relative)

                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    entries.add(FileEntry(relative, Files.size(path)))
                }
            }
        }

        return entries.sortedBy { it.path }
    }

    override fun read(path: String): InputStream =
        Files.newInputStream(checked(path), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)

    override fun write(path: String): OutputStream {
        val target = checked(path)
        Files.createDirectories(target.parent)
        checked(path)
        return Files.newOutputStream(
            target,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS,
        )
    }

    override fun delete(path: String) {
        Files.deleteIfExists(checked(path))
    }
}
