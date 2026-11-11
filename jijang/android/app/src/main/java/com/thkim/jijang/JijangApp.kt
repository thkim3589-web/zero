package com.thkim.jijang

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.thkim.jijang.data.db.AppDatabase
import com.thkim.jijang.ui.common.CrashActivity
import kotlin.system.exitProcess

class JijangApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        // 디자인 v3는 라이트 전용 — 시스템 다크 모드에서 색이 어긋나지 않도록 고정
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        installCrashScreen()
    }

    /** 비정상 종료 시 시스템의 "앱이 중단됨" 대신 오류 내용을 보여주는 화면을 띄운다. */
    private fun installCrashScreen() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                // 화면에는 원인 요약만 보여준다 (전체 스택트레이스·기기 제조사/모델은 노출하지 않는다)
                val root = generateSequence(error) { it.cause }.last()
                val frames = root.stackTrace.take(6).joinToString("\n") { "   at $it" }
                val text = "★ 원인: ${root.javaClass.name}\n$frames\n\n" +
                    "앱 ${BuildConfig.VERSION_NAME} / Android API ${Build.VERSION.SDK_INT}\n스레드: ${thread.name}"
                startActivity(Intent(this, CrashActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra(CrashActivity.EXTRA_TRACE, text.take(12000))
                })
            } catch (e: Throwable) {
                previous?.uncaughtException(thread, error)
            }
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(10)
        }
    }
}
