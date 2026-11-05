package com.thkim.jijang.data.excel

import com.thkim.jijang.data.model.LaborPriceMaster
import com.thkim.jijang.data.model.MaterialPriceMaster
import java.io.File

data class PriceParseResult(
    val laborItems: List<LaborPriceMaster>,
    val materialItems: List<MaterialPriceMaster>
)

/**
 * `내역서_일반`/`자재내역서_일반` 시트를 [02_price_master_schema.md] 문서의 열 매핑대로 읽어
 * [LaborPriceMaster]/[MaterialPriceMaster] 목록으로 변환한다.
 *
 * 데이터 행 판별: 공정번호/자재코드 열 값이 숫자만으로 이루어진 행만 데이터로 취급한다
 * (헤더/라벨 행은 한글 텍스트라 자동으로 걸러짐). **아직 실제 2026년 단가 파일로
 * 열 위치를 검증하지 못했다** — SKT 표준 정산 엑셀은 사내 문서보안(DRM)으로 암호화돼
 * 있어 이 저장소에서는 원본을 직접 열어볼 수 없었다. 실제 파일로 첫 업로드를 해보고
 * 열 매핑(아래 LABOR_, MATERIAL_ 접두사 상수)이 어긋나면 그에 맞게 조정하면 된다.
 */
object PriceMasterParser {

    private const val LABOR_SHEET_NAME = "내역서_일반"
    private const val MATERIAL_SHEET_NAME = "자재내역서_일반"

    private const val LABOR_COL_CATEGORY = "C"
    private const val LABOR_COL_WORK_TYPE = "D"
    private const val LABOR_COL_PROCESS_CODE = "E"
    private const val LABOR_COL_ITEM_NAME = "F"
    private const val LABOR_COL_SPEC = "G"
    private const val LABOR_COL_UNIT = "H"
    private const val LABOR_COL_MATERIAL_PRICE = "O"
    private const val LABOR_COL_LABOR_PRICE = "P"
    private const val LABOR_COL_EQUIPMENT_PRICE = "Q"

    private const val MATERIAL_COL_MATERIAL_CODE = "C"
    private const val MATERIAL_COL_CATEGORY = "D"
    private const val MATERIAL_COL_MATERIAL_NAME = "E"
    private const val MATERIAL_COL_SPEC = "F"
    private const val MATERIAL_COL_SPEC_DETAIL = "G"
    private const val MATERIAL_COL_UNIT = "H"
    private const val MATERIAL_COL_UNIT_PRICE = "L"

    private val numericCodeRegex = Regex("^\\d+$")

    fun parse(file: File, effectiveYear: Int, sourceFileName: String, uploadedAt: Long): PriceParseResult {
        val laborRows = XlsxReader.readSheet(file, LABOR_SHEET_NAME)
        val materialRows = XlsxReader.readSheet(file, MATERIAL_SHEET_NAME)

        val laborItems = laborRows.mapNotNull { row ->
            val code = row[LABOR_COL_PROCESS_CODE]?.trim().orEmpty()
            if (!numericCodeRegex.matches(code)) return@mapNotNull null
            LaborPriceMaster(
                processCode = code,
                category = row[LABOR_COL_CATEGORY]?.trim()?.ifBlank { null },
                workType = row[LABOR_COL_WORK_TYPE]?.trim()?.ifBlank { null },
                itemName = row[LABOR_COL_ITEM_NAME]?.trim().orEmpty(),
                spec = row[LABOR_COL_SPEC]?.trim()?.ifBlank { null },
                unit = row[LABOR_COL_UNIT]?.trim().orEmpty(),
                materialUnitPrice = row[LABOR_COL_MATERIAL_PRICE]?.toDoubleOrNull()?.toInt() ?: 0,
                laborUnitPrice = row[LABOR_COL_LABOR_PRICE]?.toDoubleOrNull()?.toInt() ?: 0,
                equipmentUnitPrice = row[LABOR_COL_EQUIPMENT_PRICE]?.toDoubleOrNull()?.toInt() ?: 0,
                effectiveYear = effectiveYear,
                sourceFile = sourceFileName,
                uploadedAt = uploadedAt
            )
        }

        val materialItems = materialRows.mapNotNull { row ->
            val code = row[MATERIAL_COL_MATERIAL_CODE]?.trim().orEmpty()
            if (!numericCodeRegex.matches(code)) return@mapNotNull null
            MaterialPriceMaster(
                materialCode = code,
                category = row[MATERIAL_COL_CATEGORY]?.trim()?.ifBlank { null },
                materialName = row[MATERIAL_COL_MATERIAL_NAME]?.trim().orEmpty(),
                spec = row[MATERIAL_COL_SPEC]?.trim()?.ifBlank { null },
                specDetail = row[MATERIAL_COL_SPEC_DETAIL]?.trim()?.ifBlank { null },
                unit = row[MATERIAL_COL_UNIT]?.trim().orEmpty(),
                materialUnitPrice = row[MATERIAL_COL_UNIT_PRICE]?.toDoubleOrNull()?.toInt() ?: 0,
                effectiveYear = effectiveYear,
                sourceFile = sourceFileName,
                uploadedAt = uploadedAt
            )
        }

        return PriceParseResult(laborItems, materialItems)
    }
}
