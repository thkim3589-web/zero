package com.thkim.jijang.ui.checklist

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import com.google.android.material.card.MaterialCardView
import com.thkim.jijang.R

/**
 * ENG시트의 반복적인 필드 유형(2지선다 토글, 상세 텍스트, 단일선택, 체크)을
 * 코드에서 바로 만들어주는 헬퍼 — 14단계에서 ENG시트 전체 항목을 추가하면서,
 * 매번 XML을 새로 쓰는 대신 이 방식을 택했다. 각 함수는 "현재 값을 읽는 함수"를
 * 반환하고, 저장 시점에 그 함수들을 호출해서 값을 모은다.
 */
object FormBuilder {

    private fun dp(ctx: Context, value: Float): Float = value * ctx.resources.displayMetrics.density
    private fun dpi(ctx: Context, value: Float): Int = dp(ctx, value).toInt()

    private val sectionPalette = listOf(
        R.color.primary, R.color.accent_dark, R.color.badge_teal,
        R.color.badge_purple, R.color.badge_blue, R.color.status_ok
    )

    private fun label(ctx: Context, container: ViewGroup, text: String) {
        val tv = TextView(ctx)
        tv.text = text
        tv.setTextColor(ctx.getColor(R.color.text_secondary))
        tv.textSize = 12.5f
        tv.setTypeface(tv.typeface, Typeface.BOLD)
        tv.setPadding(0, dpi(ctx, 14f), 0, dpi(ctx, 6f))
        container.addView(tv)
    }

    private fun pillDrawable(ctx: Context, radius: Float, fill: Int, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(ctx, radius)
            setColor(fill)
            if (strokeColor != null) setStroke(dpi(ctx, 1f), strokeColor)
        }

    fun textField(ctx: Context, container: ViewGroup, labelText: String, initial: String?, multiline: Boolean = false): () -> String? {
        label(ctx, container, labelText)
        val edit = EditText(ContextThemeWrapper(ctx, R.style.Widget_Jijang_EditText), null, 0, R.style.Widget_Jijang_EditText)
        if (multiline) {
            edit.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            edit.minLines = 2
        }
        edit.setText(initial)
        edit.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        container.addView(edit)
        return { edit.text.toString().ifBlank { null } }
    }

    /** "seg" 2지선다 — trueLabel을 고르면 그 문자열이, falseLabel을 고르면 그 문자열이 값이 된다. */
    fun toggleField(
        ctx: Context,
        container: ViewGroup,
        labelText: String,
        trueLabel: String,
        falseLabel: String,
        initial: String?,
        defaultToFalse: Boolean = true
    ): () -> String? {
        label(ctx, container, labelText)
        var selected: String? = initial ?: if (defaultToFalse) falseLabel else null

        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val btnTrue = TextView(ctx)
        val btnFalse = TextView(ctx)
        listOf(btnTrue to trueLabel, btnFalse to falseLabel).forEachIndexed { idx, (btn, text) ->
            btn.text = text
            btn.gravity = Gravity.CENTER
            btn.textSize = 13.5f
            btn.setTypeface(btn.typeface, Typeface.BOLD)
            btn.setPadding(dpi(ctx, 12f), dpi(ctx, 12f), dpi(ctx, 12f), dpi(ctx, 12f))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            if (idx == 1) lp.marginStart = dpi(ctx, 8f)
            btn.layoutParams = lp
        }

        val outlineColor = Color.parseColor("#F5F7F9")
        fun refresh() {
            val trueOn = selected == trueLabel
            val falseOn = selected == falseLabel
            btnTrue.background = if (trueOn) pillDrawable(ctx, 10f, ctx.getColor(R.color.status_warn))
                                  else pillDrawable(ctx, 10f, outlineColor, ctx.getColor(R.color.line))
            btnTrue.setTextColor(if (trueOn) Color.WHITE else ctx.getColor(R.color.text_secondary))
            btnFalse.background = if (falseOn) pillDrawable(ctx, 10f, ctx.getColor(R.color.status_ok))
                                   else pillDrawable(ctx, 10f, outlineColor, ctx.getColor(R.color.line))
            btnFalse.setTextColor(if (falseOn) Color.WHITE else ctx.getColor(R.color.text_secondary))
        }
        btnTrue.setOnClickListener { selected = trueLabel; refresh() }
        btnFalse.setOnClickListener { selected = falseLabel; refresh() }
        refresh()

        row.addView(btnTrue)
        row.addView(btnFalse)
        container.addView(row)
        return { selected }
    }

