package io.github.alphabs.fishsync.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.security.MessageDigest

object ChecksumAlgorithms {
    fun createHashAlgorithmFromName(name: String): MessageDigest =
        when (name) {
            ChecksumAlgorithmNames.MD5 -> MessageDigest.getInstance("MD5")
            ChecksumAlgorithmNames.SHA1 -> MessageDigest.getInstance("SHA-1")
            else -> throw NoSuchElementException(name)
        }

    fun computeHash(
        algorithm: String,
        stream: InputStream,
    ): String {
        val digest = createHashAlgorithmFromName(algorithm)
        val buffer = ByteArray(65536)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        return HashHelper.toHexString(digest.digest())
    }

    fun computeMD5(stream: InputStream): String = computeHash(ChecksumAlgorithmNames.MD5, stream)

    fun computeSHA1(stream: InputStream): String = computeHash(ChecksumAlgorithmNames.SHA1, stream)

    suspend fun computeHashAsync(
        algorithm: String,
        stream: InputStream,
    ): String =
        withContext(Dispatchers.IO) {
            val digest = createHashAlgorithmFromName(algorithm)
            val buffer = ByteArray(65536)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = stream.read(buffer)
                currentCoroutineContext().ensureActive()
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            HashHelper.toHexString(digest.digest())
        }
}
