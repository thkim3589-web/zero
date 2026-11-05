package com.thkim.jijang.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * ENG시트(Checklist) 전체 항목 대응.
 *
 * 14단계 — 같은 목적으로 만들어진 다른 구현(ExcelJS 기반 웹앱)이 공유해준
 * 실제 셀 매핑을 참고해서, 6단계에서 일부만 다루던 것을 ENG시트 전체
 * (기본정보/설계요약/공사배경/현장실사결과/설계이설루트/설계신설정보/
 * 이설후RM여부/지역별추가기준)로 확장했다.
 *
 * "있음/없음", "존재/해소" 같은 2지선다 항목은 boolean이 아니라 **실제 셀에
 * 쓸 문자열을 그대로 저장**한다 (null = 아직 선택 안 함, 값 있으면 그 문자열이
 * 곧 셀 값). 이러면 내보내기 쪽에서 boolean→라벨 변환표를 따로 둘 필요가 없다.
 */
@Entity(
    tableName = "checklist_detail",
    foreignKeys = [ForeignKey(
        entity = SiteCase::class,
        parentColumns = ["id"],
        childColumns = ["siteCaseId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ChecklistDetail(
    @PrimaryKey val siteCaseId: Long,

    // 기본정보
    val linkedEngExists: String? = null,           // B7: 연계 ENG 존재유무 (N/Y)

    // 설계 요약
    val relocationMethod: String? = null,          // C11
    val relocationReason: String? = null,          // C12
    val existingRouteSummary: String? = null,      // C13
    val routeBeforeConfig: String? = null,         // C14
    val routeAfterConfig: String? = null,          // C15
    val coreCountDay: String? = null,              // C18: 접속코어 수(주간)
    val coreCountNight: String? = null,            // D18: 접속코어 수(야간)

    // 공사배경 및 목적 (현장개요)
    val requestSubjectType: String? = null,        // 한전/CV기반(민원)/RM기반(위해)/지자체/공공기관/기타 -> C21~C26
    val rmRegistrationNo: String? = null,          // D23
    val parallelConstruction: String? = null,      // C27: 없음/있음
    val parallelConstructionDetail: String? = null,// D27
    val causerReportAttached: String? = null,      // C28: 첨부/미첨부
    val causerReportReason: String? = null,        // D28

    // 현장실사결과 (7개 항목, 각각 있음/없음 + 상세)
    val gisMismatchDuctPole: String? = null, val gisMismatchDuctPoleDetail: String? = null,             // C31/D31
    val gisMismatchCableEnclosure: String? = null, val gisMismatchCableEnclosureDetail: String? = null, // C32/D32
    val gisOtherIssue: String? = null, val gisOtherIssueDetail: String? = null,                         // C33/D33
    val ownDuctExists: String? = null, val ownDuctExistsDetail: String? = null,                         // C34/D34
    val otherCompanyDuctExists: String? = null, val otherCompanyDuctExistsDetail: String? = null,       // C35/D35
    val ownPolePermitPossible: String? = null, val ownPolePermitPossibleDetail: String? = null,         // C36/D36
    val bombOrMultiEnclosure: String? = null, val bombOrMultiEnclosureDetail: String? = null,           // C37/D37

    // 설계 이설루트
    val useExistingRoute: Boolean = false, val useExistingRouteDetail: String? = null, // C46(mark)/D46
    val parallelNewDuctPole: Boolean = false,      // C47(mark)
    val soleNewDuctPole: Boolean = false,          // C48(mark)
    val kepcoRelocation: Boolean = false,          // C49(mark)

    // 설계 신설정보 (C50/D50/C54/D54는 케이블·함체 목록에서 자동 계산 — 여기 없음)
    val capacityExpansion: String? = null, val capacityExpansionDetail: String? = null, // C51: 해당/해당없음, D51
    val multiplexTarget: String? = null, val multiplexTargetDetail: String? = null,     // C52: 해당/해당없음, D52
    val tbJointInvestment: String? = null, val tbJointInvestmentDetail: String? = null, // C53: 공동투자/SKT 단독공사, D53

    // 이설 후 RM여부
    val sixLaneCrossing: String? = null, val sixLaneCrossingDetail: String? = null,                       // C55: 존재/해소, D55
    val arbitraryCrossingIssue: String? = null, val arbitraryCrossingIssueDetail: String? = null,         // C56: 존재/해소, D56
    val distributionContactIssue: String? = null, val distributionContactIssueDetail: String? = null,     // C57: 존재/해소, D57
    val coreRingIssue: String? = null, val coreRingIssueDetail: String? = null,                           // C58: 발생/미발생, D58

    // 지역 별 추가기준
    val recycledCableUsed: String? = null, val recycledCableUsedDetail: String? = null,                   // C59: 사용/미사용, D59
    val coreConnectionAppropriate: String? = null, val coreConnectionAppropriateDetail: String? = null,   // C60: 부적정/적정, D60
    val lineDistributionDesignApplied: String? = null, val lineDistributionDesignAppliedDetail: String? = null, // C61: 적용/미적용, D61
    val regionalOtherCriteria: String? = null, val regionalOtherCriteriaDetail: String? = null,           // C62: 해당/해당없음, D62

    // 특이사항
    val specialNote: String? = null                // B63
)
