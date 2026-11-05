package com.thkim.jijang.ui.priceupload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.thkim.jijang.data.db.AppDatabase
import com.thkim.jijang.data.excel.PriceMasterParser
import com.thkim.jijang.data.model.PriceUploadHistory
import java.io.File

data class PriceUploadResult(val laborCount: Int, val materialCount: Int)

class PriceUploadViewModel(private val db: AppDatabase) : ViewModel() {

    suspend fun uploadHistory(): List<PriceUploadHistory> = db.priceMasterDao().getUploadHistory()

    suspend fun parseAndSave(file: File, effectiveYear: Int, sourceFileName: String): PriceUploadResult {
        val uploadedAt = System.currentTimeMillis()
        val parsed = PriceMasterParser.parse(file, effectiveYear, sourceFileName, uploadedAt)

        val dao = db.priceMasterDao()
        dao.insertLaborPrices(parsed.laborItems)
        dao.insertMaterialPrices(parsed.materialItems)
        dao.insertUploadHistory(
            PriceUploadHistory(
                fileName = sourceFileName,
                effectiveYear = effectiveYear,
                laborRowCount = parsed.laborItems.size,
                materialRowCount = parsed.materialItems.size,
                uploadedAt = uploadedAt,
                uploadedBy = null
            )
        )
        return PriceUploadResult(parsed.laborItems.size, parsed.materialItems.size)
    }

    class Factory(private val db: AppDatabase) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PriceUploadViewModel(db) as T
    }
}
