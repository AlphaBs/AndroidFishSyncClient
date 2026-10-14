package io.github.alphabs.fishsync.core.syncer

enum class SyncAction { FullSync, UpdateOnly, InstallOnly, Exclude }

enum class SyncCondition { Always, OnNewVersion }

enum class Comparison { Size, Checksum, SizeAndChecksum }

data class SyncContext(
    val isNewVersion: Boolean = false,
    val isForced: Boolean = false,
)

data class SyncRule(
    val action: SyncAction,
    val pattern: String = "**",
    val condition: SyncCondition = SyncCondition.Always,
    val comparison: Comparison = Comparison.Checksum,
)
