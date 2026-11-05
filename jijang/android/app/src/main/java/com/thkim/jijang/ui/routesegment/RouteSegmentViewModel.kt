package com.thkim.jijang.ui.routesegment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.thkim.jijang.data.db.AppDatabase
import com.thkim.jijang.data.model.RouteSegment

class RouteSegmentViewModel(
    private val db: AppDatabase,
    private val siteCaseId: Long
) : ViewModel() {

    suspend fun load(): List<RouteSegment> = db.routeSegmentDao().getAll(siteCaseId)

    suspend fun save(segments: List<RouteSegment>) {
        db.routeSegmentDao().replaceAll(siteCaseId, segments)
    }

    class Factory(
        private val db: AppDatabase,
        private val siteCaseId: Long
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RouteSegmentViewModel(db, siteCaseId) as T
    }
}
