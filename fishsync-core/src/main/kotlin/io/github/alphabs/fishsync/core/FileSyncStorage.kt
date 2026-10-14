package io.github.alphabs.fishsync.core

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

class FileSyncStorage(
    root: File,
) : SyncStorage {
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

    override fun prepare(path: String): PendingWrite {
        val target = checked(path)
        Files.createDirectories(target.parent)
        checked(path)
        // 임시 파일은 같은 파일 시스템을 사용하되 동기화 목록에는 포함하지 않는다.
        val temporary = Files.createTempFile(root.parent, ".fishsync-", ".tmp")
        return object : PendingWrite {
            private var committed = false

            override fun output(): OutputStream = Files.newOutputStream(temporary)

            override fun input(): InputStream = Files.newInputStream(temporary)

            override fun commit() {
                checked(path)
                Files.move(
                    temporary,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
                committed = true
            }

            override fun close() {
                if (!committed) Files.deleteIfExists(temporary)
            }
        }
    }

    override fun delete(path: String) {
        Files.delete(checked(path))
    }
}
