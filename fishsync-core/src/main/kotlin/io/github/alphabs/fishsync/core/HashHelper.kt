package io.github.alphabs.fishsync.core

object HashHelper {
    fun toHexString(data: ByteArray): String =
        data.joinToString("") {
            "%02x".format(it.toInt() and 255)
        }
}
