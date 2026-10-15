package io.github.alphabs.fishsync.core.storage.versions

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class VersionState(
    private val file: File,
) : VersionStore {
    override fun current(): String? =
        try {
            file
                .inputStream()
                .use { readVersion(it) }
        } catch (_: IOException) {
            null
        }

    override fun isNew(version: String?): Boolean = current() != version?.trim()

    override fun update(version: String) {
        val bytes =
            version
                .trim()
                .toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_VERSION_BYTES)

        val parent = file.absoluteFile.parentFile
        parent.mkdirs()
        val temporary = Files.createTempFile(parent.toPath(), ".version-", ".tmp")

        try {
            Files.write(temporary, bytes)
            Files.move(
                temporary,
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun readVersion(input: InputStream): String? {
        // 한 바이트 더 읽어 UTF-8 바이트 수 제한을 초과했는지 확인한다.
        val bytes = ByteArray(MAX_VERSION_BYTES + 1)
        var used = 0

        while (used < bytes.size) {
            val bytesRead = input.read(bytes, used, bytes.size - used)
            if (bytesRead < 0) {
                break
            }
            used += bytesRead
        }

        if (used > MAX_VERSION_BYTES) {
            return null
        }

        return String(bytes, 0, used, Charsets.UTF_8).trim()
    }

    private companion object {
        const val MAX_VERSION_BYTES = 128
    }
}
