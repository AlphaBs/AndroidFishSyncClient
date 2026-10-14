package io.github.alphabs.fishsync.sample

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.alphabs.fishsync.android.AndroidStorage
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var model: SyncViewModel
    private lateinit var folder: TextView
    private lateinit var status: TextView
    private val controls = mutableListOf<android.view.View>()
    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val uri = data?.data
        if (result.resultCode == RESULT_OK && uri != null) {
            try { AndroidStorage.retainFolder(this, uri, data.flags); model.selectFolder(uri); showFolder() }
            catch (_: Exception) { status.text = "읽기·쓰기 권한을 유지할 수 없는 폴더입니다. 다른 폴더를 선택하세요." }
        }
    }
    private fun showFolder() { folder.text = "대상: " + (model.folderUri ?: "앱 전용 폴더") }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[SyncViewModel::class.java]
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 48, 24, 24) }
        val scroll = ScrollView(this).apply { addView(layout) }
        setContentView(scroll)
        fun label(text: String) = TextView(this).also { it.text = text; layout.addView(it) }
        fun input(hint: String, password: Boolean = false) = EditText(this).also {
            it.hint = hint; it.setSingleLine(); it.isSaveEnabled = false
            if (password) it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            layout.addView(it); controls.add(it)
        }
        fun button(text: String, action: () -> Unit) = Button(this).also { it.text = text; it.setOnClickListener { action() }; layout.addView(it); controls.add(it) }
        label("FishSync 테스트")
        label("PULL은 서버 → 폴더, PUSH는 폴더 → 서버입니다. 전체 동기화는 대상에만 있는 파일을 삭제합니다.")
        val server = input("서버 API URL (https://…/api)")
        val username = input("사용자 이름")
        val password = input("비밀번호", true)
        button("로그인 · 버킷 조회") { model.login(server.text.toString().trim(), username.text.toString(), password.text.toString()); password.text.clear() }
        val buckets = Spinner(this).also { layout.addView(it); controls.add(it) }
        folder = label(""); showFolder()
        button("대상 폴더 선택") {
            picker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION))
        }
        button("앱 전용 폴더 사용") { model.selectFolder(null); showFolder() }
        val push = Switch(this).apply { text = "PUSH (끄면 PULL)"; layout.addView(this); controls.add(this) }
        fun selected(): String? = buckets.selectedItem?.toString()?.takeIf { it.isNotBlank() }
        button("변경 목록 비교") { selected()?.let { model.compare(it, push.isChecked) } }
        val execute = button("동기화 실행") {
            val bucket = selected() ?: return@button
            val plan = model.state.value.preview ?: return@button
            AlertDialog.Builder(this).setTitle(if (push.isChecked) "서버에 반영" else "폴더에 반영")
                .setMessage("버킷: $bucket\n대상: ${model.folderUri ?: "앱 전용 폴더"}\n추가 ${plan.added.size}, 수정 ${plan.updated.size}, 삭제 ${plan.deleted.size}\n삭제와 덮어쓰기를 포함해 실행하시겠습니까?")
                .setNegativeButton("취소", null).setPositiveButton("실행") { _, _ -> model.execute(bucket, push.isChecked) }.show()
        }
        val cancel = Button(this).also { it.text = "작업 취소"; it.setOnClickListener { model.cancel() }; layout.addView(it) }
        status = label("")
        var displayedBuckets = emptyList<String>()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { state ->
                    if (displayedBuckets != state.buckets) {
                        displayedBuckets = state.buckets
                        buckets.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, displayedBuckets)
                    }
                    controls.forEach { it.isEnabled = !state.busy }
                    execute.isEnabled = !state.busy && state.preview?.hasChanges == true
                    cancel.isEnabled = state.busy
                    status.text = state.message
                }
            }
        }
    }
}