    /** 토글 + 그 아래 상세 텍스트 한 줄, 항상 같이 다니는 조합 (ENG시트 pair() 패턴). */
    fun toggleWithDetail(
        ctx: Context,
        container: ViewGroup,
        labelText: String,
        trueLabel: String,
        falseLabel: String,
        initialToggle: String?,
        initialDetail: String?,
        defaultToFalse: Boolean = true
    ): Pair<() -> String?, () -> String?> {
        val getToggle = toggleField(ctx, container, labelText, trueLabel, falseLabel, initialToggle, defaultToFalse)
        val getDetail = textField(ctx, container, "상세내역", initialDetail)
        return getToggle to getDetail
    }

    fun checkboxField(ctx: Context, container: ViewGroup, labelText: String, initial: Boolean): () -> Boolean {
        val row = CheckBox(ctx)
        row.text = labelText
        row.isChecked = initial
        row.textSize = 14f
        row.setTextColor(ctx.getColor(R.color.text_primary))
        row.buttonTintList = ColorStateList.valueOf(ctx.getColor(R.color.accent))
        row.setPadding(dpi(ctx, 2f), dpi(ctx, 10f), dpi(ctx, 2f), dpi(ctx, 10f))
        row.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        container.addView(row)
        return { row.isChecked }
    }

    /** 탭하면 선택 다이얼로그가 뜨는 단일 선택 필드. */
    fun selectField(ctx: Context, container: ViewGroup, labelText: String, options: List<String>, initial: String?): () -> String? {
        label(ctx, container, labelText)
        var selected: String? = initial

        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setBackgroundResource(R.drawable.bg_input_field)
        row.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val display = TextView(ctx)
        display.textSize = 14.5f
        display.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

        val chevron = ImageView(ctx)
        chevron.setImageResource(R.drawable.ic_chevron_right)
        chevron.rotation = 90f
        chevron.layoutParams = LinearLayout.LayoutParams(dpi(ctx, 16f), dpi(ctx, 16f))

        fun refresh() {
            display.text = selected ?: "선택"
            display.setTextColor(if (selected == null) ctx.getColor(R.color.text_secondary) else ctx.getColor(R.color.text_primary))
        }
        refresh()
        row.setOnClickListener {
            AlertDialog.Builder(ctx)
                .setTitle(labelText)
                .setItems(options.toTypedArray()) { _, which -> selected = options[which]; refresh() }
                .show()
        }
        row.addView(display)
        row.addView(chevron)
        container.addView(row)
        return { selected }
    }

    /** 여러 칸(cells) 중 하나만 "O"로 표시되는 라디오 그룹 — 선택된 항목 이름을 반환한다. */
    fun radioGroupField(ctx: Context, container: ViewGroup, labelText: String, options: List<String>, initial: String?): () -> String? {
        label(ctx, container, labelText)
        var selected: String? = initial
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val outlineColor = Color.parseColor("#F5F7F9")
        val pills = options.map { opt ->
            val pill = TextView(ctx)
            pill.text = opt
            pill.textSize = 13.5f
            pill.gravity = Gravity.CENTER
            pill.setPadding(dpi(ctx, 18f), dpi(ctx, 12f), dpi(ctx, 18f), dpi(ctx, 12f))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = dpi(ctx, 8f)
            pill.layoutParams = lp
            row.addView(pill)
            opt to pill
        }
        fun refresh() {
            pills.forEach { (opt, pill) ->
                if (opt == selected) {
                    pill.background = pillDrawable(ctx, 10f, ctx.getColor(R.color.primary))
                    pill.setTextColor(Color.WHITE)
                    pill.setTypeface(pill.typeface, Typeface.BOLD)
                } else {
                    pill.background = pillDrawable(ctx, 10f, outlineColor, ctx.getColor(R.color.line))
                    pill.setTextColor(ctx.getColor(R.color.text_primary))
                    pill.setTypeface(pill.typeface, Typeface.NORMAL)
                }
            }
        }
        pills.forEach { (opt, pill) -> pill.setOnClickListener { selected = opt; refresh() } }
        refresh()
        val scroll = HorizontalScrollView(ctx)
        scroll.isHorizontalScrollBarEnabled = false
        scroll.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        scroll.addView(row)
        container.addView(scroll)
        return { selected }
    }

