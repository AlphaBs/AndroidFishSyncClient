package io.github.alphabs.fishsync.sample

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.alphabs.fishsync.android.AndroidStorage
import io.github.alphabs.fishsync.core.storage.SyncPlan
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var model: SyncViewModel
    private lateinit var layout: LinearLayout
    private lateinit var server: EditText
    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var buckets: Spinner
    private lateinit var folder: TextView
    private lateinit var push: Switch
    private lateinit var execute: Button
    private lateinit var cancel: Button
    private lateinit var status: TextView

    private val controls = mutableListOf<View>()
    private var displayedBuckets = emptyList<String>()
    private val picker =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            ::handleFolderSelection,
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[SyncViewModel::class.java]

        createContent()
        createLoginControls()
        createFolderControls()
        createSyncControls()
        observeState()
    }

    private fun createContent() {
        layout =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 48, 24, 24)
            }
        val scroll = ScrollView(this).apply { addView(layout) }
        setContentView(scroll)

        addLabel("FishSync 테스트")
        addLabel(
            "PULL은 서버 → 폴더, PUSH는 폴더 → 서버입니다. " +
                "전체 동기화는 대상에만 있는 파일을 삭제합니다.",
        )
    }

    private fun createLoginControls() {
        server = addInput("서버 API URL (https://…/api)")
        username = addInput("사용자 이름")
        password = addInput("비밀번호", password = true)
        addButton("로그인 · 버킷 조회", ::login)

        buckets = Spinner(this)
        layout.addView(buckets)
        controls.add(buckets)
    }

    private fun login() {
        val serverUrl =
            server.text
                .toString()
                .trim()
        val accountName =
            username.text
                .toString()
        val accountPassword =
            password.text
                .toString()

        model.login(serverUrl, accountName, accountPassword)
        password.text
            .clear()
    }

    private fun createFolderControls() {
        folder = addLabel("")
        showFolder()

        addButton("대상 폴더 선택") {
            val permissionFlags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(permissionFlags)
            picker.launch(intent)
        }

        addButton("앱 전용 폴더 사용") {
            model.selectFolder(null)
            showFolder()
        }
    }

    private fun handleFolderSelection(result: ActivityResult) {
        val data = result.data
        val uri = data?.data
        if (result.resultCode != RESULT_OK || uri == null) {
            return
        }

        try {
            AndroidStorage.retainFolder(this, uri, data.flags)
            model.selectFolder(uri)
            showFolder()
        } catch (_: Exception) {
            status.text = "읽기·쓰기 권한을 유지할 수 없는 폴더입니다. 다른 폴더를 선택하세요."
        }
    }

    private fun showFolder() {
        folder.text = "대상: " + (model.folderUri ?: "앱 전용 폴더")
    }

    private fun createSyncControls() {
        push = Switch(this).apply { text = "PUSH (끄면 PULL)" }
        layout.addView(push)
        controls.add(push)

        addButton("변경 목록 비교") {
            selectedBucket()?.let { model.compare(it, push.isChecked) }
        }
        execute = addButton("동기화 실행", ::confirmSynchronization)
        cancel =
            Button(this).apply {
                text = "작업 취소"
                setOnClickListener { model.cancel() }
            }
        layout.addView(cancel)
        status = addLabel("")
    }

    private fun selectedBucket(): String? {
        val bucket =
            buckets.selectedItem
                ?.toString() ?: return null
        return bucket.takeIf { it.isNotBlank() }
    }

    private fun confirmSynchronization() {
        val bucket = selectedBucket() ?: return
        val plan = model.state.value.preview ?: return
        val title = if (push.isChecked) "서버에 반영" else "폴더에 반영"

        AlertDialog
            .Builder(this)
            .setTitle(title)
            .setMessage(confirmationMessage(bucket, plan))
            .setNegativeButton("취소", null)
            .setPositiveButton("실행") { _, _ -> model.execute(bucket, push.isChecked) }
            .show()
    }

    private fun confirmationMessage(
        bucket: String,
        plan: SyncPlan,
    ): String {
        val target = model.folderUri ?: "앱 전용 폴더"
        return listOf(
            "버킷: $bucket",
            "대상: $target",
            "추가 ${plan.added.size}, 수정 ${plan.updated.size}, 삭제 ${plan.deleted.size}",
            "삭제와 덮어쓰기를 포함해 실행하시겠습니까?",
        ).joinToString("\n")
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state
                    .collect { state -> renderState(state) }
            }
        }
    }

    private fun renderState(state: ScreenState) {
        if (displayedBuckets != state.buckets) {
            displayedBuckets = state.buckets
            buckets.adapter =
                ArrayAdapter(
                    this,
                    android.R.layout.simple_spinner_dropdown_item,
                    displayedBuckets,
                )
        }

        controls.forEach { it.isEnabled = !state.busy }
        execute.isEnabled = !state.busy && state.preview
            ?.hasChanges == true
        cancel.isEnabled = state.busy
        status.text = state.message
    }

    private fun addLabel(text: String): TextView {
        val label = TextView(this).apply { this.text = text }
        layout.addView(label)
        return label
    }

    private fun addInput(
        hint: String,
        password: Boolean = false,
    ): EditText {
        val input =
            EditText(this).apply {
                this.hint = hint
                setSingleLine()
                isSaveEnabled = false

                if (password) {
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
            }

        layout.addView(input)
        controls.add(input)
        return input
    }

    private fun addButton(
        text: String,
        action: () -> Unit,
    ): Button {
        val button =
            Button(this).apply {
                this.text = text
                setOnClickListener { action() }
            }

        layout.addView(button)
        controls.add(button)
        return button
    }
}
