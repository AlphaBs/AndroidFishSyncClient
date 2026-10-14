package io.github.alphabs.fishsync.sample

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.alphabs.fishsync.android.AndroidStorage
import io.github.alphabs.fishsync.android.SafSyncStorage
import io.github.alphabs.fishsync.client.FishSyncClient
import io.github.alphabs.fishsync.core.SyncPlan
import io.github.alphabs.fishsync.core.SyncProgress
import io.github.alphabs.fishsync.core.SyncStorage
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
    val preview: SyncPlan? = null,
)

class SyncViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(ScreenState())
    val state = mutable.asStateFlow()

    private var operation: Job? = null
    private var client: FishSyncClient? = null
    private var token: String? = null
    private var previewKey: String? = null
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
                preview = null,
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
                message = "로그인 완료. 버킷과 폴더를 선택하고 비교하세요.",
                buckets = buckets,
            )
    }

    fun compare(
        bucket: String,
        push: Boolean,
    ) = runTask {
        val api = client ?: error("로그인이 필요합니다")
        val target = storage()
        val plan =
            if (push) {
                api.comparePush(bucket, target)
            } else {
                api.comparePull(bucket, target)
            }

        previewKey = "$bucket|${target.identity}|$push"
        mutable.value =
            mutable.value.copy(
                preview = plan,
                message = previewMessage(plan),
            )
    }

    fun execute(
        bucket: String,
        push: Boolean,
    ) = runTask {
        val api = client ?: error("로그인이 필요합니다")
        val target = storage()
        val preview = mutable.value.preview ?: error("먼저 비교하세요")
        require(previewKey == "$bucket|${target.identity}|$push") {
            "선택이 변경됐습니다. 다시 비교하세요."
        }

        synchronize(api, bucket, target, preview, push)
        mutable.value =
            mutable.value
                .copy(message = "동기화 완료", preview = null)
    }

    fun cancel() {
        operation?.cancel()
    }

    private fun storage(): SyncStorage {
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
                            preview = null,
                        )
                    throw cancel
                } catch (failure: Exception) {
                    val detail = failure.message ?: failure.javaClass.simpleName
                    mutable.value =
                        mutable.value
                            .copy(message = "실패: $detail", preview = null)
                } finally {
                    mutable.value =
                        mutable.value
                            .copy(busy = false)
                }
            }
    }

    private fun previewMessage(plan: SyncPlan): String {
        val changes = mutableListOf<String>()
        changes.addAll(
            plan.added
                .map { "+ $it" },
        )
        changes.addAll(
            plan.updated
                .map { "~ $it" },
        )
        changes.addAll(
            plan.deleted
                .map { "- $it" },
        )

        val details =
            changes
                .take(200)
                .joinToString("\n")
        val summary =
            "추가 ${plan.added.size}, 수정 ${plan.updated.size}, " +
                "삭제 ${plan.deleted.size}, 동일 ${plan.identical.size}"

        return "$summary\n$details"
    }

    private suspend fun synchronize(
        api: FishSyncClient,
        bucket: String,
        target: SyncStorage,
        preview: SyncPlan,
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
                api.push(bucket, target, scratch, expectedPlan = preview)
            } else {
                api.pull(bucket, target, expectedPlan = preview)
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
