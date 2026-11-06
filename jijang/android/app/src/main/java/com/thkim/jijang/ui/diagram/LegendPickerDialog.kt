package com.thkim.jijang.ui.diagram

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.thkim.jijang.R
import com.thkim.jijang.legend.LegendCatalog
import com.thkim.jijang.legend.LegendDraw
import com.thkim.jijang.legend.LinePattern
import com.thkim.jijang.legend.LineStyleDef
import com.thkim.jijang.legend.SymbolDef

/**
 * 개황도 범례(SKT 표준 양식) 그대로 보여주고 고르게 하는 선택 창.
 * 미리보기는 실제 편집 화면/엑셀과 같은 [LegendDraw]로 그리므로 모양이 그대로 일치한다.
 */
object LegendPickerDialog {

    /** [extraTop]: 목록 맨 위에 붙일 "직접 입력" 같은 추가 항목 (이름 → 동작). */
    fun showLineStyles(
        context: Context,
        title: String = "선 종류 선택 (범례)",
        extraTop: List<Pair<String, () -> Unit>> = emptyList(),
        onPick: (LineStyleDef) -> Unit
    ) {
        val dialogRef = arrayOfNulls<AlertDialog>(1)
        val list = column(context)
        extraTop.forEach { (label, action) ->
            list.addView(textRow(context, label) { dialogRef[0]?.dismiss(); action() })
        }
        LegendCatalog.lines.groupBy { it.group }.forEach { (group, items) ->
            list.addView(header(context, group))
            items.forEach { def ->
                list.addView(row(context, LinePreview(context, def), def.label) {
                    dialogRef[0]?.dismiss(); onPick(def)
                })
            }
        }
        dialogRef[0] = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(ScrollView(context).apply { addView(list) })
            .setNegativeButton("취소", null)
            .show()
    }

    fun showSymbols(
        context: Context,
        title: String = "도형 심볼 선택 (범례)",
        extraTop: List<Pair<String, () -> Unit>> = emptyList(),
        onPick: (SymbolDef) -> Unit
    ) {
        val dialogRef = arrayOfNulls<AlertDialog>(1)
        val list = column(context)
        extraTop.forEach { (label, action) ->
            list.addView(textRow(context, label) { dialogRef[0]?.dismiss(); action() })
        }
        LegendCatalog.symbols.groupBy { it.group }.forEach { (group, items) ->
            list.addView(header(context, group))
            items.forEach { def ->
                list.addView(row(context, SymbolPreview(context, def), def.label) {
                    dialogRef[0]?.dismiss(); onPick(def)
                })
            }
        }
        dialogRef[0] = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(ScrollView(context).apply { addView(list) })
            .setNegativeButton("취소", null)
            .show()
    }

    private fun dp(context: Context, v: Float) = (v * context.resources.displayMetrics.density).toInt()

    private fun column(context: Context) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 12f), dp(context, 4f), dp(context, 12f), dp(context, 8f))
    }

    private fun header(context: Context, text: String) = TextView(context).apply {
        this.text = text
        textSize = 12.5f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(context.getColor(R.color.text_secondary))
        setPadding(dp(context, 4f), dp(context, 14f), 0, dp(context, 4f))
    }

    private fun textRow(context: Context, label: String, onClick: () -> Unit) = TextView(context).apply {
        text = label
        textSize = 15f
        setTextColor(context.getColor(R.color.accent_dark))
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
        minHeight = dp(context, 48f)
        setPadding(dp(context, 4f), 0, dp(context, 4f), 0)
        setOnClickListener { onClick() }
    }

    private fun row(context: Context, preview: View, label: String, onClick: () -> Unit) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(context, 48f)
        setPadding(dp(context, 4f), dp(context, 2f), dp(context, 4f), dp(context, 2f))
        isClickable = true
        isFocusable = true
        val attrs = intArrayOf(android.R.attr.selectableItemBackground)
        val ta = context.obtainStyledAttributes(attrs)
        background = ta.getDrawable(0)
        ta.recycle()
        addView(preview, LinearLayout.LayoutParams(dp(context, 76f), dp(context, 36f)))
        addView(TextView(context).apply {
            text = label
            textSize = 15f
            setTextColor(context.getColor(R.color.text_primary))
            setPadding(dp(context, 12f), 0, 0, 0)
        })
        setOnClickListener { onClick() }
    }

    /** 선 한 줄 미리보기 — 범례 표의 선 샘플과 같은 모양. */
    class LinePreview(context: Context, private val def: LineStyleDef) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        override fun onDraw(canvas: Canvas) {
            val d = resources.displayMetrics.density
            val sw = 2.6f * d
            paint.strokeWidth = sw
            paint.color = try { Color.parseColor(def.colorHex) } catch (e: IllegalArgumentException) { Color.GRAY }
            paint.pathEffect = LegendDraw.pathEffect(def.pattern, sw)
            val y = height / 2f
            val endX = width - 4f * d
            canvas.drawLine(4f * d, y, endX, y, paint)
            if (def.pattern == LinePattern.ARROW) LegendDraw.drawArrowHead(canvas, 4f * d, y, endX, y, paint, sw * 4.5f)
        }
    }

    /** 도형 심볼 미리보기. */
    class SymbolPreview(context: Context, private val def: SymbolDef) : View(context) {
        override fun onDraw(canvas: Canvas) {
            val d = resources.displayMetrics.density
            val r = 12f * d
            LegendDraw.drawSymbol(canvas, width / 2f, height / 2f, r, def, 1.2f * d)
        }
    }
}
