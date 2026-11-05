package com.thkim.jijang.ui.diagram

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import com.thkim.jijang.data.model.MarkerStatus
import com.thkim.jijang.data.model.MarkerType
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** 편집용 점 하나 — 상대좌표(0..1) + 마커 종류 + (함체/전주로 지정한 경우) 표시 이름/상태. */
data class EditablePoint(
    val x: Float,
    val y: Float,
    val markerType: MarkerType = MarkerType.PLAIN,
    val label: String? = null,
    val status: MarkerStatus? = null
)

/**
 * GIS 캡처 이미지를 배경으로 표시하고, 그 위에 폴리라인(경로)을 그리거나
 * 편집(점 추가/이동/삭제)할 수 있는 뷰.
 *
 * 점은 항상 배경 이미지 기준 상대좌표(0.0~1.0)로 주고받는다 — 뷰 크기나
 * 화면 배율이 달라져도 이미지 안에서의 위치는 항상 동일하게 유지된다.
 *
 * 조작 방법:
 *  - 빈 공간 탭: [onEmptyTap] 콜백만 호출한다 (실제 추가는 액티비티가
 *    일반/함체/전주 중 선택을 받은 뒤 [addPoint]를 호출해서 확정한다)
 *  - 점을 드래그: 점 이동
 *  - 이미 찍힌 점을 탭(드래그 없이): [onPointTap] 콜백 호출 — 액티비티가 그
 *    자리에 "종류 변경/삭제" 액션 메뉴를 띄운다 (카문다 모델러의 노드 선택
 *    툴바를 참고한 방식 — 안 보이는 롱프레스 제스처 대신 눈에 보이는 메뉴로
 *    바꿨다. 15단계까지는 롱프레스로 조용히 삭제했는데 발견하기 어렵다는
 *    지적이 있었다)
 *
 * [setSecondaryPoints]로 다른 선(예: 반대 공종의 선)을 배경 참고용으로
 * 같이 표시할 수 있다 — 편집 대상이 아니라 터치에는 반응하지 않는다.
 *
 * 16단계 — 카문다 모델러 참고 요청으로 세 가지를 추가했다:
 *  - 두 손가락 핀치줌 + 한 손가락으로 빈 곳을 드래그하면 화면 이동(팬).
 *  - [setQuickPlaceType]으로 도형(일반/함체/전주/접속점)을 미리 선택해두면,
 *    빈 곳을 탭하는 즉시 다이얼로그 없이 바로 그 도형이 찍힌다 — 이름은
 *    임시 라벨로 자동 채워지고, 나중에 점을 탭해서 실제 이름으로 바꿀 수 있다.
 *  - [setLineDashed]로 점선/실선을 바꿀 수 있다 (실제 개황도 범례가 신설=점선,
 *    기설=실선을 쓰는 것을 반영).
 */
class RouteEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** 참고용으로 같이 보여줄 다른 선 하나 — 점 목록 + 색상 + 점선 여부. */
    data class ReferenceLine(val points: List<EditablePoint>, val colorHex: String, val dashed: Boolean = false)

    private var bitmap: Bitmap? = null
    private val points = mutableListOf<EditablePoint>()
    /** 17단계 — 선을 2개(신설/철거)로 고정하지 않고 몇 개든 추가할 수 있게 되면서,
     * 지금 편집 중이 아닌 "나머지 모든 선"을 전부 참고용으로 같이 보여줄 수 있어야 한다. */
    private var referenceLines: List<ReferenceLine> = emptyList()

    private val fitRect = RectF()

    /** 핀치줌/팬 누적 변환 — [fitRect] 기준 좌표계 위에 얹는다. */
    private val viewMatrix = Matrix()
    private val inverseMatrix = Matrix()
    private var currentScale = 1f
    private val minScale = 1f
    private val maxScale = 6f

    /** null이면 기존처럼 빈 곳 탭 시 [onEmptyTap] 다이얼로그를 띄운다.
     * 값이 있으면 다이얼로그 없이 바로 그 종류의 점을 찍는다 (카문다 스타일 즉시 배치). */
    private var quickPlaceType: MarkerType? = null
    private val quickPlaceCounters = mutableMapOf<MarkerType, Int>()

    private var lineDashed = false
    private val dashPathEffect = DashPathEffect(floatArrayOf(28f, 16f), 0f)

    private var lineColor = Color.parseColor("#FF3B30")
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    private val secondaryLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2f
        style = Paint.Style.STROKE
        alpha = 130
    }
    private val pointFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val pointStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 16f
        isFakeBoldText = true
    }
    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255)
        style = Paint.Style.FILL
    }
    private val glyphBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glyphBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val glyphTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val pointRadiusPx = 7f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var activeIndex = -1
    private var moved = false
    private var downX = 0f
    private var downY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f

    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val newScale = (currentScale * detector.scaleFactor).coerceIn(minScale, maxScale)
            val factor = newScale / currentScale
            viewMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
            currentScale = newScale
            clampTranslation()
            invalidate()
            return true
        }
    })

    /** 핀치줌/팬을 원래 크기로 되돌린다 — 눈에 보이는 버튼으로 노출해서 쓰게 한다
     * (더블탭 제스처는 한 번 탭한 것과 구분이 애매해져서 일부러 쓰지 않았다). */
    fun resetZoom() {
        viewMatrix.reset()
        currentScale = 1f
        invalidate()
    }

    private fun clampTranslation() {
        val mapped = RectF(fitRect)
        viewMatrix.mapRect(mapped)
        var dx = 0f
        var dy = 0f
        if (mapped.width() <= width) {
            dx = (width - mapped.width()) / 2f - mapped.left
        } else {
            if (mapped.left > 0f) dx = -mapped.left
            else if (mapped.right < width) dx = width - mapped.right
        }
        if (mapped.height() <= height) {
            dy = (height - mapped.height()) / 2f - mapped.top
        } else {
            if (mapped.top > 0f) dy = -mapped.top
            else if (mapped.bottom < height) dy = height - mapped.bottom
        }
        if (dx != 0f || dy != 0f) viewMatrix.postTranslate(dx, dy)
    }

    var onPointsChanged: ((List<EditablePoint>) -> Unit)? = null
    var onEmptyTap: ((PointF) -> Unit)? = null
    /** 이미 찍힌 점을 (드래그 없이) 탭했을 때 — 카문다 모델러처럼 그 점 위에 바로 액션
     * 메뉴(종류 변경/삭제)를 띄울 수 있게 화면 좌표와 함께 점 인덱스를 알려준다. */
    var onPointTap: ((index: Int, screenPoint: PointF) -> Unit)? = null

    fun removePointAt(index: Int) {
        if (index !in points.indices) return
        points.removeAt(index)
        onPointsChanged?.invoke(points.toList())
        invalidate()
    }

    fun updatePointMarker(index: Int, markerType: MarkerType, label: String?, status: MarkerStatus?) {
        if (index !in points.indices) return
        points[index] = points[index].copy(markerType = markerType, label = label, status = status)
        onPointsChanged?.invoke(points.toList())
        invalidate()
    }

    fun setLineColor(colorHex: String) {
        lineColor = try { Color.parseColor(colorHex) } catch (e: IllegalArgumentException) { Color.parseColor("#FF3B30") }
        invalidate()
    }

    fun setLineDashed(dashed: Boolean) {
        lineDashed = dashed
        invalidate()
    }

    /** 도형 툴바에서 선택한 종류 — null이면 기존처럼 탭할 때마다 다이얼로그로 고른다. */
    fun setQuickPlaceType(type: MarkerType?) {
        quickPlaceType = type
    }

    private fun autoLabelFor(type: MarkerType): String? {
        if (type == MarkerType.PLAIN) return null
        val prefix = when (type) {
            MarkerType.ENCLOSURE -> "함체"
            MarkerType.POLE -> "전주"
            MarkerType.CONNECTION -> "접속점"
            MarkerType.PLAIN -> return null
        }
        val next = (quickPlaceCounters[type] ?: 0) + 1
        quickPlaceCounters[type] = next
        return "$prefix-$next(임시)"
    }

    fun setImage(bmp: Bitmap) {
        bitmap = bmp
        requestLayout()
        invalidate()
    }

    fun setPoints(newPoints: List<EditablePoint>) {
        points.clear()
        points.addAll(newPoints)
        invalidate()
    }

    /** 편집 대상이 아닌, 배경 참고용 선들(지금 선택 안 된 나머지 선 전부)을 같이 그린다. */
    fun setReferenceLines(lines: List<ReferenceLine>) {
        referenceLines = lines
        invalidate()
    }

    fun getPoints(): List<EditablePoint> = points.toList()

    /** [onEmptyTap]에서 넘겨준 좌표로 실제 점을 확정해서 추가한다. */
    fun addPoint(point: PointF, markerType: MarkerType = MarkerType.PLAIN, label: String? = null, status: MarkerStatus? = null) {
        val adjusted = avoidOverlap(point)
        points.add(EditablePoint(adjusted.x, adjusted.y, markerType, label, status))
        onPointsChanged?.invoke(points.toList())
        invalidate()
    }

    /** 선이 여러 개 겹치는 교차점 근처에 새 점을 찍으면 다른 선의 점과 원이 완전히
     * 겹쳐서 안 보이거나 구분이 안 되는 문제가 있었다 — 너무 가까우면 살짝 밀어낸다. */
    private fun avoidOverlap(relPoint: PointF): PointF {
        val minDistPx = pointRadiusPx * 2.6f
        val others = mutableListOf<PointF>()
        points.forEach { others.add(relativeToView(it.x, it.y)) }
        referenceLines.forEach { ref -> ref.points.forEach { others.add(relativeToView(it.x, it.y)) } }
        if (others.isEmpty()) return relPoint

        var candidate = relativeToView(relPoint.x, relPoint.y)
        var attempt = 0
        while (attempt < 12) {
            val collision = others.firstOrNull { hypot((it.x - candidate.x).toDouble(), (it.y - candidate.y).toDouble()) < minDistPx }
                ?: return viewToRelative(candidate.x, candidate.y)
            val angle = Math.toRadians((attempt * 45).toDouble())
            val pushDist = minDistPx * 1.1f
            candidate = PointF(
                (collision.x + pushDist * cos(angle)).toFloat(),
                (collision.y + pushDist * sin(angle)).toFloat()
            )
            attempt++
        }
        return viewToRelative(candidate.x, candidate.y)
    }

    fun clearAll() {
        points.clear()
        onPointsChanged?.invoke(points.toList())
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeFitRect()
    }

    private fun computeFitRect() {
        val bmp = bitmap ?: return
        if (width == 0 || height == 0) return
        val scale = minOf(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
        val drawW = bmp.width * scale
        val drawH = bmp.height * scale
        val left = (width - drawW) / 2f
        val top = (height - drawH) / 2f
        fitRect.set(left, top, left + drawW, top + drawH)
    }

    private fun relativeToView(x: Float, y: Float): PointF =
        PointF(fitRect.left + x * fitRect.width(), fitRect.top + y * fitRect.height())

    private fun viewToRelative(x: Float, y: Float): PointF {
        val rx = ((x - fitRect.left) / fitRect.width()).coerceIn(0f, 1f)
        val ry = ((y - fitRect.top) / fitRect.height()).coerceIn(0f, 1f)
        return PointF(rx, ry)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap ?: return
        if (fitRect.width() == 0f) computeFitRect()

        canvas.save()
        canvas.concat(viewMatrix)
        canvas.drawBitmap(bmp, null, fitRect, null)

        referenceLines.forEach { ref ->
            val refColor = try { Color.parseColor(ref.colorHex) } catch (e: IllegalArgumentException) { Color.GRAY }
            drawLine(canvas, ref.points, refColor, secondaryLinePaint, drawMarkers = false, dashed = ref.dashed)
        }
        drawLine(canvas, points, lineColor, linePaint, drawMarkers = true, dashed = lineDashed)
        canvas.restore()
    }

    private fun drawLine(canvas: Canvas, pts: List<EditablePoint>, color: Int, paint: Paint, drawMarkers: Boolean, dashed: Boolean = false) {
        if (pts.size >= 2) {
            paint.color = color
            paint.pathEffect = if (dashed) dashPathEffect else null
            val path = android.graphics.Path()
            val first = relativeToView(pts[0].x, pts[0].y)
            path.moveTo(first.x, first.y)
            for (i in 1 until pts.size) {
                val p = relativeToView(pts[i].x, pts[i].y)
                path.lineTo(p.x, p.y)
            }
            canvas.drawPath(path, paint)
        }
        pts.forEach { pt ->
            val v = relativeToView(pt.x, pt.y)
            when (pt.markerType) {
                MarkerType.PLAIN -> {
                    canvas.drawCircle(v.x, v.y, pointRadiusPx, pointFillPaint)
                    pointStrokePaint.color = color
                    canvas.drawCircle(v.x, v.y, pointRadiusPx, pointStrokePaint)
                }
                MarkerType.ENCLOSURE -> {
                    drawGlyphMarker(canvas, v, "P", pt.status?.let { parseColorSafe(it.colorHex) } ?: color)
                    if (drawMarkers) drawLabel(canvas, pt.label ?: "함체", v)
                }
                MarkerType.POLE -> {
                    drawGlyphMarker(canvas, v, "C", pt.status?.let { parseColorSafe(it.colorHex) } ?: color)
                    if (drawMarkers) drawLabel(canvas, pt.label ?: "전주", v)
                }
                MarkerType.CONNECTION -> {
                    val r = pointRadiusPx * 1.3f
                    val path = android.graphics.Path().apply {
                        moveTo(v.x, v.y - r)
                        lineTo(v.x + r, v.y)
                        lineTo(v.x, v.y + r)
                        lineTo(v.x - r, v.y)
                        close()
                    }
                    canvas.drawPath(path, pointFillPaint)
                    pointStrokePaint.color = color
                    canvas.drawPath(path, pointStrokePaint)
                    if (drawMarkers) drawLabel(canvas, pt.label ?: "접속점", v)
                }
            }
        }
    }

    private fun parseColorSafe(hex: String): Int = try { Color.parseColor(hex) } catch (e: IllegalArgumentException) { Color.GRAY }

    /** 실제 개황도 범례의 "문자기호(P=함체/C=전주) + 색상(신설=파랑/기설=노랑/철거=검정)" 표기를
     * 흉내낸 마커 — 색이 있는 원형 배지 위에 흰 글자를 올린다 (16단계). */
    private fun drawGlyphMarker(canvas: Canvas, v: PointF, letter: String, glyphColor: Int) {
        val r = pointRadiusPx * 1.35f
        glyphBadgePaint.color = glyphColor
        canvas.drawCircle(v.x, v.y, r, glyphBadgePaint)
        canvas.drawCircle(v.x, v.y, r, glyphBorderPaint)
        glyphTextPaint.textSize = r * 1.25f
        val fm = glyphTextPaint.fontMetrics
        canvas.drawText(letter, v.x, v.y - (fm.ascent + fm.descent) / 2f, glyphTextPaint)
    }

    private fun drawLabel(canvas: Canvas, text: String, anchor: PointF) {
        val textWidth = labelPaint.measureText(text)
        val bgLeft = anchor.x - textWidth / 2f - 5f
        val bgTop = anchor.y - pointRadiusPx * 2.6f - labelPaint.textSize
        val bgRight = anchor.x + textWidth / 2f + 5f
        val bgBottom = anchor.y - pointRadiusPx * 2.6f + 5f
        canvas.drawRoundRect(RectF(bgLeft, bgTop, bgRight, bgBottom), 5f, 5f, labelBgPaint)
        canvas.drawText(text, anchor.x - textWidth / 2f, bgBottom - 7f, labelPaint)
    }

    private fun findNearestPointIndex(x: Float, y: Float): Int {
        var best = -1
        var bestDist = Float.MAX_VALUE
        points.forEachIndexed { index, pt ->
            val v = relativeToView(pt.x, pt.y)
            val d = hypot((v.x - x).toDouble(), (v.y - y).toDouble()).toFloat()
            if (d < pointRadiusPx * 2.2f && d < bestDist) {
                bestDist = d
                best = index
            }
        }
        return best
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)

        if (event.pointerCount > 1) {
            // 두 손가락 이상 — 핀치줌 전용, 점 드래그/탭 로직은 건드리지 않는다.
            activeIndex = -1
            moved = true
            return true
        }

        val rawX = event.x
        val rawY = event.y
        viewMatrix.invert(inverseMatrix)
        val mapped = floatArrayOf(rawX, rawY)
        inverseMatrix.mapPoints(mapped)
        val x = mapped[0]
        val y = mapped[1]

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = x; downY = y; moved = false
                lastRawX = rawX; lastRawY = rawY
                activeIndex = findNearestPointIndex(x, y)
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                activeIndex = -1
                moved = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (scaleGestureDetector.isInProgress) return true
                if (hypot((x - downX).toDouble(), (y - downY).toDouble()) > touchSlop) moved = true
                if (activeIndex in points.indices && moved) {
                    val rel = viewToRelative(x, y)
                    points[activeIndex] = points[activeIndex].copy(x = rel.x, y = rel.y)
                    invalidate()
                } else if (activeIndex == -1 && moved) {
                    // 빈 곳을 드래그 — 화면 이동(팬). translate는 화면(스크린) 좌표 기준이다.
                    viewMatrix.postTranslate(rawX - lastRawX, rawY - lastRawY)
                    clampTranslation()
                    invalidate()
                }
                lastRawX = rawX; lastRawY = rawY
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!scaleGestureDetector.isInProgress) {
                    if (activeIndex in points.indices && moved) {
                        onPointsChanged?.invoke(points.toList())
                    } else if (activeIndex in points.indices && !moved) {
                        onPointTap?.invoke(activeIndex, PointF(rawX, rawY))
                    } else if (activeIndex == -1 && !moved) {
                        val quick = quickPlaceType
                        if (quick != null) {
                            val defaultStatus = if (quick == MarkerType.ENCLOSURE || quick == MarkerType.POLE) MarkerStatus.NEW else null
                            addPoint(viewToRelative(x, y), quick, autoLabelFor(quick), defaultStatus)
                        } else {
                            onEmptyTap?.invoke(viewToRelative(x, y))
                        }
                    }
                }
                activeIndex = -1
                moved = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
