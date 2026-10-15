package io.github.alphabs.fishsync.core.internals

internal class SyncProgress<T>(
    private val action: (T) -> Unit,
) {
    fun report(value: T) {
        action(value)
    }
}
