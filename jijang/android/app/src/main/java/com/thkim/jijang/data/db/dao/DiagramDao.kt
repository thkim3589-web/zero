package com.thkim.jijang.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.thkim.jijang.data.model.BaseMapImage
import com.thkim.jijang.data.model.RouteLine
import com.thkim.jijang.data.model.RoutePoint
import com.thkim.jijang.data.model.RouteType
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagramDao {

    @Query("SELECT * FROM base_map_image WHERE siteCaseId = :siteCaseId AND routeType = :routeType LIMIT 1")
    suspend fun getBaseMapImage(siteCaseId: Long, routeType: RouteType): BaseMapImage?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBaseMapImage(image: BaseMapImage): Long

    /** 기존 이미지를 다시 고를 때는 이 UPDATE를 써야 한다 — [upsertBaseMapImage]를 다시
     * 부르면 (siteCaseId, routeType) UNIQUE 충돌로 기존 행이 지워졌다 새로 들어가면서
     * FK CASCADE로 그 위에 그려둔 route_line/route_point가 전부 같이 삭제돼버린다. */
    @Query("UPDATE base_map_image SET localFilePath = :path, importedAt = :importedAt WHERE id = :id")
    suspend fun updateBaseMapImagePath(id: Long, path: String, importedAt: Long)

    @Query("UPDATE base_map_image SET legendImagePath = :path WHERE id = :id")
    suspend fun updateLegendImage(id: Long, path: String?)

    @Query("UPDATE route_line SET colorHex = :colorHex, dashed = :dashed WHERE id = :id")
    suspend fun updateLineStyle(id: Long, colorHex: String, dashed: Boolean)

    @Query("UPDATE route_line SET lineLabel = :label WHERE id = :id")
    suspend fun updateLineLabel(id: Long, label: String)

    @Query("SELECT * FROM route_line WHERE baseMapImageId = :baseMapImageId ORDER BY lineIndex")
    fun observeLines(baseMapImageId: Long): Flow<List<RouteLine>>

    @Query("SELECT * FROM route_line WHERE baseMapImageId = :baseMapImageId ORDER BY lineIndex")
    suspend fun getLines(baseMapImageId: Long): List<RouteLine>

    @Query("SELECT * FROM route_line WHERE baseMapImageId = :baseMapImageId ORDER BY lineIndex LIMIT 1")
    suspend fun getFirstLine(baseMapImageId: Long): RouteLine?

    @Query("SELECT * FROM route_line WHERE baseMapImageId = :baseMapImageId AND lineIndex = :lineIndex LIMIT 1")
    suspend fun getLineByIndex(baseMapImageId: Long, lineIndex: Int): RouteLine?

    @Query("SELECT * FROM route_point WHERE routeLineId = :routeLineId ORDER BY pointOrder")
    suspend fun getPoints(routeLineId: Long): List<RoutePoint>

    @Insert
    suspend fun insertLine(line: RouteLine): Long

    @Delete
    suspend fun deleteLine(line: RouteLine)

    @Insert
    suspend fun insertPoints(points: List<RoutePoint>)

    @Query("DELETE FROM route_point WHERE routeLineId = :routeLineId")
    suspend fun clearPoints(routeLineId: Long)

    /** 편집 화면에서 "저장"을 누를 때, 한 선의 점 목록 전체를 통째로 교체한다. */
    @Transaction
    suspend fun replacePoints(routeLineId: Long, points: List<RoutePoint>) {
        clearPoints(routeLineId)
        insertPoints(points.mapIndexed { index, p -> p.copy(routeLineId = routeLineId, pointOrder = index) })
    }
}
