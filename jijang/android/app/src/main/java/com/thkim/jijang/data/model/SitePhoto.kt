package com.thkim.jijang.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * "공문 및 사진" 시트 사진 슬롯 (블록당 3장, 39행 간격) 대응
 */
@Entity(tableName = "site_photo")
data class SitePhoto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val siteCaseId: Long,
    val blockIndex: Int,             // 77행 블록=0, 116행 블록=1 ...
    val slotIndex: Int,              // 슬롯 0~2
    val photoType: String? = null,   // 사진유형 (현장사진#1 등)
    val description: String? = null, // 사진설명
    val relatedFacilityNo: String? = null, // 관련 근거(함체번호 등)
    val localFilePath: String,       // 기기 내 저장 경로
    val capturedAt: Long = System.currentTimeMillis()
)
