package io.github.alphabs.fishsync.core

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class VersionState(private val file: File) {
    fun current(): String? = try {
        file.inputStream().use { input ->
            val bytes = ByteArray(129)
            var used = 0
            while (used < bytes.size) { val n = input.read(bytes, used, bytes.size - used); if (n < 0) break; used += n }
            if (used > 128) null else String(bytes, 0, used, Charsets.UTF_8).trim()
        }
    } catch (_: java.io.IOException) { null }
    fun isNew(version: String?): Boolean = current() != version?.trim()
    fun update(version: String) {
        val bytes = version.trim().toByteArray(Charsets.UTF_8)
        require(bytes.size <= 128)
        val parent = file.absoluteFile.parentFile.also { it.mkdirs() }
        val temp = Files.createTempFile(parent.toPath(), ".version-", ".tmp")
        try { Files.write(temp, bytes); Files.move(temp, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        finally { Files.deleteIfExists(temp) }
    }
}
