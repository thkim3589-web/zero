package com.thkim.jijang.ui.common

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * 앱이 비정상 종료될 때 오류 내용을 화면에 보여주는 진단 화면.
 * 별도 프로세스(:crash)에서 뜨므로 메인 화면이 계속 꺼지는 상황에서도 표시된다.
 * 일부러 AppCompat/리소스 테마에 의존하지 않고 코드로만 구성한다.
 */
class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trace = intent.getStringExtra(EXTRA_TRACE) ?: "(오류 정보 없음)"
        val dp = resources.displayMetrics.density

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (40 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
        }
        root.addView(TextView(this).apply {
            text = "앱에 오류가 발생했습니다"
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "아래 내용을 화면 캡처하거나 복사해서 개발자에게 전달해 주세요."
            textSize = 13f
            setPadding(0, (6 * dp).toInt(), 0, (10 * dp).toInt())
        })
        val scroll = ScrollView(this)
        scroll.addView(TextView(this).apply {
            text = trace
            textSize = 10.5f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
        })
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Button(this).apply {
            text = "복사"
            setOnClickListener {
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("crash", trace))
                Toast.makeText(this@CrashActivity, "복사했습니다", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(Button(this).apply {
            text = "닫기"
            setOnClickListener { finishAffinity() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(buttons)
        setContentView(root)
    }

    companion object {
        const val EXTRA_TRACE = "trace"
    }
}
