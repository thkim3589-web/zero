package com.thkim.jijang.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.thkim.jijang.data.db.AppDatabase
import com.thkim.jijang.data.model.SiteCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class MainViewModel(private val db: AppDatabase) : ViewModel() {

    private val allCases = db.siteCaseDao().observeAll()

    /** null = 전체, 그 외 DRAFT / SUBMITTED / EXPORTED */
    private val statusFilter = MutableStateFlow<String?>(null)

    val cases: StateFlow<List<SiteCase>> = combine(allCases, statusFilter) { list, status ->
        if (status == null) list else list.filter { it.status == status }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 필터 탭에 표시할 건수: 전체/작성중/제출/완료 */
    val counts: StateFlow<Map<String?, Int>> = allCases.combine(statusFilter) { list, _ ->
        mapOf<String?, Int>(
            null to list.size,
            "DRAFT" to list.count { it.status == "DRAFT" },
            "SUBMITTED" to list.count { it.status == "SUBMITTED" },
            "EXPORTED" to list.count { it.status == "EXPORTED" },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())


    fun setFilter(status: String?) { statusFilter.value = status }

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
