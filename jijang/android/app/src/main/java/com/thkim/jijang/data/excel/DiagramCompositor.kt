package com.thkim.jijang.data.excel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.DashPathEffect
import com.thkim.jijang.data.model.MarkerType
import com.thkim.jijang.data.model.RoutePoint
import com.thkim.jijang.legend.LegendCatalog
import com.thkim.jijang.legend.LegendDraw
import com.thkim.jijang.legend.LinePattern

private fun parseColorSafe(hex: String, fallback: Int): Int = try { Color.parseColor(hex) } catch (e: IllegalArgumentException) { fallback }

/** 내보내기용 선 하나 — 점 목록 + 색상 + 점선 여부(실제 개황도 범례의 신설=점선/기설=실선 구분). */
data class LineRender(val points: List<RoutePoint>, val colorHex: String, val dashed: Boolean = false, val styleId: String? = null)

/**
 * GIS 캡처 배경 이미지 위에 저장된 경로(신설/철거 최대 두 줄, 함체/전주 마커 포함)를
 * 그려서 하나의 합성 비트맵으로 만든다 — [com.thkim.jijang.ui.diagram.RouteEditorView]의
 * 화면 그리기 로직과 같은 스타일이지만, 뷰가 아니라 이미지 원본 해상도에 직접
 * 그린다 (letterbox 계산이 필요 없다 — 상대좌표를 그대로 이미지 너비/높이에 곱하면 된다).
 */
object DiagramCompositor {

    /**
     * [lines]: 신설/철거 각각 최대 한 줄씩.
     * [legend]: 마커를 직접 재현하는 대신, 실제 범례 이미지를 그대로 우하단에 붙여 넣는다
     * (70여 개 아이콘을 전부 다시 그리는 대신, 사용자가 가진 진짜 범례 사진을 그대로 신뢰).
     */
    fun composite(base: Bitmap, lines: List<LineRender>, legend: Bitmap? = null): Bitmap {
        val result = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val w = result.width.toFloat()
        val h = result.height.toFloat()

        if (legend != null) {
            drawLegend(canvas, legend, w, h)
        } else {
            drawAutoLegend(canvas, lines, w, h)
        }

        if (lines.all { it.points.isEmpty() }) return result
        drawLinesAndMarkers(canvas, lines, w, h)
        return result
    }

