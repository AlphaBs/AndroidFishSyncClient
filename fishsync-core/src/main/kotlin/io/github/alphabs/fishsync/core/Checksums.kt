package io.github.alphabs.fishsync.core

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.InputStream
import java.security.MessageDigest

suspend fun hash(
    input: InputStream,
    algorithm: String = "md5",
): String {
    val algorithmName =
        when (algorithm) {
            "md5" -> "MD5"
            "sha1" -> "SHA-1"
            else -> error("Unsupported checksum algorithm")
        }
    val digest = MessageDigest.getInstance(algorithmName)
    val buffer = ByteArray(65536)

    while (true) {
        currentCoroutineContext().ensureActive()
        val bytesRead = input.read(buffer)
        if (bytesRead < 0) {
            break
        }

        if (bytesRead > 0) {
            digest.update(buffer, 0, bytesRead)
        }
    }

    return digest
        .digest()
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
