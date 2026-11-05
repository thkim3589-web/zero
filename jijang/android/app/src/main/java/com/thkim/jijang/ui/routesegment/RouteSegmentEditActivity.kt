package com.thkim.jijang.ui.routesegment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.thkim.jijang.JijangApp
import com.thkim.jijang.R
import com.thkim.jijang.data.model.LayingMethod
import com.thkim.jijang.data.model.RouteSegment
import com.thkim.jijang.data.model.SafetyMeasure
import com.thkim.jijang.data.model.SpliceType
import com.thkim.jijang.data.model.StockType
import com.thkim.jijang.data.model.WorkType
import com.thkim.jijang.databinding.ActivityRouteSegmentEditBinding
import com.thkim.jijang.databinding.ItemRouteSegmentRowBinding
import kotlinx.coroutines.launch

private val CORE_COUNT_OPTIONS = listOf(24, 48, 72, 96, 144, 288)

// AC열(케이블종류) — 실기기 캡처로 확인된 값들. 스크롤로 더 있을 수 있어 "직접입력"으로 탈출구를 둔다.
private val CABLE_TYPE_OPTIONS = listOf("Dry", "가공", "배선망", "MSLT", "리본형", "관로", "난연", "세경관로")
private const val CABLE_TYPE_CUSTOM = "직접입력"

/**
 * `기별` 시트 대응 — 전주 구간별 상세 입력 화면 (10단계, 13단계에서 실제 드롭다운
 * 값에 맞춰 필드를 다시 정리함).
 * 구간마다 기본 항목만 항상 보이고, 접속/안전 정보는 그 구간에 실제로 해당
 * 작업이 있을 때만 "+ 접속 정보"/"+ 안전 정보" 버튼으로 펼쳐서 입력한다.
 */
class RouteSegmentEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SITE_CASE_ID = "extra_site_case_id"
    }

    private lateinit var binding: ActivityRouteSegmentEditBinding
    private lateinit var viewModel: RouteSegmentViewModel
    private var siteCaseId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRouteSegmentEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        siteCaseId = intent.getLongExtra(EXTRA_SITE_CASE_ID, -1)
        require(siteCaseId >= 0) { "EXTRA_SITE_CASE_ID required" }

        val app = application as JijangApp
        viewModel = ViewModelProvider(
            this,
            RouteSegmentViewModel.Factory(app.database, siteCaseId)
        )[RouteSegmentViewModel::class.java]

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.buttonAddSegment.setOnClickListener { addRow(null) }
        binding.buttonSave.setOnClickListener { save() }

        lifecycleScope.launch {
            val segments = viewModel.load()
            segments.forEach { addRow(it) }
            renumberRows()
        }
    }

    private fun addRow(segment: RouteSegment?) {
        val row = ItemRouteSegmentRowBinding.inflate(LayoutInflater.from(this), binding.containerSegments, false)

        row.editPoleName.setText(segment?.poleName)
        row.editSegmentLength.setText(segment?.segmentLengthM?.toString())
        row.radioStockExisting.isChecked = segment?.stockType == StockType.STOCK
        row.radioStockNew.isChecked = segment?.stockType != StockType.STOCK
        row.root.setTag(R.id.tagCableType, segment?.cableType)
        row.textCableType.text = segment?.cableType?.takeIf { it.isNotBlank() } ?: "케이블종류 선택"
        row.textCableType.setOnClickListener { pickCableType(row) }

        row.root.setTag(R.id.tagWorkType, segment?.workType ?: WorkType.NEW)
        row.textWorkTypeLabel.text = "작업구분: ${(segment?.workType ?: WorkType.NEW).label}"
        row.textWorkTypeLabel.setOnClickListener { pickWorkType(row) }

        row.root.setTag(R.id.tagCoreCount, segment?.coreCount ?: 0)
        row.textCoreCount.text = segment?.coreCount?.takeIf { it != 0 }?.let { "코어수: ${it}C" } ?: "코어수 선택"
        row.textCoreCount.setOnClickListener { pickCoreCount(row) }

        row.root.setTag(R.id.tagLayingMethod, segment?.layingMethod ?: LayingMethod.AERIAL)
        row.textLayingMethodLabel.text = "포설방식: ${(segment?.layingMethod ?: LayingMethod.AERIAL).label}"
        row.textLayingMethodLabel.setOnClickListener { pickLayingMethod(row) }

        row.root.setTag(R.id.tagSpliceType, segment?.spliceType)
        row.textSpliceTypeLabel.text = segment?.spliceType?.let { "접속구분: ${it.label}" } ?: "접속구분 선택"
        row.textSpliceTypeLabel.setOnClickListener { pickSpliceType(row) }

        row.root.setTag(R.id.tagSafetyMeasure, segment?.safetyMeasure)
        row.textSafetyMeasureLabel.text = segment?.safetyMeasure?.let { "안전조치: ${it.label}" } ?: "안전조치 선택"
        row.textSafetyMeasureLabel.setOnClickListener { pickSafetyMeasure(row) }

        if (segment?.spliceType != null || !segment?.workShift.isNullOrBlank()) {
            row.groupSplice.visibility = View.VISIBLE
            row.buttonToggleSplice.text = "- 접속 정보"
            row.radioShiftNight.isChecked = segment?.workShift == "야간"
            row.radioShiftDay.isChecked = segment?.workShift != "야간"
        }
        if (segment?.safetyMeasure != null) {
            row.groupSafety.visibility = View.VISIBLE
            row.buttonToggleSafety.text = "- 안전 정보"
        }

        row.buttonToggleSplice.setOnClickListener {
            val show = row.groupSplice.visibility != View.VISIBLE
            row.groupSplice.visibility = if (show) View.VISIBLE else View.GONE
            row.buttonToggleSplice.text = if (show) "- 접속 정보" else "+ 접속 정보"
        }
        row.buttonToggleSafety.setOnClickListener {
            val show = row.groupSafety.visibility != View.VISIBLE
            row.groupSafety.visibility = if (show) View.VISIBLE else View.GONE
            row.buttonToggleSafety.text = if (show) "- 안전 정보" else "+ 안전 정보"
        }
        row.buttonDelete.setOnClickListener {
            binding.containerSegments.removeView(row.root)
            renumberRows()
        }

        binding.containerSegments.addView(row.root)
    }

    private fun pickWorkType(row: ItemRouteSegmentRowBinding) {
        val options = WorkType.entries.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("작업구분 선택")
            .setItems(options.map { it.label }.toTypedArray()) { _, which ->
                row.root.setTag(R.id.tagWorkType, options[which])
                row.textWorkTypeLabel.text = "작업구분: ${options[which].label}"
            }
            .show()
    }

    private fun pickCableType(row: ItemRouteSegmentRowBinding) {
        val options = CABLE_TYPE_OPTIONS + CABLE_TYPE_CUSTOM
        AlertDialog.Builder(this)
            .setTitle("케이블종류 선택")
            .setItems(options.toTypedArray()) { _, which ->
                val picked = options[which]
                if (picked == CABLE_TYPE_CUSTOM) {
                    promptCustomCableType(row)
                } else {
                    row.root.setTag(R.id.tagCableType, picked)
                    row.textCableType.text = picked
                }
            }
            .show()
    }

    private fun promptCustomCableType(row: ItemRouteSegmentRowBinding) {
        val input = android.widget.EditText(this)
        input.hint = "케이블 종류 직접 입력"
        val current = row.root.getTag(R.id.tagCableType) as? String
        if (!current.isNullOrBlank() && current !in CABLE_TYPE_OPTIONS) input.setText(current)
        AlertDialog.Builder(this)
            .setTitle("케이블종류 직접입력")
            .setView(input)
            .setPositiveButton("확인") { _, _ ->
                val value = input.text.toString().ifBlank { null }
                row.root.setTag(R.id.tagCableType, value)
                row.textCableType.text = value ?: "케이블종류 선택"
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun pickCoreCount(row: ItemRouteSegmentRowBinding) {
        AlertDialog.Builder(this)
            .setTitle("코어수 선택")
            .setItems(CORE_COUNT_OPTIONS.map { "${it}C" }.toTypedArray()) { _, which ->
                val value = CORE_COUNT_OPTIONS[which]
                row.root.setTag(R.id.tagCoreCount, value)
                row.textCoreCount.text = "코어수: ${value}C"
            }
            .show()
    }

    private fun pickLayingMethod(row: ItemRouteSegmentRowBinding) {
        val options = LayingMethod.entries.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("포설방식 선택")
            .setItems(options.map { it.label }.toTypedArray()) { _, which ->
                row.root.setTag(R.id.tagLayingMethod, options[which])
                row.textLayingMethodLabel.text = "포설방식: ${options[which].label}"
            }
            .show()
    }

    private fun pickSpliceType(row: ItemRouteSegmentRowBinding) {
        val options = SpliceType.entries.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("접속구분 선택")
            .setItems(options.map { it.label }.toTypedArray()) { _, which ->
                row.root.setTag(R.id.tagSpliceType, options[which])
                row.textSpliceTypeLabel.text = "접속구분: ${options[which].label}"
            }
            .show()
    }

    private fun pickSafetyMeasure(row: ItemRouteSegmentRowBinding) {
        val options = SafetyMeasure.entries.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("안전조치 선택")
            .setItems(options.map { it.label }.toTypedArray()) { _, which ->
                row.root.setTag(R.id.tagSafetyMeasure, options[which])
                row.textSafetyMeasureLabel.text = "안전조치: ${options[which].label}"
            }
            .show()
    }

    private fun renumberRows() {
        val container = binding.containerSegments
        for (i in 0 until container.childCount) {
            val row = ItemRouteSegmentRowBinding.bind(container.getChildAt(i))
            row.textSeqLabel.text = "구간 #${i + 1}"
        }
    }

    private fun readRows(): List<RouteSegment> {
        val container = binding.containerSegments
        return (0 until container.childCount).map { i ->
            val row = ItemRouteSegmentRowBinding.bind(container.getChildAt(i))
            RouteSegment(
                siteCaseId = siteCaseId,
                seq = i + 1,
                poleName = row.editPoleName.text.toString(),
                segmentLengthM = row.editSegmentLength.text.toString().toDoubleOrNull() ?: 0.0,
                stockType = if (row.radioStockExisting.isChecked) StockType.STOCK else StockType.NEW,
                workType = row.root.getTag(R.id.tagWorkType) as? WorkType ?: WorkType.NEW,
                cableType = row.root.getTag(R.id.tagCableType) as? String,
                coreCount = row.root.getTag(R.id.tagCoreCount) as? Int ?: 0,
                layingMethod = row.root.getTag(R.id.tagLayingMethod) as? LayingMethod ?: LayingMethod.AERIAL,
                spliceType = if (row.groupSplice.visibility == View.VISIBLE) row.root.getTag(R.id.tagSpliceType) as? SpliceType else null,
                workShift = if (row.groupSplice.visibility == View.VISIBLE) {
                    if (row.radioShiftNight.isChecked) "야간" else "주간"
                } else null,
                safetyMeasure = if (row.groupSafety.visibility == View.VISIBLE) row.root.getTag(R.id.tagSafetyMeasure) as? SafetyMeasure else null
            )
        }
    }

    private fun save() {
        val segments = readRows()
        lifecycleScope.launch {
            viewModel.save(segments)
            Toast.makeText(this@RouteSegmentEditActivity, "저장되었습니다", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
