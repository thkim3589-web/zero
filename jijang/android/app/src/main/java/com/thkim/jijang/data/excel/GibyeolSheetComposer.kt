package com.thkim.jijang.data.excel

import com.thkim.jijang.data.model.RouteSegment
import com.thkim.jijang.data.model.StockType

data class GibyeolCellWrites(
    val textCells: Map<String, String>,
    val numberCells: Map<String, Double>
)

/**
 * `기별` 시트에 구간(RouteSegment) 목록을 채운다.
 *
 * 11행부터 한 구간당 한 행. 실제 파일을 열어 확인한 결과, H/I/AJ/AB/AC/AD/AE/AF/
 * BD/BF/BG/BH/AI 열은 전부 값이 비어있는 순수 입력 셀이고, **AH열만
 * `=IF(AE11<>"",H11,0)` 수식**이 미리 들어있다 — 그래서 AH는 절대 건드리지
 * 않는다 (H/AE만 채우면 엑셀이 열릴 때 자동으로 재계산된다, 워크북에
 * fullCalcOnLoad를 켜둔 이유).
 *
 * 열 매핑 (13단계 — 실제 드롭다운 값을 하나하나 캡처해서 확인한 뒤 확정):
 * - D: 전주번호, H: 구간거리, I/AJ: 누적거리(둘 다 동일값 — 원본에 중복 존재)
 * - AB: 신규/재고, AC: 케이블종류, AD: 코어수, AE: 가공/지중/입상/점가공
 * - AF: 작업구분 (신설/신설(공기압)/이설/이도조정/단순지장이설_케이블/신설(AD구간)
 *   — "철거"는 이 열에 없다, 개황도 쪽 개념과는 별개)
 * - BD: 주/야, BF: 접속구분, BG: 개소(접속정보 있으면 1), BH: 안전조치 종류
 *
 * G열(시설물 구분: 기타/건물 등)은 지금 입력 화면에 없는 항목이라 비워둔다.
 */
object GibyeolSheetComposer {

    private const val FIRST_DATA_ROW = 11

    fun compose(segments: List<RouteSegment>): GibyeolCellWrites {
        val text = mutableMapOf<String, String>()
        val number = mutableMapOf<String, Double>()
        var cumulative = 0.0

        segments.sortedBy { it.seq }.forEachIndexed { index, segment ->
            val row = FIRST_DATA_ROW + index
            cumulative += segment.segmentLengthM

            text["D$row"] = segment.poleName
            number["H$row"] = segment.segmentLengthM
            number["I$row"] = cumulative
            number["AJ$row"] = cumulative
            text["AB$row"] = if (segment.stockType == StockType.STOCK) "재고" else "신규"
            segment.cableType?.let { text["AC$row"] = it }
            if (segment.coreCount != 0) number["AD$row"] = segment.coreCount.toDouble()
            text["AE$row"] = segment.layingMethod.label
            text["AF$row"] = segment.workType.label

            if (segment.spliceType != null) {
                text["BF$row"] = segment.spliceType.label
                text["BD$row"] = if (segment.workShift == "야간") "야간" else "주간"
                number["BG$row"] = 1.0
            }
            if (segment.safetyMeasure != null) {
                text["BH$row"] = segment.safetyMeasure.label
            }
        }

        return GibyeolCellWrites(text, number)
    }
}
