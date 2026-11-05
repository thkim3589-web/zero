package com.thkim.jijang.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 현장 건 (ENG시트(Checklist) 상단 정보 대응)
 */
@Entity(tableName = "site_case")
data class SiteCase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val caseName: String,              // 공사명 (ENG!B3)
    val companyName: String,           // 협력사명/설계자 (B4)
    val reviewer: String? = null,      // 검토자 (B5)
    val approver: String? = null,      // 승인자 (B6)
    val businessType: String = "",     // 사업구분 (D4) RISK 등
    val constructionMethod: String = "", // 공사방안 (D5) 절체 등
    val requestSubject: String = "",   // 요청주체 (D6)
    val extractionType: String = "",   // 적출유형 (F4)
    val requestDocNo: String? = null,  // 이설요청 공문번호 (F5)
    val siteAddress: String = "",      // 현장주소 (F6)
    val effectiveYear: Int,            // 적용 단가 연도 (단가 마스터 조회 키)
    val status: String = "DRAFT",      // DRAFT / SUBMITTED / EXPORTED
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
