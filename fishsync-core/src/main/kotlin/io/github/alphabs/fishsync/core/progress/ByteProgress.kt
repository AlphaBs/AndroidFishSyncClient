package io.github.alphabs.fishsync.core.progress

data class ByteProgress(
    val totalBytes: Long = 0,
    val progressedBytes: Long = 0,
) {
    fun getRatio(): Double = if (totalBytes == 0L) 0.0 else progressedBytes.toDouble() / totalBytes

    operator fun plus(other: ByteProgress) =
        ByteProgress(
            totalBytes + other.totalBytes,
            progressedBytes + other.progressedBytes,
        )

    operator fun minus(other: ByteProgress) =
        ByteProgress(
            totalBytes - other.totalBytes,
            progressedBytes - other.progressedBytes,
        )
}
