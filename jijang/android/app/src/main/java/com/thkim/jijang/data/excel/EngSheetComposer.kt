package com.thkim.jijang.data.excel

import com.thkim.jijang.data.model.CableItem
import com.thkim.jijang.data.model.ChecklistDetail
import com.thkim.jijang.data.model.EnclosureItem
import com.thkim.jijang.data.model.PoleInfo
import com.thkim.jijang.data.model.SiteCase
import kotlin.math.roundToInt

/**
 * `ENG시트(Checklist)`에 채울 셀 값을 계산한다.
 *
 * 셀 주소는 실제 2026년 정산 파일(JP13464)을 직접 열어 확인했고, 14단계에서
 * 같은 목적의 다른 구현(ExcelJS 기반 웹앱)이 공유해준 매핑으로 21~62행의
 * 세부 체크리스트 항목까지 채우도록 확장했다. "있음/없음" 류 항목은
 * [ChecklistDetail]에 실제 셀 문자열 그대로 저장돼 있어서 그대로 쓰면 되고,
 * 라디오그룹/체크박스(mark)류만 "O"를 써넣는다.
 */
object EngSheetComposer {

    fun compose(
        siteCase: SiteCase,
        checklist: ChecklistDetail,
        existingCables: List<CableItem>,
        newCables: List<CableItem>,
        existingEnclosures: List<EnclosureItem>,
        newEnclosures: List<EnclosureItem>,
        poleInfo: PoleInfo?
    ): Map<String, String> {
        val cells = linkedMapOf(
            "B3" to siteCase.caseName,
            "B4" to siteCase.companyName,
            "B5" to (siteCase.reviewer ?: ""),
            "B6" to (siteCase.approver ?: ""),
            "D4" to siteCase.businessType,
            "D5" to siteCase.constructionMethod,
            "D6" to siteCase.requestSubject,
            "F4" to siteCase.extractionType,
            "F5" to (siteCase.requestDocNo ?: ""),
            "F6" to siteCase.siteAddress,
            "B7" to (checklist.linkedEngExists ?: "N"),
            "C11" to (checklist.relocationMethod ?: ""),
            "C12" to (checklist.relocationReason ?: ""),
            "C13" to (checklist.existingRouteSummary ?: ""),
            "C14" to (checklist.routeBeforeConfig ?: ""),
            "C15" to (checklist.routeAfterConfig ?: ""),
            "C16" to "신설 : ${newCables.size}조 ${kmText(newCables)}",
            "D16" to "철거 : ${existingCables.size}조 ${kmText(existingCables)}",
            "C17" to "신설 : ${newEnclosures.size}",
            "D17" to "철거 : ${existingEnclosures.size}",
            "B63" to (checklist.specialNote ?: "")
        )

        checklist.coreCountDay?.let { cells["C18"] = it }
        checklist.coreCountNight?.let { cells["D18"] = it }

        // 공사배경 및 목적 — 요청주체는 라디오그룹(mark): 선택된 칸에만 "O"
        val requestSubjectCells = mapOf(
            "한전" to "C21", "CV기반(민원)" to "C22", "RM기반(위해)" to "C23",
            "지자체" to "C24", "공공기관" to "C25", "기타" to "C26"
        )
        requestSubjectCells[checklist.requestSubjectType]?.let { cells[it] = "O" }
        checklist.rmRegistrationNo?.let { cells["D23"] = it }
        checklist.parallelConstruction?.let { cells["C27"] = it }
        checklist.parallelConstructionDetail?.let { cells["D27"] = it }
        checklist.causerReportAttached?.let { cells["C28"] = it }
        checklist.causerReportReason?.let { cells["D28"] = it }

        // 현장실사결과
        pair(cells, "C31", "D31", checklist.gisMismatchDuctPole, checklist.gisMismatchDuctPoleDetail)
        pair(cells, "C32", "D32", checklist.gisMismatchCableEnclosure, checklist.gisMismatchCableEnclosureDetail)
        pair(cells, "C33", "D33", checklist.gisOtherIssue, checklist.gisOtherIssueDetail)
        pair(cells, "C34", "D34", checklist.ownDuctExists, checklist.ownDuctExistsDetail)
        pair(cells, "C35", "D35", checklist.otherCompanyDuctExists, checklist.otherCompanyDuctExistsDetail)
        pair(cells, "C36", "D36", checklist.ownPolePermitPossible, checklist.ownPolePermitPossibleDetail)
        pair(cells, "C37", "D37", checklist.bombOrMultiEnclosure, checklist.bombOrMultiEnclosureDetail)

        if (poleInfo != null) {
            cells["D40"] = "한전주 ${poleInfo.kepcoPoleCount}본 자가주 ${poleInfo.ownPoleCount}본 타사주 ${poleInfo.otherCompanyPoleCount}본"
        }
        if (existingCables.isNotEmpty()) cells["D41"] = existingCables.joinToString("\n") { cableLine("기설", it) }
        if (existingEnclosures.isNotEmpty()) cells["D42"] = existingEnclosures.joinToString("\n") { enclosureLine("기설", it) }

        // 설계 이설루트 (체크박스, mark)
        if (checklist.useExistingRoute) cells["C46"] = "O"
        checklist.useExistingRouteDetail?.let { cells["D46"] = it }
        if (checklist.parallelNewDuctPole) cells["C47"] = "O"
        if (checklist.soleNewDuctPole) cells["C48"] = "O"
        if (checklist.kepcoRelocation) cells["C49"] = "O"

        if (newCables.isNotEmpty()) cells["D50"] = newCables.joinToString("\n") { cableLine("신설", it) }
        checklist.capacityExpansion?.let { cells["C51"] = it }
        checklist.capacityExpansionDetail?.let { cells["D51"] = it }
        checklist.multiplexTarget?.let { cells["C52"] = it }
        checklist.multiplexTargetDetail?.let { cells["D52"] = it }
        checklist.tbJointInvestment?.let { cells["C53"] = it }
        checklist.tbJointInvestmentDetail?.let { cells["D53"] = it }
        if (newEnclosures.isNotEmpty()) cells["D54"] = newEnclosures.joinToString("\n") { enclosureLine("신설", it) }

        // 이설 후 RM여부
        pair(cells, "C55", "D55", checklist.sixLaneCrossing, checklist.sixLaneCrossingDetail)
        pair(cells, "C56", "D56", checklist.arbitraryCrossingIssue, checklist.arbitraryCrossingIssueDetail)
        pair(cells, "C57", "D57", checklist.distributionContactIssue, checklist.distributionContactIssueDetail)
        pair(cells, "C58", "D58", checklist.coreRingIssue, checklist.coreRingIssueDetail)

        // 지역 별 추가기준
        pair(cells, "C59", "D59", checklist.recycledCableUsed, checklist.recycledCableUsedDetail)
        pair(cells, "C60", "D60", checklist.coreConnectionAppropriate, checklist.coreConnectionAppropriateDetail)
        pair(cells, "C61", "D61", checklist.lineDistributionDesignApplied, checklist.lineDistributionDesignAppliedDetail)
        pair(cells, "C62", "D62", checklist.regionalOtherCriteria, checklist.regionalOtherCriteriaDetail)

        return cells
    }

