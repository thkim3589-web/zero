package com.thkim.jijang.ui.workquantity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.thkim.jijang.data.db.AppDatabase
import com.thkim.jijang.data.model.WorkQuantity

/** 단가 마스터를 종류에 상관없이 동일한 형태로 다루기 위한 검색/표시용 래퍼. */
data class PriceItemOption(val code: String, val label: String, val unit: String)

data class WorkQuantityRow(val workQuantity: WorkQuantity, val displayName: String, val unit: String)

class WorkQuantityViewModel(
    private val db: AppDatabase,
    private val siteCaseId: Long,
    private val effectiveYear: Int
) : ViewModel() {

    suspend fun loadRows(masterType: String): List<WorkQuantityRow> {
        val items = db.workQuantityDao().getAll(siteCaseId, masterType)
        return items.map { wq ->
            if (masterType == MASTER_TYPE_LABOR) {
                val master = db.priceMasterDao().getLaborPrice(wq.code, effectiveYear)
                WorkQuantityRow(wq, master?.itemName ?: "(단가 없음: ${wq.code})", master?.unit ?: "")
            } else {
                val master = db.priceMasterDao().getMaterialPrice(wq.code, effectiveYear)
                WorkQuantityRow(wq, master?.materialName ?: "(단가 없음: ${wq.code})", master?.unit ?: "")
            }
        }
    }

    suspend fun searchLabor(query: String): List<PriceItemOption> =
        db.priceMasterDao().searchLaborPrices(effectiveYear, query)
            .map { PriceItemOption(it.processCode, "${it.itemName} (${it.processCode})", it.unit) }

    suspend fun searchMaterial(query: String): List<PriceItemOption> =
        db.priceMasterDao().searchMaterialPrices(effectiveYear, query)
            .map { PriceItemOption(it.materialCode, "${it.materialName} (${it.materialCode})", it.unit) }

    suspend fun save(laborRows: List<WorkQuantity>, materialRows: List<WorkQuantity>) {
        db.workQuantityDao().replaceAll(siteCaseId, MASTER_TYPE_LABOR, laborRows)
        db.workQuantityDao().replaceAll(siteCaseId, MASTER_TYPE_MATERIAL, materialRows)
    }

    companion object {
        const val MASTER_TYPE_LABOR = "LABOR"
        const val MASTER_TYPE_MATERIAL = "MATERIAL"
    }

    class Factory(
        private val db: AppDatabase,
        private val siteCaseId: Long,
        private val effectiveYear: Int
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            WorkQuantityViewModel(db, siteCaseId, effectiveYear) as T
    }
}
