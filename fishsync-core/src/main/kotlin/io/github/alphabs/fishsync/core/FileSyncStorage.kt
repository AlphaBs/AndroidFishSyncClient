package io.github.alphabs.fishsync.core

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

class FileSyncStorage(root: File) : SyncStorage {
    private val root = root.canonicalFile.toPath().also { Files.createDirectories(it) }
    override val identity: String = this.root.toString()
    private fun checked(path: String): java.nio.file.Path {
        val target = root.resolve(relativePath(path)).normalize()
        require(target.startsWith(root))
        var current = root
        for (part in root.relativize(target)) {
            current = current.resolve(part)
            require(!Files.isSymbolicLink(current)) { "Symbolic links are not supported" }
        }
        return target
    }
    override fun list(): List<FileEntry> = Files.walk(root).use { paths ->
        paths.filter { it != root }.map { p ->
            val relative = root.relativize(p).toString().replace(File.separatorChar, '/')
            checked(relative)
            if (Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) FileEntry(relative, Files.size(p)) else null
        }.filter { it != null }.map { it!! }.toArray().map { it as FileEntry }.sortedBy { it.path }
    }
    override fun read(path: String): InputStream = Files.newInputStream(checked(path), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)
    override fun prepare(path: String): PendingWrite {
        val target = checked(path)
        Files.createDirectories(target.parent)
        checked(path)
        // Staging beside the root stays on the same filesystem and outside the sync inventory.
        val temporary = Files.createTempFile(root.parent, ".fishsync-", ".tmp")
        return object : PendingWrite {
            private var committed = false
            override fun output(): OutputStream = Files.newOutputStream(temporary)
            override fun input(): InputStream = Files.newInputStream(temporary)
            override fun commit() {
                checked(path)
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                committed = true
            }
            override fun close() { if (!committed) Files.deleteIfExists(temporary) }
        }
    }
    override fun delete(path: String) { Files.delete(checked(path)) }
}
