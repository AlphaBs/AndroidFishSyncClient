package io.github.alphabs.fishsync.sample

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.alphabs.fishsync.android.AndroidStorage
import io.github.alphabs.fishsync.android.SafSyncStorage
import io.github.alphabs.fishsync.client.FishSyncClient
import io.github.alphabs.fishsync.core.storage.SyncProgress
import io.github.alphabs.fishsync.core.storage.WritableSyncStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class ScreenState(
    val busy: Boolean = false,
    val message: String = "서버 로그인과 대상 폴더를 선택하세요.",
    val buckets: List<String> = emptyList(),
)

class SyncViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(ScreenState())
    val state = mutable.asStateFlow()

    private var operation: Job? = null
    private var client: FishSyncClient? = null
    private var token: String? = null
    private val prefs = application.getSharedPreferences("selection", 0)

    var folderUri: String? = prefs.getString("folder", null)
        private set

    fun selectFolder(uri: Uri?) {
        if (mutable.value.busy) {
            return
        }

        folderUri = uri?.toString()
        val editor = prefs.edit()
        if (uri == null) {
            editor.remove("folder")
        } else {
            editor.putString("folder", uri.toString())
        }
        editor.apply()

        mutable.value =
            mutable.value.copy(
                message = "대상 폴더를 변경했습니다.",
            )
    }

    fun login(
        url: String,
        username: String,
        password: String,
    ) = runTask {
        client = null
        token = null

        val next = FishSyncClient(url, { token })
        token = next.login(username, password)
        val buckets = next.listBuckets()
        client = next

        mutable.value =
            ScreenState(
                busy = true,
                message = "로그인 완료. 버킷과 폴더를 선택하고 동기화를 실행하세요.",
                buckets = buckets,
            )
    }

    fun execute(
        bucket: String,
        push: Boolean,
    ) = runTask {
        val api = client ?: error("로그인이 필요합니다")
        val target = storage()
        synchronize(api, bucket, target, push)
        mutable.value =
            mutable.value
                .copy(message = "동기화 완료")
    }

    fun cancel() {
        operation?.cancel()
    }

    private fun storage(): WritableSyncStorage {
        val uri = folderUri
        if (uri == null) {
            return AndroidStorage.appFiles(getApplication())
        }

        return SafSyncStorage(getApplication(), Uri.parse(uri))
    }

    private fun runTask(task: suspend () -> Unit) {
        if (operation?.isActive == true) {
            return
        }

        operation =
            viewModelScope.launch {
                mutable.value =
                    mutable.value
                        .copy(busy = true)

                try {
                    task()
                } catch (cancel: CancellationException) {
                    mutable.value =
                        mutable.value.copy(
                            message = "취소됨. 일부 변경은 적용됐을 수 있습니다.",
                        )
                    throw cancel
                } catch (failure: Exception) {
                    val detail = failure.message ?: failure.javaClass.simpleName
                    mutable.value =
                        mutable.value
                            .copy(message = "실패: $detail")
                } finally {
                    mutable.value =
                        mutable.value
                            .copy(busy = false)
                }
            }
    }

    private suspend fun synchronize(
        api: FishSyncClient,
        bucket: String,
        target: WritableSyncStorage,
        push: Boolean,
    ) = coroutineScope {
        val progress =
            launch {
                api.progress
                    .collect { updateProgress(it) }
            }

        try {
            if (push) {
                val scratch = File(getApplication<Application>().cacheDir, "uploads")
                api.push(bucket, target, scratch)
            } else {
                api.pull(bucket, target)
            }
        } finally {
            progress.cancel()
        }
    }

    private fun updateProgress(progress: SyncProgress) {
        val total = progress.total ?: "?"
        mutable.value =
            mutable.value.copy(
                message = "${progress.phase}: ${progress.path} (${progress.bytes} / $total)",
            )
    }
}
