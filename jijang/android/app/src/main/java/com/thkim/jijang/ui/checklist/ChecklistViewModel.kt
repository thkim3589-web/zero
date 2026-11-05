package com.thkim.jijang.ui.checklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.thkim.jijang.data.db.AppDatabase
import com.thkim.jijang.data.model.CableItem
import com.thkim.jijang.data.model.ChecklistDetail
import com.thkim.jijang.data.model.EnclosureItem
import com.thkim.jijang.data.model.FacilityPhase
import com.thkim.jijang.data.model.PoleInfo

data class ChecklistFormData(
    val detail: ChecklistDetail,
    val existingCables: List<CableItem>,
    val newCables: List<CableItem>,
    val existingEnclosures: List<EnclosureItem>,
    val newEnclosures: List<EnclosureItem>,
    val poleInfo: PoleInfo?
)

class ChecklistViewModel(
    private val db: AppDatabase,
    private val siteCaseId: Long
) : ViewModel() {

    suspend fun load(): ChecklistFormData {
        val dao = db.checklistDao()
        return ChecklistFormData(
            detail = dao.getChecklist(siteCaseId) ?: ChecklistDetail(siteCaseId = siteCaseId),
            existingCables = dao.getCableItems(siteCaseId, FacilityPhase.EXISTING),
            newCables = dao.getCableItems(siteCaseId, FacilityPhase.NEW),
            existingEnclosures = dao.getEnclosureItems(siteCaseId, FacilityPhase.EXISTING),
            newEnclosures = dao.getEnclosureItems(siteCaseId, FacilityPhase.NEW),
            poleInfo = dao.getPoleInfo(siteCaseId)
        )
    }

    suspend fun save(
        detail: ChecklistDetail,
        existingCables: List<CableItem>,
        newCables: List<CableItem>,
        existingEnclosures: List<EnclosureItem>,
        newEnclosures: List<EnclosureItem>,
        poleInfo: PoleInfo
    ) {
        val dao = db.checklistDao()
        dao.upsertChecklist(detail.copy(siteCaseId = siteCaseId))
        dao.replaceCableItems(siteCaseId, FacilityPhase.EXISTING, existingCables)
        dao.replaceCableItems(siteCaseId, FacilityPhase.NEW, newCables)
        dao.replaceEnclosureItems(siteCaseId, FacilityPhase.EXISTING, existingEnclosures)
        dao.replaceEnclosureItems(siteCaseId, FacilityPhase.NEW, newEnclosures)
        dao.upsertPoleInfo(siteCaseId, poleInfo)
    }

    class Factory(
        private val db: AppDatabase,
        private val siteCaseId: Long
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ChecklistViewModel(db, siteCaseId) as T
    }
}
