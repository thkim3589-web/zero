package com.thkim.jijang.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class RouteType { BEFORE, AFTER }   // 개황도(전) / 개황도(후)

/**
 * GIS에서 캡처해서 불러온 배경 이미지 (현장 건 + 전/후 조합당 1장).
 * 이 이미지 자체는 고정 자산이며, 그 위에 그려지는 선(RouteLine/RoutePoint)은
 * 이 이미지의 상대좌표(0~1)로 저장되므로 이미지가 그대로인 한 항상 정확히 맞는다.
 */
@Entity(
    tableName = "base_map_image",
    foreignKeys = [ForeignKey(
        entity = SiteCase::class,
        parentColumns = ["id"],
        childColumns = ["siteCaseId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("siteCaseId"), Index(value = ["siteCaseId", "routeType"], unique = true)]
)
data class BaseMapImage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val siteCaseId: Long,
    val routeType: RouteType,
    val localFilePath: String,   // 기기에 저장된 캡처 이미지 경로
    val importedAt: Long = System.currentTimeMillis(),
    // 직접 그리는 범례 대신, 실제 범례 이미지(사진/캡처)를 그대로 붙여 넣을 수 있게 한 필드.
    // 개황도 우하단에 그대로 합성되어 내보내진다.
    val legendImagePath: String? = null
)

/**
 * 케이블 경로. 지도 위에 그리는 "선"을 이미지가 아니라 좌표 리스트로 저장한다.
 * 언제든 점 추가/이동/삭제만으로 재편집 가능하며, 내보내기 시점에만
 * 배경 이미지 위에 선을 그려 합성 이미지를 렌더링한다.
 */
@Entity(
    tableName = "route_line",
    foreignKeys = [ForeignKey(
        entity = BaseMapImage::class,
        parentColumns = ["id"],
        childColumns = ["baseMapImageId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("baseMapImageId")]
)
data class RouteLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val baseMapImageId: Long,
    val lineIndex: Int,             // 한 이미지에 여러 선이 있을 수 있음
    val lineLabel: String? = null,  // 신설/철거 구분 등
    val colorHex: String = "#FF0000",
    // 실제 개황도 범례는 "신설=점선, 기설=실선"을 쓰므로, 색상과 별도로 선 모양도
    // 사용자가 직접 고를 수 있게 한다 (16단계 — 카문다 모델러의 색상/선종류 변경 참고).
    val dashed: Boolean = false,
    // 범례 선 종류 id (LegendCatalog.lines). null이면 위의 colorHex/dashed를 그대로 쓴다 (기존 데이터 호환).
    val styleId: String? = null
)

/**
 * 선을 구성하는 점. lat/lng이 아니라 배경 이미지 안에서의 상대좌표(0.0~1.0)로 저장한다.
 * (0,0)=이미지 좌상단, (1,1)=이미지 우하단. 화면 크기/줌과 무관하게 항상 동일한
 * 위치를 가리키므로, 이미지를 다시 캡처하지 않는 한 배경이 어긋나는 문제가 없다.
 */
@Entity(
    tableName = "route_point",
    foreignKeys = [ForeignKey(
        entity = RouteLine::class,
        parentColumns = ["id"],
        childColumns = ["routeLineId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("routeLineId")]
)
data class RoutePoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routeLineId: Long,
    val pointOrder: Int,       // 폴리라인 내 순서 (편집 시 재정렬)
    val relativeX: Double,     // 0.0 ~ 1.0
    val relativeY: Double,     // 0.0 ~ 1.0
    val markerType: MarkerType = MarkerType.PLAIN,
    val label: String? = null, // 함체/전주로 지정한 경우 표시할 이름 (지정 시점 스냅샷)
    // 실제 개황도 범례는 "모양(P/C 등)=시설물 종류, 색상=신설/기설/철거" 2축 체계를 쓴다
    // (13단계 문서 참고). ENCLOSURE/POLE에서만 의미 있고, PLAIN/CONNECTION은 null로 둔다.
    val status: MarkerStatus? = null,
    // 범례 도형 심볼 id (LegendCatalog.symbols). null이면 markerType/status로 예전 방식 그대로 그린다.
    val symbolId: String? = null
)

enum class MarkerType { PLAIN, ENCLOSURE, POLE, CONNECTION }   // 일반 경로점 / 함체 위치 / 전주 위치 / 접속점

/** 실제 개황도 범례의 색상 축 — 신설=파랑, 기설=노랑, 철거=검정 (13단계에서 확인한 범례 기준). */
enum class MarkerStatus(val label: String, val colorHex: String) {
    NEW("신설", "#1E88E5"),
    EXISTING("기설", "#F5A623"),
    REMOVED("철거", "#1B2430")
}
