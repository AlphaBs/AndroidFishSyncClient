package io.github.alphabs.fishsync.core.syncer

import io.github.alphabs.fishsync.core.files.SyncFilePair
import io.github.alphabs.fishsync.core.files.WritableSyncStorage
import io.github.alphabs.fishsync.core.progress.SyncProgress

/**
 * 하나의 파일을 검증하고 적용한다.
 * 구현체는 임시 쓰기, 크기·체크섬 검증, 대상 변경 검사와 취소를 보장해야 한다.
 * commit 직후 자원 정리 전에 onApplied를 한 번 호출해 부분 적용 결과를 보존한다.
 * onApplied에는 적용한 바이트 수를 전달하며, 콜백은 즉시 반환해야 한다.
 */
interface SyncFilePairSyncer {
    suspend fun sync(
        pair: SyncFilePair,
        storage: WritableSyncStorage,
        expectedTargetChecksum: String?,
        onProgress: (SyncProgress) -> Unit,
        onApplied: (Long) -> Unit,
    )
}