    private fun pair(cells: MutableMap<String, String>, toggleCell: String, detailCell: String, toggleValue: String?, detailValue: String?) {
        toggleValue?.let { cells[toggleCell] = it }
        detailValue?.let { cells[detailCell] = it }
    }

    private fun kmText(cables: List<CableItem>): String {
        val totalM = cables.sumOf { it.lengthM }
        return "%.3fKm".format(totalM / 1000.0)
    }

    private fun cableLine(label: String, item: CableItem): String {
        val percent = if (item.totalCore > 0) (item.usedCore * 100.0 / item.totalCore).roundToInt() else 0
        val cableIdPart = item.cableId?.let { " $it" } ?: ""
        return "[${label}케이블#${item.seq} ${formatLength(item.lengthM)}M] ${item.networkLevel}$cableIdPart " +
            "${item.totalCore}C/${item.usedCore}C(${percent}%)"
    }

    private fun enclosureLine(label: String, item: EnclosureItem): String =
        "[${label}함체#${item.seq}]　${item.kepcoNo ?: ""} / ${item.jpNo ?: ""} / 접속${item.connectedCore}C"

    private fun formatLength(lengthM: Double): String =
        if (lengthM == lengthM.toLong().toDouble()) lengthM.toLong().toString() else lengthM.toString()
}
