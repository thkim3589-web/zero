package com.thkim.jijang.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.thkim.jijang.data.model.RouteSegment

@Dao
interface RouteSegmentDao {

    @Query("SELECT * FROM route_segment WHERE siteCaseId = :siteCaseId ORDER BY seq")
    suspend fun getAll(siteCaseId: Long): List<RouteSegment>

    @Query("SELECT * FROM route_segment WHERE siteCaseId = :siteCaseId AND spliceType IS NOT NULL ORDER BY seq")
    suspend fun getSegmentsWithSplice(siteCaseId: Long): List<RouteSegment>

    @Insert
    suspend fun insertAll(items: List<RouteSegment>)

    @Query("DELETE FROM route_segment WHERE siteCaseId = :siteCaseId")
    suspend fun clear(siteCaseId: Long)

    /** 저장 시점의 구간 목록으로 통째로 교체한다 (행 추가/삭제/순서 변경을 그대로 반영). */
    @Transaction
    suspend fun replaceAll(siteCaseId: Long, items: List<RouteSegment>) {
        clear(siteCaseId)
        if (items.isNotEmpty()) insertAll(items)
    }
}
