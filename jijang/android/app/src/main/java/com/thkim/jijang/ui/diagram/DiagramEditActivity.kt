package com.thkim.jijang.ui.diagram

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PointF
import android.graphics.PorterDuff
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.thkim.jijang.JijangApp
import com.thkim.jijang.R
import com.thkim.jijang.data.excel.XlsxReader
import com.thkim.jijang.data.model.MarkerStatus
import com.thkim.jijang.data.model.MarkerType
import com.thkim.jijang.data.model.RouteLine
import com.thkim.jijang.data.model.RouteType
import com.thkim.jijang.databinding.ActivityDiagramEditBinding
import com.thkim.jijang.util.ImageUtil
import kotlinx.coroutines.launch
import java.io.File

/** 카문다 모델러 참고 요청으로 추가한 선 색상 팔레트 — 실제 개황도 범례에서 실제로
 * 쓰이는 색만 추려서, 아무 색이나 고를 수 있게 하는 대신 범례와 어긋나지 않게 한다. */
private val LINE_COLOR_PALETTE = listOf(
    "빨강" to "#FF3B30",
    "파랑" to "#1E88E5",
    "자홍" to "#D81B99",
    "초록" to "#2E7D32",
    "노랑" to "#F5A623",
    "검정" to "#1B2430",
    "주황" to "#E8871E"
)

/**
 * GIS 캡처 이미지를 불러와 그 위에 케이블 경로 선을 그리고 편집하는 화면.
 * 개황도(전)/개황도(후) 각각 별도 화면 인스턴스로 사용한다.
 *
 * 17단계 — 신설/철거 2줄 고정이었던 것을, 케이블 종류별로 몇 개든(5개든 그 이상이든)
 * 선을 추가/삭제할 수 있게 일반화했다. 위쪽 "편집할 선" 칩 목록에서 지금 편집할 선을
 * 고르고, 그 외 나머지 선은 전부 참고용으로 반투명하게 같이 보여준다. 점을 추가할 때
 * "일반/함체/전주" 중 고를 수 있고, 함체·전주는 이미 입력해둔 목록에서 골라 이름을
 * 다시 타이핑하지 않는다.
 */
class DiagramEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SITE_CASE_ID = "extra_site_case_id"
        const val EXTRA_ROUTE_TYPE = "extra_route_type"
    }

    private lateinit var binding: ActivityDiagramEditBinding
    private lateinit var viewModel: DiagramViewModel

    private var lines = mutableListOf<RouteLine>()
    private val pointsByLineId = mutableMapOf<Long, MutableList<EditablePoint>>()
    private var activeLineId: Long = -1
    /** 선이 여러 개 겹치면 마커가 뒤섞여 복잡해 보인다는 피드백 — 확대해서 편집하는 게
     * 기본 해법이지만, 아예 잠깐 다른 선을 안 보이게 할 수도 있게 토글을 뒀다. */
    private var hideOtherLines = false

    private var currentBaseMapImageId: Long = -1

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                val siteCaseId = intent.getLongExtra(EXTRA_SITE_CASE_ID, -1)
                val fileName = "map_${siteCaseId}_${routeType.name}_${System.currentTimeMillis()}.jpg"
                val path = ImageUtil.copyToInternalStorage(this@DiagramEditActivity, uri, fileName)
                viewModel.setBaseMapImage(path)
                loadImageAndLines(path)
            }
        }
    }

    /** 이미 GIS에서 캡처해 엑셀(개황도 시트 등)에 붙여넣어둔 사진이 있으면, 그걸 다시
     * 별도 이미지 파일로 저장해서 갤러리로 불러올 필요 없이 엑셀 파일에서 바로 꺼내
     * 편집할 수 있게 한다 — xlsx는 zip이라 삽입된 그림이 xl/media/ 안에 그대로 들어있다. */
    private val pickExcelLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                try {
                    val tempFile = File(cacheDir, "diagram_import_${System.currentTimeMillis()}.xlsx")
                    contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    val entries = XlsxReader.listMediaEntries(tempFile)
                    when {
                        entries.isEmpty() -> {
                            Toast.makeText(this@DiagramEditActivity, "엑셀 파일에서 이미지를 찾을 수 없습니다", Toast.LENGTH_SHORT).show()
                            tempFile.delete()
                        }
                        entries.size == 1 -> {
                            useMediaEntryAsBaseMap(tempFile, entries[0])
                        }
                        else -> showMediaPickerDialog(tempFile, entries)
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@DiagramEditActivity, "엑셀 파일을 여는 데 실패했습니다: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private val pickLegendLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && currentBaseMapImageId >= 0) {
            lifecycleScope.launch {
                val siteCaseId = intent.getLongExtra(EXTRA_SITE_CASE_ID, -1)
                val fileName = "legend_${siteCaseId}_${routeType.name}_${System.currentTimeMillis()}.jpg"
                val path = ImageUtil.copyToInternalStorage(this@DiagramEditActivity, uri, fileName)
                viewModel.setLegendImage(currentBaseMapImageId, path)
                showLegendThumbnail(path)
            }
        } else if (currentBaseMapImageId < 0) {
            Toast.makeText(this, "먼저 이미지를 불러오세요", Toast.LENGTH_SHORT).show()
        }
    }

    private val routeType: RouteType by lazy {
        RouteType.valueOf(intent.getStringExtra(EXTRA_ROUTE_TYPE) ?: RouteType.BEFORE.name)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDiagramEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val siteCaseId = intent.getLongExtra(EXTRA_SITE_CASE_ID, -1)
        require(siteCaseId >= 0) { "EXTRA_SITE_CASE_ID required" }

        val app = application as JijangApp
        viewModel = ViewModelProvider(
            this,
            DiagramViewModel.Factory(app.database, siteCaseId, routeType)
        )[DiagramViewModel::class.java]

        binding.toolbar.title = if (routeType == RouteType.BEFORE) "개황도(전) 편집" else "개황도(후) 편집"
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.buttonPickImage.setOnClickListener { pickImageLauncher.launch("image/*") }
        binding.buttonPickFromExcel.setOnClickListener { pickExcelLauncher.launch("*/*") }
        binding.buttonClear.setOnClickListener { binding.routeEditorView.clearAll() }
        binding.buttonSave.setOnClickListener { saveAndFinish() }

        binding.buttonPickLegend.setOnClickListener { pickLegendLauncher.launch("image/*") }
        binding.buttonRemoveLegend.setOnClickListener {
            if (currentBaseMapImageId >= 0) {
                lifecycleScope.launch {
                    viewModel.setLegendImage(currentBaseMapImageId, null)
                    hideLegendThumbnail()
                }
            }
        }

        binding.routeEditorView.onPointsChanged = { if (activeLineId >= 0) pointsByLineId[activeLineId] = it.toMutableList() }
        binding.routeEditorView.onEmptyTap = { relPoint -> showAddPointDialog(relPoint) }
        binding.routeEditorView.onPointTap = { index, _ -> showPointActionMenu(index) }

        setupDrawingToolbar()

        lifecycleScope.launch {
            val existing = viewModel.getBaseMapImage()
            if (existing != null) loadImageAndLines(existing.localFilePath)
        }
    }

    private suspend fun loadImageAndLines(path: String) {
        val bitmap = ImageUtil.decodeSampledBitmap(path) ?: run {
            Toast.makeText(this, "이미지를 불러올 수 없습니다", Toast.LENGTH_SHORT).show()
            return
        }
        binding.textNoImage.visibility = android.view.View.GONE
        binding.routeEditorView.setImage(bitmap)

        val image = viewModel.getBaseMapImage() ?: viewModel.setBaseMapImage(path)
        currentBaseMapImageId = image.id
        if (image.legendImagePath != null) showLegendThumbnail(image.legendImagePath) else hideLegendThumbnail()

        lines = viewModel.getLines(image.id).toMutableList()
        if (lines.isEmpty()) {
            // 완전히 새 이미지면 예전처럼 철거/신설 2줄로 시작한다 — 이후 필요한 만큼 추가/삭제 가능.
            lines.add(viewModel.addLine(image.id, "철거", "#FF3B30"))
            lines.add(viewModel.addLine(image.id, "신설", "#1E88E5"))
        }
        pointsByLineId.clear()
        for (line in lines) {
            pointsByLineId[line.id] = viewModel.loadPoints(line.id).toMutableList()
        }
        activeLineId = lines.first().id
        rebuildLineChips()
        applyActiveLineToView()
    }

    /** 선택한 zip 항목(엑셀 안의 그림)을 개황도 배경 이미지로 확정한다. */
    private fun useMediaEntryAsBaseMap(xlsxFile: File, entryName: String) {
        lifecycleScope.launch {
            try {
                val siteCaseId = intent.getLongExtra(EXTRA_SITE_CASE_ID, -1)
                val ext = entryName.substringAfterLast('.', "jpg")
                val destFile = File(filesDir, "map_${siteCaseId}_${routeType.name}_${System.currentTimeMillis()}.$ext")
                destFile.writeBytes(XlsxReader.readMediaBytes(xlsxFile, entryName))
                viewModel.setBaseMapImage(destFile.absolutePath)
                loadImageAndLines(destFile.absolutePath)
            } catch (e: Exception) {
                Toast.makeText(this@DiagramEditActivity, "이미지를 가져오는 데 실패했습니다: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                xlsxFile.delete()
            }
        }
    }

    /** 엑셀 안에 그림이 여러 장이면(개황도 전/후가 같은 파일에 다 있는 경우 등) 썸네일로
     * 보여주고 고르게 한다. */
    private fun showMediaPickerDialog(xlsxFile: File, entries: List<String>) {
        val density = resources.displayMetrics.density
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), (16 * density).toInt(), (20 * density).toInt(), (16 * density).toInt())
        }
        val scroll = ScrollView(this).apply { addView(container) }

        val dialog = AlertDialog.Builder(this)
            .setTitle("가져올 이미지 선택")
            .setView(scroll)
            .setNegativeButton("취소") { _, _ -> xlsxFile.delete() }
            .setOnCancelListener { xlsxFile.delete() }
            .create()

        entries.forEachIndexed { index, entryName ->
            val bitmap = try {
                val bytes = XlsxReader.readMediaBytes(xlsxFile, entryName)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) {
                null
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = (16 * density).toInt()
                layoutParams = lp
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    dialog.dismiss()
                    useMediaEntryAsBaseMap(xlsxFile, entryName)
                }
            }
            val thumb = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (140 * density).toInt())
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundResource(R.drawable.bg_input_field)
                if (bitmap != null) setImageBitmap(bitmap) else setImageResource(R.drawable.ic_menu_map)
            }
            val label = TextView(this).apply {
                text = "이미지 ${index + 1}"
                setPadding(0, (8 * density).toInt(), 0, 0)
                setTextColor(getColor(R.color.text_secondary))
                textSize = 12.5f
            }
            row.addView(thumb)
            row.addView(label)
            container.addView(row)
        }

        dialog.show()
    }

    private fun showLegendThumbnail(path: String) {
        val bitmap = ImageUtil.decodeSampledBitmap(path)
        if (bitmap != null) binding.imageLegendThumb.setImageBitmap(bitmap)
        binding.buttonRemoveLegend.visibility = android.view.View.VISIBLE
        binding.buttonPickLegend.text = "변경"
    }

    private fun hideLegendThumbnail() {
        binding.imageLegendThumb.setImageResource(com.thkim.jijang.R.drawable.ic_menu_map)
        binding.buttonRemoveLegend.visibility = android.view.View.GONE
        binding.buttonPickLegend.text = "첨부"
    }

    private fun switchLine(lineId: Long) {
        if (lineId == activeLineId) return
        if (activeLineId >= 0) pointsByLineId[activeLineId] = binding.routeEditorView.getPoints().toMutableList()
        activeLineId = lineId
        applyActiveLineToView()
        rebuildLineChips()
    }

    private fun applyActiveLineToView() {
        val activeLine = lines.find { it.id == activeLineId } ?: return
        binding.routeEditorView.setLineColor(activeLine.colorHex)
        binding.routeEditorView.setLineDashed(activeLine.dashed)
        binding.routeEditorView.setPoints(pointsByLineId[activeLineId].orEmpty())
        val refs = if (hideOtherLines) emptyList() else lines.filter { it.id != activeLineId }.map { line ->
            RouteEditorView.ReferenceLine(pointsByLineId[line.id].orEmpty(), line.colorHex, line.dashed)
        }
        binding.routeEditorView.setReferenceLines(refs)
        updateColorSwatch(activeLine.colorHex)
        binding.buttonLineDashed.setImageResource(
            if (activeLine.dashed) R.drawable.ic_line_style_dashed else R.drawable.ic_line_style_solid
        )
    }

    /** "편집할 선" 칩 목록을 새로 그린다 — 선택된 선은 색이 채워지고, 나머지는 테두리만. */
    private fun rebuildLineChips() {
        val container = binding.containerLineChips
        container.removeAllViews()
        val density = resources.displayMetrics.density
        val outlineBg = getColor(R.color.surface_muted)

        lines.forEach { line ->
            val lineColorInt = try { Color.parseColor(line.colorHex) } catch (e: IllegalArgumentException) { Color.GRAY }
            val isActive = line.id == activeLineId
            val chip = TextView(this).apply {
                text = line.lineLabel?.ifBlank { "선" } ?: "선"
                textSize = 13f
                isSingleLine = true
                setTextColor(if (isActive) Color.WHITE else lineColorInt)
                setPadding((14 * density).toInt(), (8 * density).toInt(), (14 * density).toInt(), (8 * density).toInt())
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.marginEnd = (8 * density).toInt()
                layoutParams = lp
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 999f
                    if (isActive) {
                        setColor(lineColorInt)
                    } else {
                        setColor(outlineBg)
                        setStroke((1 * density).toInt(), lineColorInt)
                    }
                }
                setOnClickListener { switchLine(line.id) }
                setOnLongClickListener { showLineActionMenu(line); true }
            }
            container.addView(chip)
        }

        val addChip = TextView(this).apply {
            text = "+ 선 추가"
            textSize = 13f
            isSingleLine = true
            setTextColor(getColor(R.color.primary))
            setPadding((14 * density).toInt(), (8 * density).toInt(), (14 * density).toInt(), (8 * density).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 999f
                setColor(outlineBg)
                setStroke((1 * density).toInt(), getColor(R.color.icon_muted))
            }
            setOnClickListener { showAddLineDialog() }
        }
        container.addView(addChip)
    }

    private fun showLineActionMenu(line: RouteLine) {
        val options = mutableListOf("이름 변경")
        if (lines.size > 1) options.add("삭제")
        AlertDialog.Builder(this)
            .setTitle(line.lineLabel ?: "선")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "이름 변경" -> showRenameLineDialog(line)
                    "삭제" -> confirmDeleteLine(line)
                }
            }
            .show()
    }

    private fun showRenameLineDialog(line: RouteLine) {
        val input = android.widget.EditText(this).apply { setText(line.lineLabel) }
        AlertDialog.Builder(this)
            .setTitle("선 이름 변경")
            .setView(input)
            .setPositiveButton("확인") { _, _ ->
                val newLabel = input.text.toString().ifBlank { line.lineLabel ?: "선" }
                lifecycleScope.launch { viewModel.renameLine(line.id, newLabel) }
                val idx = lines.indexOfFirst { it.id == line.id }
                if (idx >= 0) lines[idx] = line.copy(lineLabel = newLabel)
                rebuildLineChips()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun confirmDeleteLine(line: RouteLine) {
        AlertDialog.Builder(this)
            .setTitle("선 삭제")
            .setMessage("\"${line.lineLabel ?: "선"}\"과 그 위의 모든 점을 삭제합니다. 계속할까요?")
            .setPositiveButton("삭제") { _, _ ->
                lifecycleScope.launch { viewModel.deleteLine(line) }
                lines.removeAll { it.id == line.id }
                pointsByLineId.remove(line.id)
                if (activeLineId == line.id) {
                    activeLineId = lines.firstOrNull()?.id ?: -1
                }
                // 지운 선이 활성 선이 아니었어도 참고용 선 목록(referenceLines)에는
                // 그 선이 남아있으므로, 항상 다시 그려서 화면에서도 바로 사라지게 한다.
                applyActiveLineToView()
                rebuildLineChips()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showAddLineDialog() {
        val input = android.widget.EditText(this).apply { hint = "선 이름 (예: 지중신설)" }
        AlertDialog.Builder(this)
            .setTitle("새 선 추가")
            .setView(input)
            .setPositiveButton("추가") { _, _ ->
                if (currentBaseMapImageId < 0) return@setPositiveButton
                val label = input.text.toString().ifBlank { "선 ${lines.size + 1}" }
                val colorHex = LINE_COLOR_PALETTE[lines.size % LINE_COLOR_PALETTE.size].second
                lifecycleScope.launch {
                    pointsByLineId[activeLineId] = binding.routeEditorView.getPoints().toMutableList()
                    val newLine = viewModel.addLine(currentBaseMapImageId, label, colorHex)
                    lines.add(newLine)
                    pointsByLineId[newLine.id] = mutableListOf()
                    activeLineId = newLine.id
                    rebuildLineChips()
                    applyActiveLineToView()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private val shapeButtons by lazy {
        listOf(
            binding.buttonShapePlain to MarkerType.PLAIN,
            binding.buttonShapeEnclosure to MarkerType.ENCLOSURE,
            binding.buttonShapePole to MarkerType.POLE,
            binding.buttonShapeConnection to MarkerType.CONNECTION
        )
    }

    /** 카문다 모델러의 도형 팔레트 요청 반영 — 도형을 미리 선택해두면 빈 곳을 탭하는
     * 즉시(다이얼로그 없이) 그 종류의 점이 찍힌다. 같은 도형을 다시 누르면 선택 해제되어
     * 기존처럼 매번 다이얼로그로 고르는 방식으로 돌아간다. */
    private fun setupDrawingToolbar() {
        shapeButtons.forEach { (button, type) ->
            button.setOnClickListener {
                if (button.isSelected) {
                    button.isSelected = false
                    binding.routeEditorView.setQuickPlaceType(null)
                } else {
                    shapeButtons.forEach { (b, _) -> b.isSelected = false }
                    button.isSelected = true
                    binding.routeEditorView.setQuickPlaceType(type)
                }
            }
        }

        binding.buttonLineColor.setOnClickListener { showColorPickerDialog() }
        binding.buttonLineDashed.setOnClickListener { toggleLineDashed() }
        binding.buttonResetZoom.setOnClickListener { binding.routeEditorView.resetZoom() }
        binding.buttonUndo.setOnClickListener { binding.routeEditorView.undo() }
        binding.buttonRedo.setOnClickListener { binding.routeEditorView.redo() }
        binding.buttonAim.setOnClickListener {
            val on = !binding.routeEditorView.aimLiftEnabled
            binding.routeEditorView.aimLiftEnabled = on
            binding.buttonAim.text = if (on) "조준 ON" else "조준 OFF"
        }
        binding.routeEditorView.onHistoryChanged = { canUndo, canRedo ->
            binding.buttonUndo.isEnabled = canUndo
            binding.buttonRedo.isEnabled = canRedo
        }
        binding.buttonHideOtherLines.setOnClickListener { toggleHideOtherLines() }
    }

    private fun toggleHideOtherLines() {
        hideOtherLines = !hideOtherLines
        binding.buttonHideOtherLines.text = if (hideOtherLines) "다른 선 보이기" else "다른 선 숨기기"
        applyActiveLineToView()
    }

    private fun showColorPickerDialog() {
        val labels = LINE_COLOR_PALETTE.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("선 색상 선택")
            .setItems(labels) { _, which -> applyLineColor(LINE_COLOR_PALETTE[which].second) }
            .show()
    }

    private fun applyLineColor(colorHex: String) {
        binding.routeEditorView.setLineColor(colorHex)
        updateColorSwatch(colorHex)
        val idx = lines.indexOfFirst { it.id == activeLineId }
        if (idx < 0) return
        val updated = lines[idx].copy(colorHex = colorHex)
        lines[idx] = updated
        rebuildLineChips()
        lifecycleScope.launch { viewModel.setLineStyle(updated.id, updated.colorHex, updated.dashed) }
    }

    private fun toggleLineDashed() {
        val idx = lines.indexOfFirst { it.id == activeLineId }
        if (idx < 0) return
        val updated = lines[idx].copy(dashed = !lines[idx].dashed)
        lines[idx] = updated
        binding.routeEditorView.setLineDashed(updated.dashed)
        binding.buttonLineDashed.setImageResource(
            if (updated.dashed) R.drawable.ic_line_style_dashed else R.drawable.ic_line_style_solid
        )
        lifecycleScope.launch { viewModel.setLineStyle(updated.id, updated.colorHex, updated.dashed) }
    }

    private fun updateColorSwatch(colorHex: String) {
        val color = try { Color.parseColor(colorHex) } catch (e: IllegalArgumentException) { Color.RED }
        binding.buttonLineColor.drawable?.mutate()?.setColorFilter(color, PorterDuff.Mode.SRC_IN)
    }

    private fun showAddPointDialog(relPoint: PointF) {
        showMarkerTypeDialog("점 추가") { markerType, label, status ->
            binding.routeEditorView.addPoint(relPoint, markerType, label, status)
        }
    }

    /** 카문다 모델러의 "노드 선택 → 액션 메뉴" 방식을 참고해서, 이미 찍힌 점을 탭하면
     * 바로 종류/이름을 바꾸거나 삭제할 수 있게 한다 — 예전에는 지우려면 안 보이는
     * 롱프레스 제스처를 써야 했다. */
    private fun showPointActionMenu(index: Int) {
        AlertDialog.Builder(this)
            .setTitle("점 편집")
            .setItems(arrayOf("종류/이름 변경", "이 점 삭제")) { _, which ->
                when (which) {
                    0 -> showMarkerTypeDialog("종류 변경") { markerType, label, status ->
                        binding.routeEditorView.updatePointMarker(index, markerType, label, status)
                    }
                    1 -> binding.routeEditorView.removePointAt(index)
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /** 함체/전주는 실제 범례처럼 이름 외에 상태(신설/기설/철거)도 정해야 색이 정해진다. */
    private fun showMarkerTypeDialog(title: String, onConfirm: (MarkerType, String?, MarkerStatus?) -> Unit) {
        val options = arrayOf("일반 경로점", "함체 위치로 지정", "전주 위치로 지정", "접속점 위치로 지정")
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> onConfirm(MarkerType.PLAIN, null, null)
                    1 -> showPickerThenConfirm(MarkerType.ENCLOSURE, { viewModel.getEnclosureOptions() }) { type, label ->
                        showStatusDialog { status -> onConfirm(type, label, status) }
                    }
                    2 -> showPickerThenConfirm(MarkerType.POLE, { viewModel.getPoleOptions() }) { type, label ->
                        showStatusDialog { status -> onConfirm(type, label, status) }
                    }
                    3 -> showPickerThenConfirm(MarkerType.CONNECTION, { viewModel.getConnectionOptions() }) { type, label ->
                        onConfirm(type, label, null)
                    }
                }
            }
            .show()
    }

    private fun showStatusDialog(onPicked: (MarkerStatus) -> Unit) {
        val options = MarkerStatus.entries.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("상태 선택")
            .setItems(options.map { it.label }.toTypedArray()) { _, which -> onPicked(options[which]) }
            .show()
    }

    private fun pickerTitle(markerType: MarkerType): String = when (markerType) {
        MarkerType.ENCLOSURE -> "함체 선택"
        MarkerType.POLE -> "전주 선택"
        MarkerType.CONNECTION -> "접속점 선택"
        MarkerType.PLAIN -> ""
    }

    private fun customInputHint(markerType: MarkerType): String = when (markerType) {
        MarkerType.ENCLOSURE -> "함체 이름 직접 입력"
        MarkerType.POLE -> "전주번호 직접 입력"
        MarkerType.CONNECTION -> "접속점 이름 직접 입력"
        MarkerType.PLAIN -> "이름 입력"
    }

    /** 체크리스트/구간별 상세를 아직 안 채웠어도 개황도는 별개로 그릴 수 있어야 한다는
     * 요청 반영 — 목록이 비어있으면 막던 것을 "직접입력" 옵션으로 바꿔서, 항상 이름을
     * 타이핑해서라도 점을 찍을 수 있게 했다. 나중에 실제 데이터가 채워지면 그 목록에서
     * 골라 다시 이름을 맞출 수도 있다 (점 편집 메뉴). */
    private fun showPickerThenConfirm(
        markerType: MarkerType,
        loadOptions: suspend () -> List<String>,
        onConfirm: (MarkerType, String?) -> Unit
    ) {
        lifecycleScope.launch {
            val options = loadOptions()
            val displayOptions = options + "직접입력"
            AlertDialog.Builder(this@DiagramEditActivity)
                .setTitle(pickerTitle(markerType))
                .setItems(displayOptions.toTypedArray()) { _, which ->
                    if (which == displayOptions.lastIndex) {
                        promptCustomLabel(markerType, onConfirm)
                    } else {
                        onConfirm(markerType, options[which])
                    }
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }

    private fun promptCustomLabel(markerType: MarkerType, onConfirm: (MarkerType, String?) -> Unit) {
        val input = android.widget.EditText(this)
        input.hint = customInputHint(markerType)
        AlertDialog.Builder(this)
            .setTitle("직접 입력")
            .setView(input)
            .setPositiveButton("확인") { _, _ -> onConfirm(markerType, input.text.toString().ifBlank { null }) }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun saveAndFinish() {
        if (lines.isEmpty()) {
            Toast.makeText(this, "먼저 이미지를 불러오세요", Toast.LENGTH_SHORT).show()
            return
        }
        if (activeLineId >= 0) pointsByLineId[activeLineId] = binding.routeEditorView.getPoints().toMutableList()
        lifecycleScope.launch {
            lines.forEach { line -> viewModel.savePoints(line.id, pointsByLineId[line.id].orEmpty()) }
            Toast.makeText(this@DiagramEditActivity, "저장되었습니다", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
