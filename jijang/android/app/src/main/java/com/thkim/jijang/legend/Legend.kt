package com.thkim.jijang.legend

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathEffect
import android.graphics.RectF
import com.thkim.jijang.data.model.MarkerStatus
import com.thkim.jijang.data.model.MarkerType

/**
 * 실제 개황도 범례(SKT 표준 양식)의 선/도형 심볼 카탈로그.
 *
 * 화면(RouteEditorView), 선택 창(LegendPickerDialog), 엑셀용 합성(DiagramCompositor)이
 * 모두 이 파일의 정의와 [LegendDraw]를 같이 쓰므로, 화면에서 보이는 모양이 그대로
 * 내보내기 결과에도 나온다. 저장은 항목의 id(문자열)만 DB에 넣는다 — 항목을 추가하거나
 * 색을 조정해도 이미 저장된 도면은 그대로 새 정의를 따라간다.
 */
enum class LinePattern { SOLID, DOTTED, DASH, DASH_DOT, ARROW }

data class LineStyleDef(
    val id: String,
    val label: String,
    val colorHex: String,
    val pattern: LinePattern,
    val group: String
)

enum class SymbolShape { CIRCLE, SQUARE }

data class SymbolDef(
    val id: String,
    val label: String,
    val letter: String?,
    val shape: SymbolShape,
    val fillHex: String,
    val letterHex: String = "#FFFFFF",
    val borderHex: String = "#FFFFFF",
    val group: String,
    /** 기존 데이터/엑셀 연동과의 호환을 위해 같이 저장하는 값 (P/M/S/함체 → ENCLOSURE, C/전주 → POLE). */
    val markerType: MarkerType,
    val status: MarkerStatus?
)

object LegendCatalog {

    private const val RED = "#E53935"
    private const val BLUE = "#1E4FD8"
    private const val MAGENTA = "#D63CE8"
    private const val BLACK = "#111111"
    private const val GREEN = "#2E9E3A"
    private const val YELLOW = "#F4D000"
    private const val OLIVE = "#5E8C2A"

    const val G_CABLE = "케이블"
    const val G_CONDUIT = "관로"
    const val G_ENCLOSURE = "함체 · Master · Slave"
    const val G_POLE = "전주"
    const val G_STATION = "국사 · 기지국 · 중계기"
    const val G_MANHOLE = "맨홀"

    val lines: List<LineStyleDef> = listOf(
        LineStyleDef("AERIAL_NEW", "가공신설", RED, LinePattern.DOTTED, G_CABLE),
        LineStyleDef("AERIAL_EXIST", "가공기설", RED, LinePattern.SOLID, G_CABLE),
        LineStyleDef("UG_NEW", "지중신설", "#1E40FF", LinePattern.DOTTED, G_CABLE),
        LineStyleDef("UG_EXIST", "지중기설", "#1E40FF", LinePattern.SOLID, G_CABLE),
        LineStyleDef("FTTH_NEW", "FTTH신설", "#C026D3", LinePattern.DOTTED, G_CABLE),
        LineStyleDef("FTTH_EXIST", "FTTH기설", "#C026D3", LinePattern.SOLID, G_CABLE),
        LineStyleDef("CABLE_REMOVE", "케이블 철거", BLACK, LinePattern.SOLID, G_CABLE),
        LineStyleDef("SKB_CABLE", "SKB 케이블", GREEN, LinePattern.SOLID, G_CABLE),
        LineStyleDef("TB_NEW", "T/B공동 케이블 신설", "#E6B800", LinePattern.DOTTED, G_CABLE),
        LineStyleDef("TB_EXIST", "T/B공동 케이블 기설", "#E6B800", LinePattern.SOLID, G_CABLE),
        LineStyleDef("TEMP_CABLE", "임차케이블", "#3B4A3A", LinePattern.DASH_DOT, G_CABLE),
        LineStyleDef("MOVE_CABLE", "단순이설(케이블)", OLIVE, LinePattern.DASH_DOT, G_CABLE),
        LineStyleDef("MOVE_SIMPLE", "단순이설", "#E69A00", LinePattern.ARROW, G_CABLE),
        LineStyleDef("CONDUIT_SELF_NEW", "자가 신설관로", OLIVE, LinePattern.DASH, G_CONDUIT),
        LineStyleDef("CONDUIT_SELF_EXIST", "자가 기설관로", OLIVE, LinePattern.SOLID, G_CONDUIT),
        LineStyleDef("CONDUIT_OTHER_NEW", "타사 신설관로", "#2F6BD8", LinePattern.DASH, G_CONDUIT),
        LineStyleDef("CONDUIT_OTHER_EXIST", "타사 기설관로", "#2F6BD8", LinePattern.SOLID, G_CONDUIT)
    )

