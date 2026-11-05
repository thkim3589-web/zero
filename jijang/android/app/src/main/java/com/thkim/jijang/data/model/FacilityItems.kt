package com.thkim.jijang.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class FacilityPhase { EXISTING, NEW }   // 기설 / 신설

@Entity(tableName = "cable_item")
data class CableItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val siteCaseId: Long,
    val phase: FacilityPhase,
    val seq: Int,                     // #1, #2 ...
    val lengthM: Double,              // 예: 643 (643M)
    val networkLevel: String,         // 가입자망/지선망 등
    val cableId: String? = null,      // =2345= 관리번호
    val totalCore: Int,
    val usedCore: Int,
    val skbShared: Boolean = false    // SKB 공동투자 여부
)

@Entity(tableName = "enclosure_item")
data class EnclosureItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val siteCaseId: Long,
    val phase: FacilityPhase,
    val seq: Int,
    val kepcoNo: String? = null,      // 한전전산번호/관리번호 (7929DX01 등)
    val jpNo: String? = null,         // JP 번호 (달서구JP13464 등)
    val connectedCore: Int
)

@Entity(tableName = "pole_info")
data class PoleInfo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val siteCaseId: Long,
    val kepcoPoleCount: Int,          // 한전주
    val ownPoleCount: Int,            // 자가주
    val otherCompanyPoleCount: Int    // 타사주
)
