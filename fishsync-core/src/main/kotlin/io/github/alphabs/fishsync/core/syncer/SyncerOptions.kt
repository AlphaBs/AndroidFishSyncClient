package io.github.alphabs.fishsync.core.syncer

import io.github.alphabs.fishsync.core.progress.FileProgressEvent
import io.github.alphabs.fishsync.core.progress.SyncFileByteProgress

data class SyncerOptions(
    val rules: List<SyncRule>? = null,
    val context: SyncContext? = SyncContext(),
    val fileProgress: ((FileProgressEvent) -> Unit)? = null,
    val byteProgress: ((SyncFileByteProgress) -> Unit)? = null,
    /** null이면 경로 비교 옵션을 따르며 파일 경로 동일성에는 영향을 주지 않는다. */
    val rulePatternCaseInsensitive: Boolean? = null,
)
