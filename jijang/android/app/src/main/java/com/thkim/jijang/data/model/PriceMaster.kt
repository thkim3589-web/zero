package com.thkim.jijang.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 현장에서 입력된 수량. code(공정번호/자재코드)로 단가 마스터를 조회해서
 * 내역서/자재내역서/원가계산서 금액을 계산한다.
 */
@Entity(tableName = "work_quantity")
data class WorkQuantity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val siteCaseId: Long,
    val masterType: String,     // "LABOR" | "MATERIAL"
    val code: String,           // processCode 또는 materialCode
    val quantity: Double,
    val category: String? = null   // 투자/수선 구분 등
)

/** 내역서_일반 대응 — 공정번호 키 노무/장비 단가 */
@Entity(tableName = "labor_price_master", primaryKeys = ["processCode", "effectiveYear"])
data class LaborPriceMaster(
    val processCode: String,
    val category: String? = null,
    val workType: String? = null,
    val itemName: String,
    val spec: String? = null,
    val unit: String,
    val materialUnitPrice: Int = 0,
    val laborUnitPrice: Int = 0,
    val equipmentUnitPrice: Int = 0,
    val effectiveYear: Int,
    val sourceFile: String? = null,
    val uploadedAt: Long
)

/** 자재내역서_일반 대응 — 자재코드 키 자재 단가 */
@Entity(tableName = "material_price_master", primaryKeys = ["materialCode", "effectiveYear"])
data class MaterialPriceMaster(
    val materialCode: String,
    val category: String? = null,
    val materialName: String,
    val spec: String? = null,
    val specDetail: String? = null,
    val unit: String,
    val materialUnitPrice: Int = 0,
    val effectiveYear: Int,
    val sourceFile: String? = null,
    val uploadedAt: Long
)

/** 원가계산서(일반) 상단 요율표 (간접노무비율, 산재보험료율 등) */
@Entity(tableName = "cost_rate_master", primaryKeys = ["rateKey", "effectiveYear"])
data class CostRateMaster(
    val rateKey: String,        // INDIRECT_LABOR_RATE 등
    val rateNameKr: String,
    val rateValue: Double,
    val effectiveYear: Int,
    val sourceFile: String? = null,
    val uploadedAt: Long
)

/** 관리자가 단가 파일을 업로드할 때마다 남기는 이력 (감사 추적용) */
@Entity(tableName = "price_upload_history")
data class PriceUploadHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val effectiveYear: Int,
    val laborRowCount: Int,
    val materialRowCount: Int,
    val uploadedAt: Long,
    val uploadedBy: String? = null
)
