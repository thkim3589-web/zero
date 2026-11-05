package com.thkim.jijang.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.thkim.jijang.data.model.CostRateMaster
import com.thkim.jijang.data.model.LaborPriceMaster
import com.thkim.jijang.data.model.MaterialPriceMaster
import com.thkim.jijang.data.model.PriceUploadHistory

@Dao
interface PriceMasterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLaborPrices(items: List<LaborPriceMaster>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaterialPrices(items: List<MaterialPriceMaster>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCostRates(items: List<CostRateMaster>)

    @Insert
    suspend fun insertUploadHistory(history: PriceUploadHistory)

    @Query("SELECT * FROM labor_price_master WHERE processCode = :code AND effectiveYear = :year")
    suspend fun getLaborPrice(code: String, year: Int): LaborPriceMaster?

    @Query("SELECT * FROM material_price_master WHERE materialCode = :code AND effectiveYear = :year")
    suspend fun getMaterialPrice(code: String, year: Int): MaterialPriceMaster?

    @Query("SELECT * FROM cost_rate_master WHERE effectiveYear = :year")
    suspend fun getCostRates(year: Int): List<CostRateMaster>

    @Query(
        "SELECT * FROM labor_price_master WHERE effectiveYear = :year " +
            "AND (itemName LIKE '%' || :query || '%' OR processCode LIKE '%' || :query || '%') " +
            "ORDER BY itemName LIMIT 50"
    )
    suspend fun searchLaborPrices(year: Int, query: String): List<LaborPriceMaster>

    @Query(
        "SELECT * FROM material_price_master WHERE effectiveYear = :year " +
            "AND (materialName LIKE '%' || :query || '%' OR materialCode LIKE '%' || :query || '%') " +
            "ORDER BY materialName LIMIT 50"
    )
    suspend fun searchMaterialPrices(year: Int, query: String): List<MaterialPriceMaster>

    @Query("SELECT * FROM price_upload_history ORDER BY uploadedAt DESC")
    suspend fun getUploadHistory(): List<PriceUploadHistory>
}
