package com.thkim.jijang.ui.diagram

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.thkim.jijang.data.db.AppDatabase
import com.thkim.jijang.data.model.BaseMapImage
import com.thkim.jijang.data.model.FacilityPhase
import com.thkim.jijang.data.model.RouteLine
import com.thkim.jijang.data.model.RoutePoint
import com.thkim.jijang.data.model.RouteType

class DiagramViewModel(
    private val db: AppDatabase,
    private val siteCaseId: Long,
    private val routeType: RouteType
) : ViewModel() {

    suspend fun getBaseMapImage(): BaseMapImage? =
        db.diagramDao().getBaseMapImage(siteCaseId, routeType)

    /** 이미지를 다시 고를 때(잘못 캡처해서 재업로드 등) 기존 행을 지웠다 새로 만들면
     * (siteCaseId, routeType) UNIQUE 충돌로 인한 FK CASCADE 때문에 그 위에 그려둔 모든
     * 선/점이 같이 삭제된다 — 그래서 기존 행이 있으면 경로만 UPDATE해서 id를 그대로
     * 유지하고, 완전히 새 이미지일 때만 새로 만든다. */
    suspend fun setBaseMapImage(localFilePath: String): BaseMapImage {
        val existing = getBaseMapImage()
        if (existing != null) {
            val importedAt = System.currentTimeMillis()
            db.diagramDao().updateBaseMapImagePath(existing.id, localFilePath, importedAt)
            return existing.copy(localFilePath = localFilePath, importedAt = importedAt)
        }
        val image = BaseMapImage(siteCaseId = siteCaseId, routeType = routeType, localFilePath = localFilePath)
        val id = db.diagramDao().upsertBaseMapImage(image)
        return image.copy(id = id)
    }

    suspend fun setScale(baseMapImageId: Long, metersPerWidth: Double?) {
        db.diagramDao().updateScale(baseMapImageId, metersPerWidth)
    }

    /** 직접 그리기 대신 실제 범례 이미지를 그대로 첨부할 수 있게 한다 — 내보내기 시 우하단에 합성된다. */
    suspend fun setLegendImage(baseMapImageId: Long, localFilePath: String?) {
        db.diagramDao().updateLegendImage(baseMapImageId, localFilePath)
    }

    /** 선 색상/점선 여부를 사용자가 직접 바꿀 수 있게 한다 (기본은 신설=파랑/철거=빨강이지만 자유롭게 변경 가능). */
    suspend fun setLineStyle(lineId: Long, colorHex: String, dashed: Boolean) {
        db.diagramDao().updateLineStyle(lineId, colorHex, dashed)
    }

    /** 17단계 — 신설/철거 2줄 고정 대신, 필요한 만큼(케이블 종류별로 5개든 몇 개든) 선을
     * 추가/삭제할 수 있게 일반화했다. 기존에 신설/철거로 만들어져 있던 선도 그냥
     * "이름이 신설/철거인 선"으로 취급되어 그대로 호환된다. */
    suspend fun getLines(baseMapImageId: Long): List<RouteLine> =
        db.diagramDao().getLines(baseMapImageId)

    suspend fun addLine(baseMapImageId: Long, label: String, colorHex: String, style: com.thkim.jijang.legend.LineStyleDef? = null): RouteLine {
        val nextIndex = (db.diagramDao().getLines(baseMapImageId).maxOfOrNull { it.lineIndex } ?: -1) + 1
        val line = RouteLine(
            baseMapImageId = baseMapImageId, lineIndex = nextIndex, lineLabel = label, colorHex = colorHex,
            dashed = style != null && style.pattern != com.thkim.jijang.legend.LinePattern.SOLID,
            styleId = style?.id
        )
        val id = db.diagramDao().insertLine(line)
        return line.copy(id = id)
    }

    /** 범례에서 고른 선 종류로 색/패턴/이름을 한 번에 바꾼다. */
    suspend fun setLineLegendStyle(lineId: Long, def: com.thkim.jijang.legend.LineStyleDef) {
        db.diagramDao().updateLineLegendStyle(
            lineId, def.id, def.colorHex, def.pattern != com.thkim.jijang.legend.LinePattern.SOLID, def.label
        )
    }

    suspend fun deleteLine(line: RouteLine) {
        db.diagramDao().deleteLine(line)
    }

    suspend fun renameLine(lineId: Long, label: String) {
        db.diagramDao().updateLineLabel(lineId, label)
    }

    suspend fun loadPoints(routeLineId: Long): List<EditablePoint> =
        db.diagramDao().getPoints(routeLineId).map {
            EditablePoint(it.relativeX.toFloat(), it.relativeY.toFloat(), it.markerType, it.label, it.status, it.symbolId)
        }

    suspend fun savePoints(routeLineId: Long, points: List<EditablePoint>) {
        val entities = points.map {
            RoutePoint(
                routeLineId = routeLineId,
                pointOrder = 0,
                relativeX = it.x.toDouble(),
                relativeY = it.y.toDouble(),
                markerType = it.markerType,
                label = it.label,
                status = it.status,
                symbolId = it.symbolId
            )
        }
        db.diagramDao().replacePoints(routeLineId, entities)
    }

    /** 지도 위에 "함체로 지정"할 때 고를 수 있는 목록 — 6단계 체크리스트에서 이미 입력한 함체들. */
    suspend fun getEnclosureOptions(): List<String> {
        val dao = db.checklistDao()
        val existing = dao.getEnclosureItems(siteCaseId, FacilityPhase.EXISTING)
            .map { "[기설#${it.seq}] ${it.kepcoNo ?: ""} ${it.jpNo ?: ""}".trim() }
        val new = dao.getEnclosureItems(siteCaseId, FacilityPhase.NEW)
            .map { "[신설#${it.seq}] ${it.kepcoNo ?: ""} ${it.jpNo ?: ""}".trim() }
        return existing + new
    }

    /** 지도 위에 "전주로 지정"할 때 고를 수 있는 목록 — 10단계 구간별 입력에서 이미 입력한 전주들. */
    suspend fun getPoleOptions(): List<String> =
        db.routeSegmentDao().getAll(siteCaseId).map { it.poleName }.distinct()

    /** 지도 위에 "접속점으로 지정"할 때 고를 수 있는 목록 — 접속정보를 입력해둔 구간들. */
    suspend fun getConnectionOptions(): List<String> =
        db.routeSegmentDao().getSegmentsWithSplice(siteCaseId).map { "${it.poleName} - ${it.spliceType?.label}" }

    class Factory(
        private val db: AppDatabase,
        private val siteCaseId: Long,
        private val routeType: RouteType
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DiagramViewModel(db, siteCaseId, routeType) as T
    }
}
