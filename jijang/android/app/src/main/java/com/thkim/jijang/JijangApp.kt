package com.thkim.jijang

import android.app.Application
import com.thkim.jijang.data.db.AppDatabase

class JijangApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
}
