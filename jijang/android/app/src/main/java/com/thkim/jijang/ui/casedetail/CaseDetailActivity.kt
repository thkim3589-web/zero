package com.thkim.jijang.ui.casedetail

import android.content.Intent
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.thkim.jijang.JijangApp
import com.thkim.jijang.R
import com.thkim.jijang.data.excel.CaseTemplateStore
import com.thkim.jijang.data.excel.XlsxExporter
import com.thkim.jijang.data.model.RouteType
import com.thkim.jijang.databinding.ActivityCaseDetailBinding
import com.thkim.jijang.databinding.ItemMenuRowBinding
import com.thkim.jijang.ui.checklist.ChecklistEditActivity
import com.thkim.jijang.ui.diagram.DiagramEditActivity
import com.thkim.jijang.ui.photo.PhotoListActivity
import com.thkim.jijang.ui.routesegment.RouteSegmentEditActivity
import com.thkim.jijang.ui.workquantity.WorkQuantityEditActivity
import kotlinx.coroutines.launch
import java.io.File

class CaseDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SITE_CASE_ID = "extra_site_case_id"
    }

    private lateinit var binding: ActivityCaseDetailBinding
    private var effectiveYear: Int = -1
    private var caseName: String = "지장이설"
    private var pendingExportSiteCaseId: Long = -1

    private val pickTemplateLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) copyTemplateThenExport(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCaseDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val siteCaseId = intent.getLongExtra(EXTRA_SITE_CASE_ID, -1)
        require(siteCaseId >= 0) { "EXTRA_SITE_CASE_ID required" }

        val app = application as JijangApp
        lifecycleScope.launch {
            val case = app.database.siteCaseDao().getById(siteCaseId)
            binding.textCaseName.text = case?.caseName ?: ""
            binding.textSiteAddress.text = case?.siteAddress?.ifBlank { "주소 미입력" } ?: "주소 미입력"
            effectiveYear = case?.effectiveYear ?: -1
            caseName = case?.caseName?.ifBlank { null } ?: "지장이설"
            com.thkim.jijang.ui.common.StatusChip.apply(binding.textStatusChip, case?.status ?: "DRAFT")
        }

        binding.toolbar.title = "현장 상세"

        setupRow(binding.rowChecklist, R.drawable.ic_menu_checklist, R.color.primary, "체크리스트 입력", "ENG시트 전체 항목 입력")
        setupRow(binding.rowPhotos, R.drawable.ic_menu_camera, R.color.badge_teal, "현장 사진 관리", "촬영 · 첨부 · 캡션 입력")
        setupRow(binding.rowWorkQuantity, R.drawable.ic_menu_calculator, R.color.accent, "원가계산 수량 입력", "단가 마스터에서 골라 담기")
        setupRow(binding.rowRouteSegments, R.drawable.ic_menu_route, R.color.badge_purple, "구간별 상세 입력", "전주 구간 · 접속 · 안전조치")
        setupRow(binding.rowDiagramBefore, R.drawable.ic_menu_map, R.color.badge_blue, "개황도(전) 편집", "이설 전 경로 그리기")
        setupRow(binding.rowDiagramAfter, R.drawable.ic_menu_map, R.color.badge_blue, "개황도(후) 편집", "이설 후 경로 그리기")
        setupRow(binding.rowExport, R.drawable.ic_menu_export, R.color.status_ok, "엑셀 내보내기", "정산 엑셀 생성 · 공유")

        binding.buttonEditChecklist.setOnClickListener { openChecklist(siteCaseId) }
        binding.buttonManagePhotos.setOnClickListener { openPhotos(siteCaseId) }
        binding.buttonWorkQuantity.setOnClickListener { openWorkQuantity(siteCaseId) }
        binding.buttonRouteSegments.setOnClickListener { openRouteSegments(siteCaseId) }
        binding.buttonEditBefore.setOnClickListener { openDiagram(siteCaseId, RouteType.BEFORE) }
        binding.buttonEditAfter.setOnClickListener { openDiagram(siteCaseId, RouteType.AFTER) }
        binding.buttonExportExcel.setOnClickListener { exportExcel(siteCaseId) }
    }

    private fun setupRow(row: ItemMenuRowBinding, iconRes: Int, badgeColorRes: Int, title: String, subtitle: String) {
        row.imageIcon.setImageResource(iconRes)
        row.imageIcon.background.mutate().setColorFilter(ContextCompat.getColor(this, badgeColorRes), PorterDuff.Mode.SRC_IN)
        row.textTitle.text = title
        row.textSubtitle.text = subtitle
    }


    private fun exportExcel(siteCaseId: Long) {
        val templateFile = CaseTemplateStore.templateFile(this)
        if (!CaseTemplateStore.isAvailable(this)) {
            pendingExportSiteCaseId = siteCaseId
            AlertDialog.Builder(this)
                .setTitle("엑셀 템플릿 필요")
                .setMessage("정산 엑셀을 만들려면 SKT 표준 양식(xlsx)이 먼저 필요합니다. 지금 선택하시겠습니까?")
                .setPositiveButton("템플릿 선택") { _, _ -> pickTemplateLauncher.launch("*/*") }
                .setNegativeButton("취소", null)
                .show()
            return
        }
        lifecycleScope.launch {
            try {
                val outDir = File(cacheDir, "export").apply { mkdirs() }
                val outputFile = File(outDir, "${caseName}.xlsx")
                XlsxExporter.export(
                    db = (application as JijangApp).database,
                    siteCaseId = siteCaseId,
                    templateFile = templateFile,
                    outputFile = outputFile
                )
                shareFile(outputFile)
            } catch (e: Exception) {
                Toast.makeText(this@CaseDetailActivity, "엑셀 생성 실패: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun copyTemplateThenExport(uri: Uri) {
        lifecycleScope.launch {
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    CaseTemplateStore.templateFile(this@CaseDetailActivity).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Toast.makeText(this@CaseDetailActivity, "템플릿이 저장되었습니다", Toast.LENGTH_SHORT).show()
                if (pendingExportSiteCaseId >= 0) exportExcel(pendingExportSiteCaseId)
            } catch (e: Exception) {
                Toast.makeText(this@CaseDetailActivity, "템플릿 저장 실패: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "엑셀 공유"))
    }

    private fun openWorkQuantity(siteCaseId: Long) {
        val intent = Intent(this, WorkQuantityEditActivity::class.java).apply {
            putExtra(WorkQuantityEditActivity.EXTRA_SITE_CASE_ID, siteCaseId)
            putExtra(WorkQuantityEditActivity.EXTRA_EFFECTIVE_YEAR, effectiveYear)
        }
        startActivity(intent)
    }

    private fun openRouteSegments(siteCaseId: Long) {
        val intent = Intent(this, RouteSegmentEditActivity::class.java).apply {
            putExtra(RouteSegmentEditActivity.EXTRA_SITE_CASE_ID, siteCaseId)
        }
        startActivity(intent)
    }

    private fun openChecklist(siteCaseId: Long) {
        val intent = Intent(this, ChecklistEditActivity::class.java).apply {
            putExtra(ChecklistEditActivity.EXTRA_SITE_CASE_ID, siteCaseId)
        }
        startActivity(intent)
    }

    private fun openPhotos(siteCaseId: Long) {
        val intent = Intent(this, PhotoListActivity::class.java).apply {
            putExtra(PhotoListActivity.EXTRA_SITE_CASE_ID, siteCaseId)
        }
        startActivity(intent)
    }

    private fun openDiagram(siteCaseId: Long, routeType: RouteType) {
        val intent = Intent(this, DiagramEditActivity::class.java).apply {
            putExtra(DiagramEditActivity.EXTRA_SITE_CASE_ID, siteCaseId)
            putExtra(DiagramEditActivity.EXTRA_ROUTE_TYPE, routeType.name)
        }
        startActivity(intent)
    }
}
