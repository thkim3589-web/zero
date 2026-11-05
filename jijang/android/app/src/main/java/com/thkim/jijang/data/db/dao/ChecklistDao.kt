package com.thkim.jijang.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.thkim.jijang.data.model.CableItem
import com.thkim.jijang.data.model.ChecklistDetail
import com.thkim.jijang.data.model.EnclosureItem
import com.thkim.jijang.data.model.FacilityPhase
import com.thkim.jijang.data.model.PoleInfo

@Dao
interface ChecklistDao {

    @Query("SELECT * FROM checklist_detail WHERE siteCaseId = :siteCaseId")
    suspend fun getChecklist(siteCaseId: Long): ChecklistDetail?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChecklist(detail: ChecklistDetail)

    @Query("SELECT * FROM cable_item WHERE siteCaseId = :siteCaseId AND phase = :phase ORDER BY seq")
    suspend fun getCableItems(siteCaseId: Long, phase: FacilityPhase): List<CableItem>

    @Query("DELETE FROM cable_item WHERE siteCaseId = :siteCaseId AND phase = :phase")
    suspend fun clearCableItems(siteCaseId: Long, phase: FacilityPhase)

    @Insert
    suspend fun insertCableItems(items: List<CableItem>)

    /** 저장 시점의 리스트로 통째로 교체한다 (행 추가/삭제를 그대로 반영). */
    @Transaction
    suspend fun replaceCableItems(siteCaseId: Long, phase: FacilityPhase, items: List<CableItem>) {
        clearCableItems(siteCaseId, phase)
        if (items.isNotEmpty()) insertCableItems(items)
    }

    @Query("SELECT * FROM enclosure_item WHERE siteCaseId = :siteCaseId AND phase = :phase ORDER BY seq")
    suspend fun getEnclosureItems(siteCaseId: Long, phase: FacilityPhase): List<EnclosureItem>

    @Query("DELETE FROM enclosure_item WHERE siteCaseId = :siteCaseId AND phase = :phase")
    suspend fun clearEnclosureItems(siteCaseId: Long, phase: FacilityPhase)

    @Insert
    suspend fun insertEnclosureItems(items: List<EnclosureItem>)

    @Transaction
    suspend fun replaceEnclosureItems(siteCaseId: Long, phase: FacilityPhase, items: List<EnclosureItem>) {
        clearEnclosureItems(siteCaseId, phase)
        if (items.isNotEmpty()) insertEnclosureItems(items)
    }

    @Query("SELECT * FROM pole_info WHERE siteCaseId = :siteCaseId LIMIT 1")
    suspend fun getPoleInfo(siteCaseId: Long): PoleInfo?

    @Insert
    suspend fun insertPoleInfo(pole: PoleInfo): Long

    @Update
    suspend fun updatePoleInfo(pole: PoleInfo)

    @Transaction
    suspend fun upsertPoleInfo(siteCaseId: Long, pole: PoleInfo) {
        val existing = getPoleInfo(siteCaseId)
        if (existing == null) {
            insertPoleInfo(pole.copy(id = 0, siteCaseId = siteCaseId))
        } else {
            updatePoleInfo(pole.copy(id = existing.id, siteCaseId = siteCaseId))
        }
    }
}
