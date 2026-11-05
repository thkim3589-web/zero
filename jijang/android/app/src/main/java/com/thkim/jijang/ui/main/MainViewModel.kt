package com.thkim.jijang.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.thkim.jijang.data.db.AppDatabase
import com.thkim.jijang.data.model.SiteCase
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class MainViewModel(private val db: AppDatabase) : ViewModel() {

    val cases: StateFlow<List<SiteCase>> = db.siteCaseDao().observeAll()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    fun addCase(caseName: String) {
        if (caseName.isBlank()) return
        viewModelScope.launch {
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            db.siteCaseDao().insert(
                SiteCase(
                    caseName = caseName.trim(),
                    companyName = "",
                    effectiveYear = currentYear
                )
            )
        }
    }

    class Factory(private val db: AppDatabase) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(db) as T
    }
}
