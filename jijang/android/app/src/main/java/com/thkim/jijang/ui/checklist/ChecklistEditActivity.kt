package com.thkim.jijang.ui.checklist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.thkim.jijang.JijangApp
import com.thkim.jijang.data.model.CableItem
import com.thkim.jijang.data.model.ChecklistDetail
import com.thkim.jijang.data.model.EnclosureItem
import com.thkim.jijang.data.model.FacilityPhase
import com.thkim.jijang.data.model.PoleInfo
import com.thkim.jijang.databinding.ActivityChecklistEditBinding
import com.thkim.jijang.databinding.ItemCableRowBinding
import com.thkim.jijang.databinding.ItemEnclosureRowBinding
import kotlinx.coroutines.launch

/**
 * ENG시트(Checklist) 대응 화면 — 6단계에서는 설계 요약/기설·신설 목록/특이사항만
 * 다뤘는데, 14단계에서 같은 목적의 다른 구현(ExcelJS 기반 웹앱)이 공유해준 실제
 * 셀 매핑을 참고해서 ENG시트 전체 항목으로 확장했다. 반복적인 2지선다/상세텍스트
 * 필드는 XML 대신 [FormBuilder]로 코드에서 만들고, 각 필드의 "현재 값 읽기" 함수를
 * [stringFields]에 모아뒀다가 저장 시점에 한꺼번에 읽는다.
 */
class ChecklistEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SITE_CASE_ID = "extra_site_case_id"
    }

    private lateinit var binding: ActivityChecklistEditBinding
    private lateinit var viewModel: ChecklistViewModel

    private val stringFields = mutableMapOf<String, () -> String?>()
    private var getUseExistingRoute: () -> Boolean = { false }
    private var getParallelNewDuctPole: () -> Boolean = { false }
    private var getSoleNewDuctPole: () -> Boolean = { false }
    private var getKepcoRelocation: () -> Boolean = { false }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChecklistEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val siteCaseId = intent.getLongExtra(EXTRA_SITE_CASE_ID, -1)
        require(siteCaseId >= 0) { "EXTRA_SITE_CASE_ID required" }

        val app = application as JijangApp
        viewModel = ViewModelProvider(
            this,
            ChecklistViewModel.Factory(app.database, siteCaseId)
        )[ChecklistViewModel::class.java]

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.buttonAddExistingCable.setOnClickListener {
            addCableRow(binding.containerExistingCables, null)
        }
        binding.buttonAddNewCable.setOnClickListener {
            addCableRow(binding.containerNewCables, null)
        }
        binding.buttonAddExistingEnclosure.setOnClickListener {
            addEnclosureRow(binding.containerExistingEnclosures, null)
        }
        binding.buttonAddNewEnclosure.setOnClickListener {
            addEnclosureRow(binding.containerNewEnclosures, null)
        }
        binding.buttonSave.setOnClickListener { save(siteCaseId) }

        lifecycleScope.launch {
            val data = viewModel.load()
            populate(data)
        }
    }

    private fun populate(data: ChecklistFormData) {
        val d = data.detail

        // 기본정보
        stringFields["linkedEngExists"] = FormBuilder.toggleField(
            this, binding.containerBasicInfoExtra, "연계 ENG 존재유무", "Y", "N", d.linkedEngExists
        )

        // 설계 요약 (고정 필드)
        binding.editRelocationMethod.setText(d.relocationMethod)
        binding.editRelocationReason.setText(d.relocationReason)
        binding.editExistingRouteSummary.setText(d.existingRouteSummary)
        binding.editRouteBeforeConfig.setText(d.routeBeforeConfig)
        binding.editRouteAfterConfig.setText(d.routeAfterConfig)
        stringFields["coreCountDay"] = FormBuilder.textField(this, binding.containerDesignSummaryExtra, "접속코어 수 (주간)", d.coreCountDay)
        stringFields["coreCountNight"] = FormBuilder.textField(this, binding.containerDesignSummaryExtra, "접속코어 수 (야간)", d.coreCountNight)

        // 공사배경 및 목적
        stringFields["requestSubjectType"] = FormBuilder.radioGroupField(
            this, binding.containerBackground, "요청 주체",
            listOf("한전", "CV기반(민원)", "RM기반(위해)", "지자체", "공공기관", "기타"), d.requestSubjectType
        )
        stringFields["rmRegistrationNo"] = FormBuilder.textField(this, binding.containerBackground, "RM등록번호 (RM기반 선택 시)", d.rmRegistrationNo)
        stringFields["parallelConstruction"] = FormBuilder.toggleField(this, binding.containerBackground, "병행공사 여부", "있음", "없음", d.parallelConstruction)
        stringFields["parallelConstructionDetail"] = FormBuilder.textField(this, binding.containerBackground, "병행공사 상세 (주관사/참여사)", d.parallelConstructionDetail)
        stringFields["causerReportAttached"] = FormBuilder.toggleField(this, binding.containerBackground, "원인자 판정조서 첨부", "첨부", "미첨부", d.causerReportAttached)
        stringFields["causerReportReason"] = FormBuilder.textField(this, binding.containerBackground, "원인자 불가사유", d.causerReportReason)

        // 현장실사결과
        addPair(binding.containerFieldSurvey, "gisMismatchDuctPole", "GIS 불일치 관로·전주 존재", d.gisMismatchDuctPole, d.gisMismatchDuctPoleDetail)
        addPair(binding.containerFieldSurvey, "gisMismatchCableEnclosure", "GIS 불일치 케이블·함체 존재", d.gisMismatchCableEnclosure, d.gisMismatchCableEnclosureDetail)
        addPair(binding.containerFieldSurvey, "gisOtherIssue", "GIS 상 그 외 특이사항", d.gisOtherIssue, d.gisOtherIssueDetail)
        addPair(binding.containerFieldSurvey, "ownDuctExists", "자가주·관로 존재여부", d.ownDuctExists, d.ownDuctExistsDetail)
        addPair(binding.containerFieldSurvey, "otherCompanyDuctExists", "타사주·관로 존재여부", d.otherCompanyDuctExists, d.otherCompanyDuctExistsDetail)
        addPair(binding.containerFieldSurvey, "ownPolePermitPossible", "자가주 인허가 가능여부", d.ownPolePermitPossible, d.ownPolePermitPossibleDetail)
        addPair(binding.containerFieldSurvey, "bombOrMultiEnclosure", "폭탄함체·다분기 함체 존재", d.bombOrMultiEnclosure, d.bombOrMultiEnclosureDetail)

        data.existingCables.forEach { addCableRow(binding.containerExistingCables, it) }
        data.newCables.forEach { addCableRow(binding.containerNewCables, it) }
        data.existingEnclosures.forEach { addEnclosureRow(binding.containerExistingEnclosures, it) }
        data.newEnclosures.forEach { addEnclosureRow(binding.containerNewEnclosures, it) }

        data.poleInfo?.let {
            binding.editKepcoPoleCount.setText(it.kepcoPoleCount.toString())
            binding.editOwnPoleCount.setText(it.ownPoleCount.toString())
            binding.editOtherPoleCount.setText(it.otherCompanyPoleCount.toString())
        }

        // 설계 이설루트
        getUseExistingRoute = FormBuilder.checkboxField(this, binding.containerDesignRoute, "기설 통신주/관로 루트사용", d.useExistingRoute)
        stringFields["useExistingRouteDetail"] = FormBuilder.textField(this, binding.containerDesignRoute, "상세 (기설 루트활용)", d.useExistingRouteDetail)
        getParallelNewDuctPole = FormBuilder.checkboxField(this, binding.containerDesignRoute, "병행 관로/전주 신설", d.parallelNewDuctPole)
        getSoleNewDuctPole = FormBuilder.checkboxField(this, binding.containerDesignRoute, "단독 관로/전주 신설", d.soleNewDuctPole)
        getKepcoRelocation = FormBuilder.checkboxField(this, binding.containerDesignRoute, "한전주 이설", d.kepcoRelocation)

        // 설계 신설정보 (추가 항목)
        stringFields["capacityExpansion"] = FormBuilder.toggleField(this, binding.containerNewInfoExtra, "용량증설 대상 여부", "해당", "해당없음", d.capacityExpansion)
        stringFields["capacityExpansionDetail"] = FormBuilder.textField(this, binding.containerNewInfoExtra, "용량증설 상세", d.capacityExpansionDetail)
        stringFields["multiplexTarget"] = FormBuilder.toggleField(this, binding.containerNewInfoExtra, "다대화 대상 여부", "해당", "해당없음", d.multiplexTarget)
        stringFields["multiplexTargetDetail"] = FormBuilder.textField(this, binding.containerNewInfoExtra, "다대화 상세", d.multiplexTargetDetail)
        stringFields["tbJointInvestment"] = FormBuilder.toggleField(this, binding.containerNewInfoExtra, "T&B 공동투자", "공동투자", "SKT 단독공사", d.tbJointInvestment)
        stringFields["tbJointInvestmentDetail"] = FormBuilder.textField(this, binding.containerNewInfoExtra, "공동투자 상세", d.tbJointInvestmentDetail)

        // 이설 후 RM여부
        addPair(binding.containerRmStatus, "sixLaneCrossing", "6차선 횡단 구간 존재", d.sixLaneCrossing, d.sixLaneCrossingDetail, trueLabel = "존재", falseLabel = "해소")
        addPair(binding.containerRmStatus, "arbitraryCrossingIssue", "임의횡단·지선없는 종말주·지상고 미달", d.arbitraryCrossingIssue, d.arbitraryCrossingIssueDetail, trueLabel = "존재", falseLabel = "해소")
        addPair(binding.containerRmStatus, "distributionContactIssue", "배전설비접촉·여유장과다·저압선이격 등", d.distributionContactIssue, d.distributionContactIssueDetail, trueLabel = "존재", falseLabel = "해소")
        addPair(binding.containerRmStatus, "coreRingIssue", "코아링/케이블링 발생여부", d.coreRingIssue, d.coreRingIssueDetail, trueLabel = "발생", falseLabel = "미발생")

        // 지역 별 추가기준
        addPair(binding.containerRegionalCriteria, "recycledCableUsed", "재활용 케이블 사용여부", d.recycledCableUsed, d.recycledCableUsedDetail, trueLabel = "사용", falseLabel = "미사용")
        addPair(binding.containerRegionalCriteria, "coreConnectionAppropriate", "코아 접속수량 적정성", d.coreConnectionAppropriate, d.coreConnectionAppropriateDetail, trueLabel = "부적정", falseLabel = "적정")
        addPair(binding.containerRegionalCriteria, "lineDistributionDesignApplied", "회선분산설계 적용여부", d.lineDistributionDesignApplied, d.lineDistributionDesignAppliedDetail, trueLabel = "적용", falseLabel = "미적용")
        addPair(binding.containerRegionalCriteria, "regionalOtherCriteria", "기타 지역기준 해당여부", d.regionalOtherCriteria, d.regionalOtherCriteriaDetail, trueLabel = "해당", falseLabel = "해당없음")

        binding.editSpecialNote.setText(d.specialNote)

        FormBuilder.makeSectionsCollapsible(binding.rootContent)
    }

    /** "있음/없음 + 상세텍스트" 조합을 만들고 [stringFields]에 등록한다. */
    private fun addPair(
        container: ViewGroup,
        key: String,
        label: String,
        toggleValue: String?,
        detailValue: String?,
        trueLabel: String = "있음",
        falseLabel: String = "없음"
    ) {
        val (getToggle, getDetail) = FormBuilder.toggleWithDetail(this, container, label, trueLabel, falseLabel, toggleValue, detailValue)
        stringFields[key] = getToggle
        stringFields["${key}Detail"] = getDetail
    }

    private fun addCableRow(container: ViewGroup, item: CableItem?) {
        val row = ItemCableRowBinding.inflate(LayoutInflater.from(this), container, false)
        row.editSeq.setText((item?.seq ?: (container.childCount + 1)).toString())
        row.editNetworkLevel.setText(item?.networkLevel)
        row.editLengthM.setText(item?.lengthM?.toString())
        row.editTotalCore.setText(item?.totalCore?.toString())
        row.editUsedCore.setText(item?.usedCore?.toString())
        row.editCableId.setText(item?.cableId)
        row.checkSkbShared.isChecked = item?.skbShared ?: false
        row.buttonDelete.setOnClickListener { container.removeView(row.root) }
        container.addView(row.root)
    }

    private fun addEnclosureRow(container: ViewGroup, item: EnclosureItem?) {
        val row = ItemEnclosureRowBinding.inflate(LayoutInflater.from(this), container, false)
        row.editSeq.setText((item?.seq ?: (container.childCount + 1)).toString())
        row.editKepcoNo.setText(item?.kepcoNo)
        row.editJpNo.setText(item?.jpNo)
        row.editConnectedCore.setText(item?.connectedCore?.toString())
        row.buttonDelete.setOnClickListener { container.removeView(row.root) }
        container.addView(row.root)
    }

    private fun readCableRows(container: ViewGroup, phase: FacilityPhase, siteCaseId: Long): List<CableItem> =
        container.children().map { child ->
            val row = ItemCableRowBinding.bind(child)
            CableItem(
                siteCaseId = siteCaseId,
                phase = phase,
                seq = row.editSeq.text.toString().toIntOrNull() ?: 0,
                lengthM = row.editLengthM.text.toString().toDoubleOrNull() ?: 0.0,
                networkLevel = row.editNetworkLevel.text.toString(),
                cableId = row.editCableId.text.toString().ifBlank { null },
                totalCore = row.editTotalCore.text.toString().toIntOrNull() ?: 0,
                usedCore = row.editUsedCore.text.toString().toIntOrNull() ?: 0,
                skbShared = row.checkSkbShared.isChecked
            )
        }

    private fun readEnclosureRows(container: ViewGroup, phase: FacilityPhase, siteCaseId: Long): List<EnclosureItem> =
        container.children().map { child ->
            val row = ItemEnclosureRowBinding.bind(child)
            EnclosureItem(
                siteCaseId = siteCaseId,
                phase = phase,
                seq = row.editSeq.text.toString().toIntOrNull() ?: 0,
                kepcoNo = row.editKepcoNo.text.toString().ifBlank { null },
                jpNo = row.editJpNo.text.toString().ifBlank { null },
                connectedCore = row.editConnectedCore.text.toString().toIntOrNull() ?: 0
            )
        }

    private fun ViewGroup.children(): List<View> = (0 until childCount).map { getChildAt(it) }

    private fun save(siteCaseId: Long) {
        fun s(key: String): String? = stringFields[key]?.invoke()

        val detail = ChecklistDetail(
            siteCaseId = siteCaseId,
            linkedEngExists = s("linkedEngExists"),
            relocationMethod = binding.editRelocationMethod.text.toString().ifBlank { null },
            relocationReason = binding.editRelocationReason.text.toString().ifBlank { null },
            existingRouteSummary = binding.editExistingRouteSummary.text.toString().ifBlank { null },
            routeBeforeConfig = binding.editRouteBeforeConfig.text.toString().ifBlank { null },
            routeAfterConfig = binding.editRouteAfterConfig.text.toString().ifBlank { null },
            coreCountDay = s("coreCountDay"),
            coreCountNight = s("coreCountNight"),
            requestSubjectType = s("requestSubjectType"),
            rmRegistrationNo = s("rmRegistrationNo"),
            parallelConstruction = s("parallelConstruction"),
            parallelConstructionDetail = s("parallelConstructionDetail"),
            causerReportAttached = s("causerReportAttached"),
            causerReportReason = s("causerReportReason"),
            gisMismatchDuctPole = s("gisMismatchDuctPole"), gisMismatchDuctPoleDetail = s("gisMismatchDuctPoleDetail"),
            gisMismatchCableEnclosure = s("gisMismatchCableEnclosure"), gisMismatchCableEnclosureDetail = s("gisMismatchCableEnclosureDetail"),
            gisOtherIssue = s("gisOtherIssue"), gisOtherIssueDetail = s("gisOtherIssueDetail"),
            ownDuctExists = s("ownDuctExists"), ownDuctExistsDetail = s("ownDuctExistsDetail"),
            otherCompanyDuctExists = s("otherCompanyDuctExists"), otherCompanyDuctExistsDetail = s("otherCompanyDuctExistsDetail"),
            ownPolePermitPossible = s("ownPolePermitPossible"), ownPolePermitPossibleDetail = s("ownPolePermitPossibleDetail"),
            bombOrMultiEnclosure = s("bombOrMultiEnclosure"), bombOrMultiEnclosureDetail = s("bombOrMultiEnclosureDetail"),
            useExistingRoute = getUseExistingRoute(), useExistingRouteDetail = s("useExistingRouteDetail"),
            parallelNewDuctPole = getParallelNewDuctPole(),
            soleNewDuctPole = getSoleNewDuctPole(),
            kepcoRelocation = getKepcoRelocation(),
            capacityExpansion = s("capacityExpansion"), capacityExpansionDetail = s("capacityExpansionDetail"),
            multiplexTarget = s("multiplexTarget"), multiplexTargetDetail = s("multiplexTargetDetail"),
            tbJointInvestment = s("tbJointInvestment"), tbJointInvestmentDetail = s("tbJointInvestmentDetail"),
            sixLaneCrossing = s("sixLaneCrossing"), sixLaneCrossingDetail = s("sixLaneCrossingDetail"),
            arbitraryCrossingIssue = s("arbitraryCrossingIssue"), arbitraryCrossingIssueDetail = s("arbitraryCrossingIssueDetail"),
            distributionContactIssue = s("distributionContactIssue"), distributionContactIssueDetail = s("distributionContactIssueDetail"),
            coreRingIssue = s("coreRingIssue"), coreRingIssueDetail = s("coreRingIssueDetail"),
            recycledCableUsed = s("recycledCableUsed"), recycledCableUsedDetail = s("recycledCableUsedDetail"),
            coreConnectionAppropriate = s("coreConnectionAppropriate"), coreConnectionAppropriateDetail = s("coreConnectionAppropriateDetail"),
            lineDistributionDesignApplied = s("lineDistributionDesignApplied"), lineDistributionDesignAppliedDetail = s("lineDistributionDesignAppliedDetail"),
            regionalOtherCriteria = s("regionalOtherCriteria"), regionalOtherCriteriaDetail = s("regionalOtherCriteriaDetail"),
            specialNote = binding.editSpecialNote.text.toString().ifBlank { null }
        )
        val poleInfo = PoleInfo(
            siteCaseId = siteCaseId,
            kepcoPoleCount = binding.editKepcoPoleCount.text.toString().toIntOrNull() ?: 0,
            ownPoleCount = binding.editOwnPoleCount.text.toString().toIntOrNull() ?: 0,
            otherCompanyPoleCount = binding.editOtherPoleCount.text.toString().toIntOrNull() ?: 0
        )

        lifecycleScope.launch {
            viewModel.save(
                detail = detail,
                existingCables = readCableRows(binding.containerExistingCables, FacilityPhase.EXISTING, siteCaseId),
                newCables = readCableRows(binding.containerNewCables, FacilityPhase.NEW, siteCaseId),
                existingEnclosures = readEnclosureRows(binding.containerExistingEnclosures, FacilityPhase.EXISTING, siteCaseId),
                newEnclosures = readEnclosureRows(binding.containerNewEnclosures, FacilityPhase.NEW, siteCaseId),
                poleInfo = poleInfo
            )
            Toast.makeText(this@ChecklistEditActivity, "저장되었습니다", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
