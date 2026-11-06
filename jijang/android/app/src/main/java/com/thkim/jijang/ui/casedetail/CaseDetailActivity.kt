package com.thkim.jijang.ui.casedetail

import android.content.Intent
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.tabs.TabLayout
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
    private var currentCaseId: Long = -1
    private var selectedTab = 0

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
        currentCaseId = siteCaseId
        selectedTab = savedInstanceState?.getInt("tab") ?: 0
        setupTabs()

        setupRow(binding.rowChecklist, R.drawable.ic_menu_checklist, R.color.primary, "체크리스트 입력", "ENG시트 전체 항목 입력")
        setupRow(binding.rowPhotos, R.drawable.ic_menu_camera, R.color.badge_teal, "현장 사진 관리", "촬영 · 첨부 · 캡션 입력")
        setupRow(binding.rowWorkQuantity, R.drawable.ic_menu_calculator, R.color.accent, "원가계산 수량 입력", "단가 마스터에서 골라 담기")
        setupRow(binding.rowRouteSegments, R.drawable.ic_menu_route, R.color.badge_purple, "구간별 상세 입력", "전주 구간 · 접속 · 안전조치")
        setupRow(binding.rowDiagramBefore, R.drawable.ic_menu_map, R.color.badge_blue, "개황도(전) 편집", "이설 전 경로 그리기")
        setupRow(binding.rowDiagramAfter, R.drawable.ic_menu_map, R.color.badge_blue, "개황도(후) 편집", "이설 후 경로 그리기")
        setupRow(binding.rowExport, R.drawable.ic_menu_export, R.color.status_ok, "엑셀 내보내기", "정산 엑셀 생성 · 공유")
        binding.rowExport.textTitle.setTextColor(ContextCompat.getColor(this, R.color.on_primary))
        binding.rowExport.textSubtitle.setTextColor(ContextCompat.getColor(this, R.color.on_primary_muted))

        binding.buttonEditChecklist.setOnClickListener { openChecklist(siteCaseId) }
        binding.buttonManagePhotos.setOnClickListener { openPhotos(siteCaseId) }
        binding.buttonWorkQuantity.setOnClickListener { openWorkQuantity(siteCaseId) }
        binding.buttonRouteSegments.setOnClickListener { openRouteSegments(siteCaseId) }
        binding.buttonEditBefore.setOnClickListener { openDiagram(siteCaseId, RouteType.BEFORE) }
        binding.buttonEditAfter.setOnClickListener { openDiagram(siteCaseId, RouteType.AFTER) }
        binding.buttonExportExcel.setOnClickListener { exportExcel(siteCaseId) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", selectedTab)
    }

    override fun onResume() {
        super.onResume()
        if (currentCaseId >= 0) refreshSummary(currentCaseId)
    }

    /** 현장조사 / 도면 / 물량 / 현황 4개 탭 — 같은 화면에서 카드 묶음만 바꿔 보여준다. */
    private fun setupTabs() {
        val groups = listOf(binding.tabSurvey, binding.tabDrawing, binding.tabQuantity, binding.tabStatus)
        listOf("현장조사", "도면", "물량", "현황").forEach { binding.tabLayout.addTab(binding.tabLayout.newTab().setText(it)) }
        fun show(index: Int) {
            selectedTab = index
            groups.forEachIndexed { i, v -> v.visibility = if (i == index) View.VISIBLE else View.GONE }
        }
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = show(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        binding.tabLayout.getTabAt(selectedTab.coerceIn(0, 3))?.select()
        show(selectedTab.coerceIn(0, 3))
    }

    /** DB에서 사진/구간/수량/개황도 현황을 읽어 카드 부제목과 "현황" 탭 요약을 갱신한다. */
    private fun refreshSummary(siteCaseId: Long) {
        lifecycleScope.launch {
            val db = (application as JijangApp).database
            val photos = db.sitePhotoDao().countBySiteCase(siteCaseId)
            val segments = db.routeSegmentDao().getAll(siteCaseId).size
            val labor = db.workQuantityDao().getAll(siteCaseId, "LABOR").size
            val material = db.workQuantityDao().getAll(siteCaseId, "MATERIAL").size
            val hasChecklist = db.checklistDao().getChecklist(siteCaseId) != null
            val beforeImg = db.diagramDao().getBaseMapImage(siteCaseId, RouteType.BEFORE)
            val afterImg = db.diagramDao().getBaseMapImage(siteCaseId, RouteType.AFTER)
            val beforeLines = beforeImg?.let { db.diagramDao().getLines(it.id).size } ?: 0
            val afterLines = afterImg?.let { db.diagramDao().getLines(it.id).size } ?: 0

            binding.rowChecklist.textSubtitle.text = if (hasChecklist) "입력됨 · ENG시트 전체 항목" else "ENG시트 전체 항목 입력"
            binding.rowPhotos.textSubtitle.text = if (photos > 0) "${photos}장 등록됨" else "촬영 · 첨부 · 캡션 입력"
            binding.rowRouteSegments.textSubtitle.text = if (segments > 0) "${segments}개 구간" else "전주 구간 · 접속 · 안전조치"
            binding.rowWorkQuantity.textSubtitle.text =
                if (labor + material > 0) "노무·장비 ${labor}건 · 자재 ${material}건" else "단가 마스터에서 골라 담기"
            binding.rowDiagramBefore.textSubtitle.text = diagramText(beforeImg != null, beforeLines, "이설 전 경로 그리기")
            binding.rowDiagramAfter.textSubtitle.text = diagramText(afterImg != null, afterLines, "이설 후 경로 그리기")

            val box = binding.containerSummary
            box.removeAllViews()
            addSummaryRow(box, "체크리스트", if (hasChecklist) "입력됨" else "미입력", hasChecklist)
            addSummaryRow(box, "현장 사진", if (photos > 0) "${photos}장" else "없음", photos > 0)
            addSummaryRow(box, "구간(기별)", if (segments > 0) "${segments}개" else "없음", segments > 0)
            addSummaryRow(box, "원가계산 수량", if (labor + material > 0) "${labor + material}건" else "없음", labor + material > 0)
            addSummaryRow(box, "개황도(전)", if (beforeImg != null) "선 ${beforeLines}개" else "없음", beforeImg != null)
            addSummaryRow(box, "개황도(후)", if (afterImg != null) "선 ${afterLines}개" else "없음", afterImg != null)
        }
    }

    private fun diagramText(hasImage: Boolean, lines: Int, empty: String) =
        if (!hasImage) empty else if (lines > 0) "이미지 있음 · 선 ${lines}개" else "이미지 있음 · 선 없음"

    private fun addSummaryRow(container: LinearLayout, label: String, value: String, done: Boolean) {
        val dp = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
        }
        row.addView(TextView(this).apply {
            text = if (done) "●" else "○"
            setTextColor(ContextCompat.getColor(context, if (done) R.color.status_ok else R.color.icon_muted))
            textSize = 14f
        })
        row.addView(TextView(this).apply {
            text = label
            textSize = 14.5f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setPadding((10 * dp).toInt(), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            text = value
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, if (done) R.color.text_primary else R.color.text_secondary))
        })
        container.addView(row)
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