    /**
     * 범례 이미지가 없을 때: 도면에 실제로 쓰인 선 종류와 도형 심볼만 모아 우하단에 표로 그린다.
     * 범례 카탈로그의 정의(색/패턴/모양)를 그대로 써서 도면 위 모양과 항상 일치한다.
     */
    private fun drawAutoLegend(canvas: Canvas, lines: List<LineRender>, w: Float, h: Float) {
        val usedLines = lines.filter { it.points.size >= 2 }
            .mapNotNull { LegendCatalog.line(it.styleId) }.distinctBy { it.id }
        val usedSymbols = lines.flatMap { it.points }
            .mapNotNull { LegendCatalog.symbol(it.symbolId) }.distinctBy { it.id }
        if (usedLines.isEmpty() && usedSymbols.isEmpty()) return

        val unit = (w * 0.002f).coerceAtLeast(2f)
        val textSize = (w * 0.011f).coerceAtLeast(12f)
        val rowH = textSize * 1.9f
        val pad = textSize * 0.7f
        val sampleW = textSize * 3.2f
        val title = "범 례"

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; this.textSize = textSize }
        val bold = Paint(text).apply { isFakeBoldText = true }
        val labels = usedLines.map { it.label } + usedSymbols.map { it.label }
        val maxLabel = labels.maxOf { text.measureText(it) }
        val boxW = pad * 3.6f + sampleW + maxLabel
        val rows = usedLines.size + usedSymbols.size
        val boxH = pad * 2 + rowH * (rows + 1)
        val margin = w * 0.015f
        val left = w - boxW - margin
        val top = h - boxH - margin

        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B7C0CC"); style = Paint.Style.STROKE; strokeWidth = (w * 0.0015f).coerceAtLeast(2f)
        }
        canvas.drawRect(left, top, left + boxW, top + boxH, bg)
        canvas.drawRect(left, top, left + boxW, top + boxH, frame)

        fun baseline(y: Float) = y - (text.fontMetrics.ascent + text.fontMetrics.descent) / 2f
        var cy = top + pad + rowH / 2
        canvas.drawText(title, left + boxW / 2 - bold.measureText(title) / 2, baseline(cy), bold)
        cy += rowH

        val sx = left + pad
        val tx = left + pad * 2 + sampleW
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = unit * 1.3f }
        usedLines.forEach { def ->
            linePaint.color = parseColorSafe(def.colorHex, Color.RED)
            linePaint.pathEffect = LegendDraw.pathEffect(def.pattern, linePaint.strokeWidth)
            canvas.drawLine(sx, cy, sx + sampleW, cy, linePaint)
            if (def.pattern == LinePattern.ARROW) {
                LegendDraw.drawArrowHead(canvas, sx, cy, sx + sampleW, cy, linePaint, linePaint.strokeWidth * 4.5f)
            }
            canvas.drawText(def.label, tx, baseline(cy), text)
            cy += rowH
        }
        usedSymbols.forEach { def ->
            LegendDraw.drawSymbol(canvas, sx + sampleW / 2, cy, rowH * 0.38f, def, unit * 0.5f)
            canvas.drawText(def.label, tx, baseline(cy), text)
            cy += rowH
        }
    }

    private fun drawLegend(canvas: Canvas, legend: Bitmap, w: Float, h: Float) {
        val maxWidth = w * 0.26f
        val scale = (maxWidth / legend.width).coerceAtMost(1f)
        val legendW = legend.width * scale
        val legendH = legend.height * scale
        val margin = w * 0.015f
        val left = w - legendW - margin
        val top = h - legendH - margin

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B7C0CC")
            style = Paint.Style.STROKE
            strokeWidth = (w * 0.0015f).coerceAtLeast(2f)
        }
        val pad = w * 0.006f
        canvas.drawRect(left - pad, top - pad, left + legendW + pad, top + legendH + pad, borderPaint)
        canvas.drawRect(left - pad, top - pad, left + legendW + pad, top + legendH + pad, framePaint)

        val destRect = RectF(left, top, left + legendW, top + legendH)
        canvas.drawBitmap(legend, null, destRect, null)
    }

    private fun drawLinesAndMarkers(canvas: Canvas, lines: List<LineRender>, w: Float, h: Float) {
        val strokeWidth = (w * 0.002f).coerceAtLeast(2f)
        val radius = (w * 0.0035f).coerceAtLeast(4f)
        val textSize = (w * 0.009f).coerceAtLeast(11f)
        val dashEffect = DashPathEffect(floatArrayOf(strokeWidth * 4f, strokeWidth * 2.5f), 0f)

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; this.strokeWidth = strokeWidth }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; this.strokeWidth = strokeWidth * 0.6f }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; this.textSize = textSize; isFakeBoldText = true }
        val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(210, 255, 255, 255); style = Paint.Style.FILL }
        val glyphBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val glyphBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; this.strokeWidth = strokeWidth * 0.4f }
        val glyphTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; isFakeBoldText = true }

        fun drawGlyphMarker(cx: Float, cy: Float, letter: String, glyphColor: Int) {
            val r = radius * 1.35f
            glyphBadgePaint.color = glyphColor
            canvas.drawCircle(cx, cy, r, glyphBadgePaint)
            canvas.drawCircle(cx, cy, r, glyphBorderPaint)
            glyphTextPaint.textSize = r * 1.25f
            val fm = glyphTextPaint.fontMetrics
            canvas.drawText(letter, cx, cy - (fm.ascent + fm.descent) / 2f, glyphTextPaint)
        }

        lines.forEach { line ->
            val points = line.points
            if (points.isEmpty()) return@forEach
            val styleDef = LegendCatalog.line(line.styleId)
            val color = styleDef?.let { parseColorSafe(it.colorHex, Color.RED) }
                ?: try { Color.parseColor(line.colorHex) } catch (e: IllegalArgumentException) { Color.RED }

            if (points.size >= 2) {
                linePaint.color = color
                linePaint.pathEffect = if (styleDef != null) LegendDraw.pathEffect(styleDef.pattern, strokeWidth) else if (line.dashed) dashEffect else null
                val path = Path()
                path.moveTo((points[0].relativeX * w).toFloat(), (points[0].relativeY * h).toFloat())
                for (i in 1 until points.size) {
                    path.lineTo((points[i].relativeX * w).toFloat(), (points[i].relativeY * h).toFloat())
                }
                canvas.drawPath(path, linePaint)
                if (styleDef?.pattern == LinePattern.ARROW) {
                    val a = points[points.size - 2]
                    val b = points.last()
                    LegendDraw.drawArrowHead(
                        canvas, (a.relativeX * w).toFloat(), (a.relativeY * h).toFloat(),
                        (b.relativeX * w).toFloat(), (b.relativeY * h).toFloat(), linePaint, strokeWidth * 4.5f
                    )
                }
            }

            points.forEach { p ->
                val cx = (p.relativeX * w).toFloat()
                val cy = (p.relativeY * h).toFloat()
                val symbol = LegendCatalog.symbol(p.symbolId)
                if (symbol != null) {
                    LegendDraw.drawSymbol(canvas, cx, cy, radius * 1.5f, symbol, strokeWidth * 0.5f)
                    if (p.label != null) drawLabel(canvas, p.label, cx, cy, radius, labelPaint, labelBgPaint)
                    return@forEach
                }
                when (p.markerType) {
                    MarkerType.PLAIN -> {
                        canvas.drawCircle(cx, cy, radius, fillPaint)
                        strokePaint.color = color
                        canvas.drawCircle(cx, cy, radius, strokePaint)
                    }
                    MarkerType.ENCLOSURE -> {
                        drawGlyphMarker(cx, cy, "P", p.status?.let { parseColorSafe(it.colorHex, color) } ?: color)
                        drawLabel(canvas, p.label ?: "함체", cx, cy, radius, labelPaint, labelBgPaint)
                    }
                    MarkerType.POLE -> {
                        drawGlyphMarker(cx, cy, "C", p.status?.let { parseColorSafe(it.colorHex, color) } ?: color)
                        drawLabel(canvas, p.label ?: "전주", cx, cy, radius, labelPaint, labelBgPaint)
                    }
                    MarkerType.CONNECTION -> {
                        val r = radius * 1.3f
                        val path = Path().apply {
                            moveTo(cx, cy - r); lineTo(cx + r, cy); lineTo(cx, cy + r); lineTo(cx - r, cy); close()
                        }
                        canvas.drawPath(path, fillPaint)
                        strokePaint.color = color
                        canvas.drawPath(path, strokePaint)
                        drawLabel(canvas, p.label ?: "접속점", cx, cy, radius, labelPaint, labelBgPaint)
                    }
                }
            }
        }
    }

    private fun drawLabel(canvas: Canvas, text: String, cx: Float, cy: Float, radius: Float, labelPaint: Paint, bgPaint: Paint) {
        val textWidth = labelPaint.measureText(text)
        val bgLeft = cx - textWidth / 2f - 8f
        val bgTop = cy - radius * 2.4f - labelPaint.textSize - 8f
        val bgRight = cx + textWidth / 2f + 8f
        val bgBottom = cy - radius * 2.4f + 8f
        canvas.drawRoundRect(RectF(bgLeft, bgTop, bgRight, bgBottom), 8f, 8f, bgPaint)
        canvas.drawText(text, cx - textWidth / 2f, bgBottom - 12f, labelPaint)
    }
}
