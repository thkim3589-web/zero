package com.thkim.jijang.ui.common

import android.graphics.PorterDuff
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.thkim.jijang.R

/** 현장 상태(DRAFT/SUBMITTED/EXPORTED)를 어디서든 같은 색·라벨의 칩으로 보여준다. */
object StatusChip {
    fun apply(chip: TextView, status: String) {
        val (label, bg, text) = when (status) {
            "SUBMITTED" -> Triple("제출됨", R.color.status_submitted_bg, R.color.status_submitted_text)
            "EXPORTED" -> Triple("내보내기 완료", R.color.status_exported_bg, R.color.status_exported_text)
            else -> Triple("작성중", R.color.status_draft_bg, R.color.status_draft_text)
        }
        val ctx = chip.context
        chip.text = label
        chip.background.mutate().setColorFilter(ContextCompat.getColor(ctx, bg), PorterDuff.Mode.SRC_IN)
        chip.setTextColor(ContextCompat.getColor(ctx, text))
    }
}
