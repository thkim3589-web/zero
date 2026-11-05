package com.thkim.jijang.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class StockType(val label: String) {          // AB열 — 확인됨: 정확히 이 2개
    NEW("신규"), STOCK("재고")
}

enum class LayingMethod(val label: String) {        // AE열 — 확인됨: 4개
    AERIAL("가공"), UNDERGROUND("지중"), RISER("입상"), POINT("점가공")
}

/** AF열("작업구분") — 실제 드롭다운에 "철거"는 없고 "이설"이 따로 있다 (지장이설 사업의 핵심 작업). */
enum class WorkType(val label: String) {
    NEW("신설"),
    NEW_AIR_PRESSURE("신설(공기압)"),
    RELOCATION("이설"),
    ROUTE_ADJUSTMENT("이도조정"),
    SIMPLE_RELOCATION_CABLE("단순지장이설_케이블"),
    NEW_AD_SECTION("신설(AD구간)")
}

/** BF열("접속구분"). */
enum class SpliceType(val label: String) {
    NEW_D("D-신설"),
    SLACK_CLEANUP("여장정리"),
    SHEATH_ADD_G("G-외피추가"),
    SHEATH_ADD_D("D-외피추가"),
    SHEATH_REMOVE("외피해체"),
    FTTH_NEW("FTTH신설"),
    FTTH_RECONNECT("FTTH재접속"),
    FTTH_EXISTING("FTTH기설"),
    MSLT_NEW("MSLT신설"),
    MSLT_NEW_FTTH("MSLT신설_FTTH"),
    E_TYPE_NEW("E_Type신설")
}

/** BH열 — 처음엔 "방호차 필요 여부(체크박스)"로 오해했는데, 실제로는 안전조치 종류를 고르는 열이었다. */
enum class SafetyMeasure(val label: String) {
    GUARD_TRUCK("방호차"),
    DAY_WORK("주간작업"),
    SIDE_ROAD("이면도로"),
    ROAD_OUTSIDE_WORK("도로외부작업"),
    SIDEWALK_WORK("인도내작업"),
    NEUNGRO("능로"),
    VEHICLE_ACCESS_BLOCKED("차량진입불가"),
    CONSTRUCTION_SITE("공사장")
}

/**
 * `기별` 시트 대응 — 전주 구간 하나에서 있었던 작업 내역.
 * 열 대부분이 자유 텍스트가 아니라 엑셀 데이터 유효성 검사(드롭다운)로 값이 고정돼
 * 있어서, 실제 드롭다운 항목을 하나하나 캡처해서 확인한 뒤 그대로 enum으로 옮겼다
 * (13단계 문서 참고). 접속/안전 관련 항목은 그 구간에 실제로 해당 작업이 있을 때만
 * 채운다 (null 허용).
 */
@Entity(tableName = "route_segment")
data class RouteSegment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val siteCaseId: Long,
    val seq: Int,                            // 구간 순서 (누적거리는 이 순서대로 계산)
    val poleName: String,                    // 전주번호/지점명 (예: 학산간20R1)
    val segmentLengthM: Double,               // 구간거리(m)
    val stockType: StockType,                // 신규 / 재고
    val workType: WorkType,                  // 신설 / 신설(공기압) / 이설 / 이도조정 / 단순지장이설_케이블 / 신설(AD구간)
    val cableType: String? = null,           // 케이블 종류 (예: Dry) — 확인된 목록이 더 있을 수 있어 자유 입력 유지
    val coreCount: Int = 0,                  // 코어수 (24/48/72/96/144/288 중 하나 권장)
    val layingMethod: LayingMethod,          // 가공 / 지중 / 입상 / 점가공
    // 접속 정보 — 이 구간에 접속 작업이 있을 때만
    val spliceType: SpliceType? = null,      // 접속구분
    val workShift: String? = null,           // 주간 / 야간
    // 안전 정보 — 해당하는 안전조치가 있을 때만
    val safetyMeasure: SafetyMeasure? = null
)
