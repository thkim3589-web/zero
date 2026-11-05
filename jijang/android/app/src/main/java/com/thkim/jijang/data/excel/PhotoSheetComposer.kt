package com.thkim.jijang.data.excel

import com.thkim.jijang.data.model.SitePhoto

/**
 * `공문 및 사진` 시트의 사진 슬롯 위치 계산 (3단계 셀 매핑 문서 + 실제 파일 대조 결과).
 *
 * 블록은 77행부터 39행 간격으로 반복되고(77, 116, 155 ...), 한 블록에 슬롯 3개
 * (열 그룹 B~H / K~Q / T~Z)가 있다. 슬롯의 "사진유형"/"관련 근거" 값은 블록
 * 시작행에, "사진설명"은 그 다음 행에 들어간다.
 */
object PhotoSheetComposer {

    private const val FIRST_BLOCK_ROW = 77
    private const val BLOCK_ROW_SPAN = 39

    private data class SlotColumns(val typeAndDescCol: String, val relatedFacilityCol: String, val anchorFromCol: Int, val anchorToCol: Int)

    private val SLOTS = listOf(
        SlotColumns("D", "H", anchorFromCol = 1, anchorToCol = 8),
        SlotColumns("M", "Q", anchorFromCol = 10, anchorToCol = 17),
        SlotColumns("V", "Z", anchorFromCol = 19, anchorToCol = 26)
    )

    fun blockRow(blockIndex: Int): Int = FIRST_BLOCK_ROW + blockIndex * BLOCK_ROW_SPAN

    fun textCells(photo: SitePhoto): Map<String, String> {
        val slot = SLOTS.getOrNull(photo.slotIndex) ?: return emptyMap()
        val row = blockRow(photo.blockIndex)
        val cells = mutableMapOf(
            "${slot.typeAndDescCol}$row" to (photo.photoType ?: ""),
            "${slot.typeAndDescCol}${row + 1}" to (photo.description ?: "")
        )
        if (!photo.relatedFacilityNo.isNullOrBlank()) {
            cells["${slot.relatedFacilityCol}$row"] = photo.relatedFacilityNo
        }
        return cells
    }

    /**
     * 이미지 앵커 좌표 (0-indexed 열/행, xdr:twoCellAnchor 기준).
     *
     * 처음엔 라벨 행 아래에 이미지가 들어가는 줄 알고 그렇게 만들었는데 틀렸다 —
     * 실제 템플릿은 **이미지가 캡션(사진유형/설명) 행 위쪽 36행**에 들어간다
     * (예: 캡션이 77행이면 이미지는 41~76행, 1단계 셀 매핑 문서의 블록 범위와
     * 정확히 일치). 같은 목적으로 만들어진 다른 구현(ExcelJS 기반 웹앱)의
     * PHOTO_SLOTS 범위를 참고해서 바로잡았다.
     */
    fun anchor(photo: SitePhoto): PhotoAnchor? {
        val slot = SLOTS.getOrNull(photo.slotIndex) ?: return null
        val row1Indexed = blockRow(photo.blockIndex)
        val fromRow0 = row1Indexed - 37 // 캡션 행 위쪽 36행의 시작 (0-indexed)
        val toRow0 = row1Indexed - 2    // 캡션 행 바로 위 (0-indexed)
        return PhotoAnchor(
            fromCol = slot.anchorFromCol,
            fromRow = fromRow0,
            toCol = slot.anchorToCol,
            toRow = toRow0
        )
    }
}

data class PhotoAnchor(val fromCol: Int, val fromRow: Int, val toCol: Int, val toRow: Int)