    private fun enc(id: String, label: String, letter: String, fill: String, status: MarkerStatus?, letterHex: String = "#FFFFFF", border: String = "#FFFFFF") =
        SymbolDef(id, label, letter, SymbolShape.CIRCLE, fill, letterHex, border, G_ENCLOSURE, MarkerType.ENCLOSURE, status)

    private fun pole(id: String, label: String, letter: String?, fill: String, status: MarkerStatus?, letterHex: String = "#FFFFFF", border: String = "#FFFFFF") =
        SymbolDef(id, label, letter, SymbolShape.CIRCLE, fill, letterHex, border, G_POLE, MarkerType.POLE, status)

    private fun station(id: String, label: String, letter: String, fill: String) =
        SymbolDef(id, label, letter, SymbolShape.SQUARE, fill, "#FFFFFF", "#7A1F1F", G_STATION, MarkerType.PLAIN, null)

    private fun manhole(id: String, label: String, fill: String, status: MarkerStatus?) =
        SymbolDef(id, label, "M", SymbolShape.SQUARE, fill, "#FFFFFF", "#FFFFFF", G_MANHOLE, MarkerType.PLAIN, status)

    val symbols: List<SymbolDef> = listOf(
        enc("P_NEW", "신설함체", "P", BLUE, MarkerStatus.NEW),
        enc("P_EXIST", "기설함체", "P", YELLOW, MarkerStatus.EXISTING, "#222222", "#222222"),
        enc("P_REMOVED", "철거함체", "P", BLACK, MarkerStatus.REMOVED),
        enc("M_NEW", "신설 Master", "M", BLUE, MarkerStatus.NEW),
        enc("M_EXIST", "기설 Master", "M", YELLOW, MarkerStatus.EXISTING, "#222222", "#222222"),
        enc("M_REMOVED", "철거 Master", "M", BLACK, MarkerStatus.REMOVED),
        enc("S_NEW", "신설 Slave", "S", BLUE, MarkerStatus.NEW),
        enc("S_EXIST", "기설 Slave", "S", YELLOW, MarkerStatus.EXISTING, "#222222", "#222222"),
        enc("S_REMOVED", "철거 Slave", "S", BLACK, MarkerStatus.REMOVED),
        enc("P_SKB", "SKB함체", "P", GREEN, null),

        pole("C_SELF_EXIST", "기설전주(자가)", "C", MAGENTA, MarkerStatus.EXISTING),
        pole("C_SELF_NEW", "신설전주(자가)", "C", BLUE, MarkerStatus.NEW),
        pole("C_SELF_REMOVED", "철거전주(자가)", "C", BLACK, MarkerStatus.REMOVED),
        pole("C_OTHER", "타사전주", "C", GREEN, null),
        pole("C_OTHER_REMOVED", "타사전주(철거)", "C", BLACK, MarkerStatus.REMOVED),
        pole("C_KEPCO_EXIST", "기설전주(한전)", "C", "#FFFFFF", MarkerStatus.EXISTING, "#6B7280", "#6B7280"),
        pole("C_KEPCO_NEW", "신설전주(한전)", null, RED, MarkerStatus.NEW),
        pole("C_KEPCO_REMOVED", "철거전주(한전)", null, BLACK, MarkerStatus.REMOVED),

        station("HQ", "중심국/국사", "SK", "#D32F2F"),
        station("BASE", "기지국", "T", "#F57C00"),
        station("RELAY", "중계기", "R", "#F57C00"),

        manhole("MH_SELF_EXIST", "자가맨홀(기설)", MAGENTA, MarkerStatus.EXISTING),
        manhole("MH_SELF_NEW", "자가맨홀(신설)", "#1E88E5", MarkerStatus.NEW),
        manhole("MH_SELF_REMOVED", "자가맨홀(철거)", BLACK, MarkerStatus.REMOVED),
        manhole("MH_OTHER", "타사맨홀", GREEN, null)
    )

