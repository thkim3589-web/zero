package com.thkim.jijang.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.thkim.jijang.data.model.WorkQuantity

@Dao
interface WorkQuantityDao {

    @Query("SELECT * FROM work_quantity WHERE siteCaseId = :siteCaseId AND masterType = :masterType ORDER BY id")
    suspend fun getAll(siteCaseId: Long, masterType: String): List<WorkQuantity>

    @Insert
    suspend fun insertAll(items: List<WorkQuantity>)

    @Query("DELETE FROM work_quantity WHERE siteCaseId = :siteCaseId AND masterType = :masterType")
    suspend fun clear(siteCaseId: Long, masterType: String)

    /** 저장 시점의 목록으로 통째로 교체한다 (항목 추가/삭제/수량 변경을 그대로 반영). */
    @Transaction
    suspend fun replaceAll(siteCaseId: Long, masterType: String, items: List<WorkQuantity>) {
        clear(siteCaseId, masterType)
        if (items.isNotEmpty()) insertAll(items)
    }
}