    /**
     * `android:tag="section_header"`가 붙은 TextView를 찾아, 그 다음 헤더가 나오기 전까지의
     * 형제 뷰들과 함께 하나의 카드(번호 배지 + 제목 + 회전하는 화살표 + 접이식 본문)로
     * 다시 감싼다. 필드 50개짜리 화면을 스크롤 지옥으로 만들지 않으려고 14단계에서 추가했고,
     * 15단계에서 플랫한 텍스트 헤더 대신 카드형 아코디언으로 다시 다듬었다. 첫 섹션만 기본으로 펼쳐둔다.
     */
    fun makeSectionsCollapsible(root: LinearLayout) {
        val ctx = root.context
        var i = 0
        var sectionIndex = 0
        while (i < root.childCount) {
            val header = root.getChildAt(i)
            if (header is TextView && header.tag == "section_header") {
                val titleText = header.text.toString()
                val bodyChildren = mutableListOf<View>()
                var j = i + 1
                while (j < root.childCount) {
                    val next = root.getChildAt(j)
                    if (next is TextView && next.tag == "section_header") break
                    if (next.tag == "section_end") break
                    bodyChildren.add(next)
                    j++
                }
                root.removeView(header)
                bodyChildren.forEach { root.removeView(it) }

                val isOpen = sectionIndex == 0
                val badgeColor = ctx.getColor(sectionPalette[sectionIndex % sectionPalette.size])

                val card = MaterialCardView(ctx)
                card.radius = dp(ctx, 14f)
                card.cardElevation = dp(ctx, 1.5f)
                card.setCardBackgroundColor(ctx.getColor(R.color.surface))
                card.strokeColor = ctx.getColor(R.color.line)
                card.strokeWidth = dpi(ctx, 1f)
                val cardLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                cardLp.bottomMargin = dpi(ctx, 12f)
                card.layoutParams = cardLp

                val cardContent = LinearLayout(ctx)
                cardContent.orientation = LinearLayout.VERTICAL
                card.addView(cardContent)

                val headerRow = LinearLayout(ctx)
                headerRow.orientation = LinearLayout.HORIZONTAL
                headerRow.gravity = Gravity.CENTER_VERTICAL
                headerRow.setPadding(dpi(ctx, 16f), dpi(ctx, 14f), dpi(ctx, 16f), dpi(ctx, 14f))
                headerRow.isClickable = true
                headerRow.isFocusable = true
                val outValue = TypedValue()
                ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                headerRow.setBackgroundResource(outValue.resourceId)
                headerRow.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

                val badge = TextView(ctx)
                badge.text = "${sectionIndex + 1}"
                badge.setTextColor(Color.WHITE)
                badge.textSize = 12f
                badge.gravity = Gravity.CENTER
                badge.setTypeface(badge.typeface, Typeface.BOLD)
                val badgeSize = dpi(ctx, 26f)
                badge.layoutParams = LinearLayout.LayoutParams(badgeSize, badgeSize)
                badge.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(badgeColor) }

                val titleView = TextView(ctx)
                titleView.text = titleText
                titleView.setTextColor(ctx.getColor(R.color.text_primary))
                titleView.textSize = 15.5f
                titleView.setTypeface(titleView.typeface, Typeface.BOLD)
                val titleLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                titleLp.marginStart = dpi(ctx, 12f)
                titleView.layoutParams = titleLp

                val chevron = ImageView(ctx)
                chevron.setImageResource(R.drawable.ic_chevron_right)
                chevron.layoutParams = LinearLayout.LayoutParams(dpi(ctx, 20f), dpi(ctx, 20f))
                chevron.rotation = if (isOpen) 90f else 0f

                headerRow.addView(badge)
                headerRow.addView(titleView)
                headerRow.addView(chevron)

                val divider = View(ctx)
                divider.setBackgroundColor(ctx.getColor(R.color.line))
                divider.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpi(ctx, 1f))
                divider.visibility = if (isOpen) View.VISIBLE else View.GONE

                val wrapper = LinearLayout(ctx)
                wrapper.orientation = LinearLayout.VERTICAL
                wrapper.setPadding(dpi(ctx, 16f), 0, dpi(ctx, 16f), dpi(ctx, 14f))
                bodyChildren.forEach { wrapper.addView(it) }
                wrapper.visibility = if (isOpen) View.VISIBLE else View.GONE

                cardContent.addView(headerRow)
                cardContent.addView(divider)
                cardContent.addView(wrapper)

                headerRow.setOnClickListener {
                    val show = wrapper.visibility != View.VISIBLE
                    wrapper.visibility = if (show) View.VISIBLE else View.GONE
                    divider.visibility = wrapper.visibility
                    chevron.animate().rotation(if (show) 90f else 0f).setDuration(180).start()
                }

                root.addView(card, i)
                sectionIndex++
                i += 1
            } else {
                i++
            }
        }
    }
}
