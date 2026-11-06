package com.thkim.jijang

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.thkim.jijang.data.db.AppDatabase

class JijangApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        // 디자인 v3는 라이트 전용 — 시스템 다크 모드에서 색이 어긋나지 않도록 고정
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }
}
