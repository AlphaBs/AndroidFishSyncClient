package io.github.alphabs.fishsync.sample

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.alphabs.fishsync.android.*
import io.github.alphabs.fishsync.client.*
import io.github.alphabs.fishsync.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class ScreenState(val busy: Boolean = false, val message: String = "서버 로그인과 대상 폴더를 선택하세요.", val buckets: List<String> = emptyList(), val preview: SyncPlan? = null)
class SyncViewModel(application: Application) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(ScreenState())
    val state = mutable.asStateFlow()
    private var operation: Job? = null
    private var client: FishSyncClient? = null
    private var token: String? = null
    private var previewKey: String? = null
    private val prefs = application.getSharedPreferences("selection", 0)
    var folderUri: String? = prefs.getString("folder", null)
        private set
    private fun storage(): SyncStorage = folderUri?.let { SafSyncStorage(getApplication(), Uri.parse(it)) } ?: AndroidStorage.appFiles(getApplication())
    fun selectFolder(uri: Uri?) {
        if (mutable.value.busy) return
        folderUri = uri?.toString()
        prefs.edit().apply { if (uri == null) remove("folder") else putString("folder", uri.toString()) }.apply()
        mutable.value = mutable.value.copy(preview = null, message = "대상 폴더를 변경했습니다.")
    }
    private fun runTask(task: suspend () -> Unit) {
        if (operation?.isActive == true) return
        operation = viewModelScope.launch {
            mutable.value = mutable.value.copy(busy = true)
            try { task() }
            catch (cancel: CancellationException) { mutable.value = mutable.value.copy(message = "취소됨. 일부 변경은 적용됐을 수 있습니다.", preview = null); throw cancel }
            catch (failure: Exception) { mutable.value = mutable.value.copy(message = "실패: ${failure.message ?: failure.javaClass.simpleName}", preview = null) }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
    fun login(url: String, username: String, password: String) = runTask {
        client = null; token = null
        val next = FishSyncClient(url, { token })
        token = next.login(username, password)
        val buckets = next.listBuckets()
        client = next
        mutable.value = ScreenState(true, "로그인 완료. 버킷과 폴더를 선택하고 비교하세요.", buckets)
    }
    fun compare(bucket: String, push: Boolean) = runTask {
        val api = client ?: error("로그인이 필요합니다")
        val target = storage()
        val plan = if (push) api.comparePush(bucket, target) else api.comparePull(bucket, target)
        previewKey = "$bucket|${target.identity}|$push"
        mutable.value = mutable.value.copy(preview = plan, message = "추가 ${plan.added.size}, 수정 ${plan.updated.size}, 삭제 ${plan.deleted.size}, 동일 ${plan.identical.size}\n" +
            (plan.added.map { "+ $it" } + plan.updated.map { "~ $it" } + plan.deleted.map { "- $it" }).take(200).joinToString("\n"))
    }
    fun execute(bucket: String, push: Boolean) = runTask {
        val api = client ?: error("로그인이 필요합니다")
        val target = storage()
        val preview = mutable.value.preview ?: error("먼저 비교하세요")
        require(previewKey == "$bucket|${target.identity}|$push") { "선택이 변경됐습니다. 다시 비교하세요." }
        coroutineScope {
            val progress = launch { api.progress.collect { p -> mutable.value = mutable.value.copy(message = "${p.phase}: ${p.path} (${p.bytes} / ${p.total ?: "?"})") } }
            try { if (push) {
            api.push(bucket, target, File(getApplication<Application>().cacheDir, "uploads"), expectedPlan = preview)
        } else { api.pull(bucket, target, expectedPlan = preview) }
            } finally { progress.cancel() }
        }
        mutable.value = mutable.value.copy(message = "동기화 완료", preview = null)
    }
    fun cancel() { operation?.cancel() }
}
