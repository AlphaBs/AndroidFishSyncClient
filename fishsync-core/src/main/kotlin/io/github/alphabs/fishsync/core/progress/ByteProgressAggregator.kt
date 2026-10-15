package io.github.alphabs.fishsync.core.progress

open class ByteProgressAggregator(
    private val reporter: (ByteProgress) -> Unit,
) {
    private var currentProgress = ByteProgress()

    fun report(value: ByteProgress) {
        if (value.totalBytes == 0L && value.progressedBytes == 0L) return
        currentProgress += value
        reporter(currentProgress)
    }
}
