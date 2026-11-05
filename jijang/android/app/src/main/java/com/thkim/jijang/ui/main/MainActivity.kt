package com.thkim.jijang.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupWindow
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.thkim.jijang.JijangApp
import com.thkim.jijang.R
import com.thkim.jijang.data.excel.CaseTemplateStore
import com.thkim.jijang.databinding.ActivityMainBinding
import com.thkim.jijang.databinding.DialogTemplateManageBinding
import com.thkim.jijang.databinding.PopupMainMenuBinding
import com.thkim.jijang.ui.casedetail.CaseDetailActivity
import com.thkim.jijang.ui.priceupload.PriceUploadActivity
import com.thkim.jijang.util.UpdateManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private val adapter = SiteCaseAdapter(onClick = { case ->
        startActivity(Intent(this, CaseDetailActivity::class.java).apply {
            putExtra(CaseDetailActivity.EXTRA_SITE_CASE_ID, case.id)
        })
    })

    private val pickTemplateLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) copyTemplate(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val app = application as JijangApp
        viewModel = ViewModelProvider(this, MainViewModel.Factory(app.database))[MainViewModel::class.java]

        binding.recyclerCases.layoutManager = LinearLayoutManager(this)
        binding.recyclerCases.adapter = adapter

        binding.fabAddCase.setOnClickListener { showAddCaseDialog() }

        binding.buttonMenu.setOnClickListener { showMenu(it) }

        // 시작할 때 조용히 새 버전 확인 (인터넷이 없거나 실패하면 아무 안내 없이 넘어감)
        if (savedInstanceState == null) UpdateManager.check(this, manual = false)

        lifecycleScope.launch {
            viewModel.cases.collectLatest { cases ->
                adapter.submitList(cases)
                binding.emptyState.visibility = if (cases.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
    }

    /**
     * 시스템 `PopupMenu`는 삼성 One UI 등 일부 OEM 스킨에서 텍스트가 배경색과 같아져
     * "빈 박스"로 보이거나(테마 오버레이로 시도) 아예 안 뜨는(테마 계열이 안 맞아 인플레이트
     * 실패) 문제가 계속 보고됐다. 두 번 다른 테마로 고쳐봐도 기기별로 재현이 안 돼 정확한
     * 원인 확인이 어려워서, 아예 시스템 팝업 대신 우리가 직접 만든 레이아웃을 `PopupWindow`로
     * 띄우는 방식으로 바꿨다 — 색상을 전부 우리가 정하므로 기기/OEM 테마 스킨의 영향을
     * 받지 않는다.
     */
    private fun showMenu(anchor: android.view.View) {
        val popupBinding = PopupMainMenuBinding.inflate(layoutInflater)
        val popupWindow = PopupWindow(
            popupBinding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow.isOutsideTouchable = true
        popupWindow.elevation = 12f

        popupBinding.rowPriceUpload.setOnClickListener {
            popupWindow.dismiss()
            startActivity(Intent(this, PriceUploadActivity::class.java))
        }
        popupBinding.rowTemplateUpload.setOnClickListener {
            popupWindow.dismiss()
            showTemplateDialog()
        }
        popupBinding.rowUpdate.setOnClickListener {
            popupWindow.dismiss()
            UpdateManager.check(this, manual = true)
        }

        popupWindow.showAsDropDown(anchor)
    }

    /** 점3개 메뉴에서 "엑셀 템플릿 업로드"를 누르면 바로 파일 선택기로 넘어가던 것을,
     * 현재 템플릿 상태를 먼저 보여주는 창으로 바꿨다 — 지금 등록된 템플릿이 있는지,
     * 언제 올린 건지 확인한 뒤에 바꿀 수 있게 한다. */
    private fun showTemplateDialog() {
        val dialogBinding = DialogTemplateManageBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setNegativeButton("닫기", null)
            .create()

        fun refreshStatus() {
            val file = CaseTemplateStore.templateFile(this)
            dialogBinding.textTemplateStatus.text = if (file.exists()) {
                val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA).format(Date(file.lastModified()))
                "현재 템플릿이 등록되어 있습니다.\n마지막 업데이트: $date"
            } else {
                "아직 등록된 템플릿이 없습니다.\n엑셀 내보내기 전에 SKT 표준 양식(xlsx)을 선택해주세요."
            }
        }
        refreshStatus()

        dialogBinding.buttonSelectTemplate.setOnClickListener { pickTemplateLauncher.launch("*/*") }
        pendingRefreshTemplateStatus = ::refreshStatus
        dialog.setOnDismissListener { pendingRefreshTemplateStatus = null }
        dialog.show()
    }

    private var pendingRefreshTemplateStatus: (() -> Unit)? = null

    private fun copyTemplate(uri: Uri) {
        lifecycleScope.launch {
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    CaseTemplateStore.templateFile(this@MainActivity).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Toast.makeText(this@MainActivity, "템플릿이 저장되었습니다", Toast.LENGTH_SHORT).show()
                pendingRefreshTemplateStatus?.invoke()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "템플릿 저장 실패: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showAddCaseDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.new_case_name_hint)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.add_case)
            .setView(input)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                viewModel.addCase(input.text.toString())
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }
}