    private val lineMap = lines.associateBy { it.id }
    private val symbolMap = symbols.associateBy { it.id }

    fun line(id: String?): LineStyleDef? = id?.let { lineMap[it] }
    fun symbol(id: String?): SymbolDef? = id?.let { symbolMap[it] }
}

/** 화면·미리보기·엑셀 합성이 같이 쓰는 그리기 도구 — 크기는 모두 호출하는 쪽이 정한 좌표계 기준. */
object LegendDraw {

    private fun color(hex: String, fallback: Int = Color.GRAY) =
        try { Color.parseColor(hex) } catch (e: IllegalArgumentException) { fallback }

    /** [unit]은 선 굵기 — 점선/일점쇄선의 간격은 굵기에 비례해서 정한다. */
    fun pathEffect(pattern: LinePattern, unit: Float): PathEffect? = when (pattern) {
        LinePattern.SOLID, LinePattern.ARROW -> null
        LinePattern.DOTTED -> DashPathEffect(floatArrayOf(unit * 1.3f, unit * 1.7f), 0f)
        LinePattern.DASH -> DashPathEffect(floatArrayOf(unit * 4.5f, unit * 2.8f), 0f)
        LinePattern.DASH_DOT -> DashPathEffect(floatArrayOf(unit * 4.5f, unit * 2.2f, unit * 1.2f, unit * 2.2f), 0f)
    }

    /** 선의 마지막 구간 끝에 화살촉을 그린다 ("단순이설" 선 전용). */
    fun drawArrowHead(canvas: Canvas, fromX: Float, fromY: Float, toX: Float, toY: Float, paint: Paint, size: Float) {
        val dx = toX - fromX
        val dy = toY - fromY
        val len = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (len < 1e-3f) return
        val ux = dx / len
        val uy = dy / len
        val path = Path().apply {
            moveTo(toX, toY)
            lineTo(toX - ux * size - uy * size * 0.55f, toY - uy * size + ux * size * 0.55f)
            lineTo(toX - ux * size + uy * size * 0.55f, toY - uy * size - ux * size * 0.55f)
            close()
        }
        val fill = Paint(paint).apply { style = Paint.Style.FILL; pathEffect = null }
        canvas.drawPath(path, fill)
    }

    /** 범례 도형 하나를 (cx, cy) 중심, 반지름 [r]로 그린다. [borderWidth]는 테두리 굵기. */
    fun drawSymbol(canvas: Canvas, cx: Float, cy: Float, r: Float, def: SymbolDef, borderWidth: Float) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = color(def.fillHex) }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = borderWidth; color = color(def.borderHex, Color.WHITE)
        }
        when (def.shape) {
            SymbolShape.CIRCLE -> {
                canvas.drawCircle(cx, cy, r, fill)
                canvas.drawCircle(cx, cy, r, stroke)
            }
            SymbolShape.SQUARE -> {
                val rect = RectF(cx - r, cy - r, cx + r, cy + r)
                canvas.drawRoundRect(rect, r * 0.18f, r * 0.18f, fill)
                canvas.drawRoundRect(rect, r * 0.18f, r * 0.18f, stroke)
            }
        }
        val letter = def.letter ?: return
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color(def.letterHex, Color.WHITE)
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            textSize = if (letter.length > 1) r * 0.95f else r * 1.25f
        }
        val fm = text.fontMetrics
        canvas.drawText(letter, cx, cy - (fm.ascent + fm.descent) / 2f, text)
    }
}
